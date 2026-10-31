//! AGT-03 opt-in (NOT service-wired) HTTPS/mTLS adapter.
//! No local plaintext fallback, no arbitrary redirect/proxy, and no ACK
//! unless the signed-in server response names EXACTLY every sent sequence.
//! The *server* must additionally bind Caddy-verified mTLS fingerprint to
//! an active PostgreSQL device; this module cannot grant that trust.
use crate::store::{OutboxStore, PendingEvent, StoreError};
use reqwest::header::{CONTENT_TYPE, RETRY_AFTER};
use serde::{Deserialize, Serialize};
use std::{collections::HashSet, time::Duration};
use thiserror::Error;
use uuid::Uuid;

const AGENT_ORIGIN: &str = "https://agent.lanveriq.internal";
const MAX_RESPONSE_BYTES: usize = 4096;
const MAX_BATCH_BYTES: usize = 512 * 1024;
const MAX_BATCH_EVENTS: usize = 100;
const MAX_RETRY_AFTER_SECONDS: u64 = 300;

#[derive(Debug, Error)]
pub enum TransportError {
    #[error("only the approved HTTPS Agent hostname on port 443 is permitted")]
    InvalidOrigin,
    #[error("valid nonempty trusted server CA and client certificate/private key are required")]
    InvalidTlsMaterial,
    #[error("TLS client could not be built")]
    Tls(#[source] reqwest::Error),
    #[error("request failed; local outbox has not been acknowledged")]
    Network(#[source] reqwest::Error),
    #[error("response exceeded the bounded protocol size")]
    OversizedResponse,
    #[error("invalid or incomplete signed-in server ACK; local outbox retained")]
    InvalidAcknowledgement,
    #[error("invalid outbound event envelope")]
    InvalidEvent,
    #[error("local SQLite operation failed")]
    Store(#[from] StoreError),
    #[error("event envelope could not be serialized")]
    Json(#[from] serde_json::Error),
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum DeliveryDisposition {
    Accepted,
    Retry { after: Duration },
    PauseIdentity,
    Quarantine,
}

#[derive(Debug, Serialize)]
pub struct EventBatch {
    schema_version: u32,
    batch_id: Uuid,
    device_id: Uuid,
    events: Vec<EventWire>,
}

#[derive(Debug, Serialize)]
pub struct EventWire {
    event_id: Uuid,
    device_sequence: i64,
    schema_version: u32,
    source: &'static str,
    event_type: &'static str,
    policy_version: i64,
    occurred_at: String,
    payload: serde_json::Value,
}

#[derive(Debug, Deserialize)]
struct EventAck {
    schema_version: u32,
    accepted_sequences: Vec<i64>,
    server_time: String,
    request_id: Uuid,
}

/// V0.1 heartbeat fields. This explicit type is deliberately NOT generated
/// from arbitrary hardware collection; call only after device activation.
#[derive(Debug, Serialize)]
pub struct Heartbeat {
    pub schema_version: u32,
    pub device_id: Uuid,
    pub sent_at: String,
    pub agent_version: String,
    pub policy_version: i64,
    pub status: &'static str,
    pub capabilities: std::collections::BTreeMap<String, String>,
    pub queue_count: i64,
    pub queue_bytes: i64,
    pub oldest_pending_at: Option<String>,
    pub last_successful_upload_at: Option<String>,
    pub dropped_count: i64,
    pub integrity_degraded: bool,
    pub collection_errors: Vec<String>,
}

impl Heartbeat {
    /// Synthetic test status only; no claim that hardware is supported.
    pub fn synthetic(device_id: Uuid, sent_at: String) -> Self {
        Self {
            schema_version: 1,
            device_id,
            sent_at,
            agent_version: "0.1.0".into(),
            policy_version: 1,
            status: "running",
            capabilities: [
                ("hardware_inventory".into(), "not_collected".into()),
                ("app_activity".into(), "not_collected".into()),
            ]
            .into_iter()
            .collect(),
            queue_count: 0,
            queue_bytes: 0,
            oldest_pending_at: None,
            last_successful_upload_at: None,
            dropped_count: 0,
            integrity_degraded: false,
            collection_errors: Vec::new(),
        }
    }
}

impl EventBatch {
    pub fn from_pending(
        device_id: Uuid,
        batch_id: Uuid,
        pending: &[PendingEvent],
    ) -> Result<Self, TransportError> {
        if pending.is_empty() || pending.len() > MAX_BATCH_EVENTS {
            return Err(TransportError::InvalidEvent);
        }
        let mut seen = HashSet::with_capacity(pending.len());
        let mut previous = 0;
        let mut events = Vec::with_capacity(pending.len());
        for event in pending {
            if event.sequence <= previous
                || !seen.insert(event.sequence)
                || event.policy_version < 1
            {
                return Err(TransportError::InvalidEvent);
            }
            previous = event.sequence;
            if format!("{:x}", sha2::Sha256::digest(event.payload_json.as_bytes()))
                != event.content_digest
            {
                return Err(TransportError::InvalidEvent);
            }
            let payload: serde_json::Value = serde_json::from_str(&event.payload_json)?;
            if !payload.is_object()
                || event.occurred_at_utc.len() < 20
                || !event.occurred_at_utc.ends_with('Z')
            {
                return Err(TransportError::InvalidEvent);
            }
            events.push(EventWire {
                event_id: event.event_id,
                device_sequence: event.sequence,
                schema_version: 1,
                source: "synthetic_test",
                event_type: "protocol_fixture",
                policy_version: event.policy_version,
                occurred_at: event.occurred_at_utc.clone(),
                payload,
            });
        }
        let batch = Self {
            schema_version: 1,
            batch_id,
            device_id,
            events,
        };
        if serde_json::to_vec(&batch)?.len() > MAX_BATCH_BYTES {
            return Err(TransportError::InvalidEvent);
        }
        Ok(batch)
    }

    pub fn sequences(&self) -> Vec<i64> {
        self.events
            .iter()
            .map(|event| event.device_sequence)
            .collect()
    }
}

use sha2::Digest;

/// Validates a complete all-or-nothing ACK. Never accept a partial,
/// duplicate, invented or version-mismatched server response.
pub fn classify_batch_response(
    status: u16,
    response: &[u8],
    expected: &[i64],
    retry_after: Option<&str>,
) -> Result<DeliveryDisposition, TransportError> {
    if expected.is_empty()
        || expected.len() > MAX_BATCH_EVENTS
        || expected.iter().any(|s| *s <= 0)
        || expected.iter().copied().collect::<HashSet<_>>().len() != expected.len()
    {
        return Err(TransportError::InvalidEvent);
    }
    if status != 200 {
        return Ok(classify_error_status(status, retry_after));
    }
    if response.len() > MAX_RESPONSE_BYTES {
        return Err(TransportError::OversizedResponse);
    }
    let ack: EventAck =
        serde_json::from_slice(response).map_err(|_| TransportError::InvalidAcknowledgement)?;
    if ack.schema_version != 1
        || ack.server_time.len() < 20
        || !ack.server_time.ends_with('Z')
        || ack.request_id.is_nil()
        || ack.accepted_sequences.len() != expected.len()
        || ack
            .accepted_sequences
            .iter()
            .copied()
            .collect::<HashSet<_>>()
            != expected.iter().copied().collect::<HashSet<_>>()
    {
        return Err(TransportError::InvalidAcknowledgement);
    }
    Ok(DeliveryDisposition::Accepted)
}

pub fn classify_error_status(status: u16, retry_after: Option<&str>) -> DeliveryDisposition {
    match status {
        401 | 403 => DeliveryDisposition::PauseIdentity,
        429 | 500..=599 => {
            let seconds = retry_after
                .and_then(|s| s.parse::<u64>().ok())
                .map(|s| s.clamp(1, MAX_RETRY_AFTER_SECONDS))
                .unwrap_or(30);
            DeliveryDisposition::Retry {
                after: Duration::from_secs(seconds),
            }
        }
        _ => DeliveryDisposition::Quarantine,
    }
}

/// Builder requires a specifically approved trust root and mTLS client key.
/// Caller is responsible for secure OS key custody and certificate activation.
pub struct AgentTlsTransport {
    client: reqwest::Client,
}

impl AgentTlsTransport {
    pub fn new(
        origin: &str,
        ca_pem: &[u8],
        client_identity_pem: &[u8],
    ) -> Result<Self, TransportError> {
        if origin != AGENT_ORIGIN {
            return Err(TransportError::InvalidOrigin);
        }
        if ca_pem.is_empty() || client_identity_pem.is_empty() {
            return Err(TransportError::InvalidTlsMaterial);
        }
        let ca = reqwest::Certificate::from_pem(ca_pem)
            .map_err(|_| TransportError::InvalidTlsMaterial)?;
        let identity = reqwest::Identity::from_pem(client_identity_pem)
            .map_err(|_| TransportError::InvalidTlsMaterial)?;
        let client = reqwest::Client::builder()
            .tls_backend_rustls()
            .tls_certs_only([ca])
            .identity(identity)
            .https_only(true)
            .redirect(reqwest::redirect::Policy::none())
            .no_proxy()
            .tls_version_min(reqwest::tls::Version::TLS_1_2)
            .connect_timeout(Duration::from_secs(5))
            .timeout(Duration::from_secs(12))
            .pool_max_idle_per_host(1)
            .build()
            .map_err(TransportError::Tls)?;
        Ok(Self { client })
    }

    async fn post_json<T: Serialize>(
        &self,
        path: &str,
        payload: &T,
    ) -> Result<(u16, Vec<u8>, Option<String>), TransportError> {
        let body = serde_json::to_vec(payload)?;
        if body.len() > MAX_BATCH_BYTES {
            return Err(TransportError::InvalidEvent);
        }
        // All paths are constructed below from a parsed UUID, never supplied
        // by a remote peer. Client is HTTPS-only, redirects and proxies off.
        let url = format!("{AGENT_ORIGIN}{path}");
        let mut response = self
            .client
            .post(url)
            .header(CONTENT_TYPE, "application/json")
            .body(body)
            .send()
            .await
            .map_err(TransportError::Network)?;
        let status = response.status().as_u16();
        let retry_after = response
            .headers()
            .get(RETRY_AFTER)
            .and_then(|v| v.to_str().ok())
            .map(str::to_owned);
        let mut bytes = Vec::new();
        while let Some(chunk) = response.chunk().await.map_err(TransportError::Network)? {
            if bytes.len() + chunk.len() > MAX_RESPONSE_BYTES {
                return Err(TransportError::OversizedResponse);
            }
            bytes.extend_from_slice(&chunk);
        }
        Ok((status, bytes, retry_after))
    }

    /// Never called by AGT-01 service, and no live Agent authorization exists.
    pub async fn send_heartbeat(
        &self,
        heartbeat: &Heartbeat,
    ) -> Result<DeliveryDisposition, TransportError> {
        if heartbeat.schema_version != 1
            || heartbeat.policy_version < 1
            || heartbeat.queue_count < 0
            || heartbeat.queue_bytes < 0
            || heartbeat.dropped_count < 0
            || heartbeat.device_id.is_nil()
        {
            return Err(TransportError::InvalidEvent);
        }
        let path = format!("/api/v1/devices/{}/heartbeats", heartbeat.device_id);
        let (status, body, retry_after) = self.post_json(&path, heartbeat).await?;
        if status != 200 {
            return Ok(classify_error_status(status, retry_after.as_deref()));
        }
        let response: serde_json::Value =
            serde_json::from_slice(&body).map_err(|_| TransportError::InvalidAcknowledgement)?;
        if response
            .get("server_time")
            .and_then(serde_json::Value::as_str)
            .is_none_or(|v| v.len() < 20 || !v.ends_with('Z'))
            || response
                .get("request_id")
                .and_then(serde_json::Value::as_str)
                .and_then(|v| Uuid::parse_str(v).ok())
                .is_none()
        {
            return Err(TransportError::InvalidAcknowledgement);
        }
        Ok(DeliveryDisposition::Accepted)
    }

    /// One explicit attempt using SQLx's durable prepared batch identity.
    /// A network timeout returns without ACK and the next attempt reuses
    /// the exact same batch UUID and selected events.
    pub async fn send_next_synthetic_batch(
        &self,
        store: &OutboxStore,
        device_id: Uuid,
    ) -> Result<Option<DeliveryDisposition>, TransportError> {
        if device_id.is_nil() {
            return Err(TransportError::InvalidEvent);
        }
        let Some(prepared) = store.prepare_synthetic_batch().await? else {
            return Ok(None);
        };
        let batch = EventBatch::from_pending(device_id, prepared.batch_id, &prepared.events)?;
        self.send_synthetic_batch(store, &batch).await.map(Some)
    }

    /// One network attempt per call; caller schedules bounded retry/backoff.
    /// This method deletes local entries ONLY after full validated 200 ACK.
    async fn send_synthetic_batch(
        &self,
        store: &OutboxStore,
        batch: &EventBatch,
    ) -> Result<DeliveryDisposition, TransportError> {
        if batch.schema_version != 1 || batch.device_id.is_nil() || batch.batch_id.is_nil() {
            return Err(TransportError::InvalidEvent);
        }
        let sequences = batch.sequences();
        if sequences.is_empty() || sequences.len() > MAX_BATCH_EVENTS {
            return Err(TransportError::InvalidEvent);
        }
        let path = format!("/api/v1/devices/{}/event-batches", batch.device_id);
        let (status, response, retry_after) = self.post_json(&path, batch).await?;
        let disposition =
            classify_batch_response(status, &response, &sequences, retry_after.as_deref())?;
        if disposition == DeliveryDisposition::Accepted {
            // Server committed all events and acknowledged every sequence.
            store.acknowledge(&sequences).await?;
        }
        Ok(disposition)
    }
}
