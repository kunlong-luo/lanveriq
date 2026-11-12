//! AGT-03 opt-in (NOT service-wired) HTTPS/mTLS adapter.
//! No local plaintext fallback, no arbitrary redirect/proxy, and no ACK
//! unless the signed-in server response names EXACTLY every sent sequence.
//! The *server* must additionally bind Caddy-verified mTLS fingerprint to
//! an active PostgreSQL device; this module cannot grant that trust.
use crate::store::{OutboxStore, PendingEvent, PendingInventory, StoreError};
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
    #[error("invalid typed inventory payload or device identity")]
    InvalidInventory,
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

/// Batch A2: one device activation attempt against the separately gated
/// Spring POST route. This carries only a correlation UUID; it never conveys
/// authorization, enrollment tokens, CSR private keys or real device data.
#[derive(Debug, Serialize)]
pub struct ActivationRequest {
    pub schema_version: u32,
    pub device_id: Uuid,
    pub agent_version: String,
    pub request_id: Uuid,
}

impl ActivationRequest {
    /// Synthetic-only constructor; the running Agent does not call this.
    pub fn synthetic(device_id: Uuid) -> Self {
        Self {
            schema_version: 1,
            device_id,
            agent_version: "0.1.0".into(),
            request_id: Uuid::now_v7(),
        }
    }

    fn is_valid(&self) -> bool {
        let bytes = self.agent_version.as_bytes();
        self.schema_version == 1
            && !self.device_id.is_nil()
            && !self.request_id.is_nil()
            && !bytes.is_empty()
            && bytes.len() <= 64
            && bytes[0].is_ascii_alphanumeric()
            && bytes[1..]
                .iter()
                .all(|c| c.is_ascii_alphanumeric() || matches!(*c, b'.' | b'_' | b'-'))
    }
}

/// The exact five-field Spring activation receipt, decoded fail-closed.
#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
struct ActivationAck {
    schema_version: u32,
    device_id: Uuid,
    enrollment_state: String,
    server_time: String,
    request_id: Uuid,
}

/// Returns Accepted only for a 200 receipt bound to this device and attempt.
/// A raw 200, unrelated UUID or success-shaped response is never approval.
pub fn classify_activation_response(
    status: u16,
    body: &[u8],
    expected_device_id: Uuid,
    expected_request_id: Uuid,
    retry_after: Option<&str>,
) -> Result<DeliveryDisposition, TransportError> {
    if expected_device_id.is_nil() || expected_request_id.is_nil() {
        return Err(TransportError::InvalidEvent);
    }
    if status != 200 {
        return Ok(classify_error_status(status, retry_after));
    }
    if body.len() > MAX_RESPONSE_BYTES {
        return Err(TransportError::OversizedResponse);
    }
    let ack: ActivationAck =
        serde_json::from_slice(body).map_err(|_| TransportError::InvalidAcknowledgement)?;
    if ack.schema_version != 1
        || ack.device_id != expected_device_id
        || ack.request_id != expected_request_id
        || ack.enrollment_state != "active"
        || !is_utc_instant_shape(&ack.server_time)
    {
        return Err(TransportError::InvalidAcknowledgement);
    }
    Ok(DeliveryDisposition::Accepted)
}

/// Batch A: strict positive envelope for one explicit heartbeat attempt.
/// The server must echo the request UUID provided in the outbound envelope.
/// Heartbeats are still NOT scheduled by the Agent service binary.
#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
struct HeartbeatAck {
    server_time: String,
    request_id: Uuid,
}

/// Pure fail-closed check. Never interpret a malformed 200 response as an ACK.
pub fn classify_heartbeat_response(
    status: u16,
    body: &[u8],
    expected_request_id: Uuid,
    retry_after: Option<&str>,
) -> Result<DeliveryDisposition, TransportError> {
    if expected_request_id.is_nil() {
        return Err(TransportError::InvalidEvent);
    }
    if status != 200 {
        return Ok(classify_error_status(status, retry_after));
    }
    if body.len() > MAX_RESPONSE_BYTES {
        return Err(TransportError::OversizedResponse);
    }
    let ack: HeartbeatAck =
        serde_json::from_slice(body).map_err(|_| TransportError::InvalidAcknowledgement)?;
    if ack.request_id != expected_request_id || !is_utc_instant_shape(&ack.server_time) {
        return Err(TransportError::InvalidAcknowledgement);
    }
    Ok(DeliveryDisposition::Accepted)
}

/// Strict type for one CURRENT-credential mTLS read of the committed request
/// ledger. The verified positive is proof of durable ID membership, not of a
/// new heartbeat POST or an original response timestamp.
#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
struct HeartbeatLookupReceipt {
    schema_version: u32,
    device_id: Uuid,
    request_id: Uuid,
    committed: bool,
    server_time: String,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum HeartbeatLookupDisposition {
    Committed,
    /// 404 and transient errors cannot prove a request was never committed.
    Unresolved,
    PauseIdentity,
    Quarantine,
}

/// A missing row is never permission to retry or mint a new request UUID.
pub fn classify_heartbeat_lookup_response(
    status: u16,
    body: &[u8],
    device_id: Uuid,
    request_id: Uuid,
) -> Result<HeartbeatLookupDisposition, TransportError> {
    if device_id.is_nil() || request_id.is_nil() {
        return Err(TransportError::InvalidEvent);
    }
    match status {
        200 => {
            if body.len() > MAX_RESPONSE_BYTES {
                return Err(TransportError::OversizedResponse);
            }
            let receipt: HeartbeatLookupReceipt =
                serde_json::from_slice(body).map_err(|_| TransportError::InvalidAcknowledgement)?;
            if receipt.schema_version != 1
                || receipt.device_id != device_id
                || receipt.request_id != request_id
                || !receipt.committed
                || !is_utc_instant_shape(&receipt.server_time)
            {
                return Err(TransportError::InvalidAcknowledgement);
            }
            Ok(HeartbeatLookupDisposition::Committed)
        }
        404 | 429 | 500..=599 => Ok(HeartbeatLookupDisposition::Unresolved),
        401 | 403 => Ok(HeartbeatLookupDisposition::PauseIdentity),
        _ => Ok(HeartbeatLookupDisposition::Quarantine),
    }
}

/// UTC ISO-8601 shape only. Server/database time is authoritative; do not
/// interpret a client-parsed string as evidence about device online status.
fn is_utc_instant_shape(value: &str) -> bool {
    let bytes = value.as_bytes();
    if bytes.len() < 20 || bytes.len() > 40 || !value.ends_with('Z') {
        return false;
    }
    for (index, expected) in [(4, b'-'), (7, b'-'), (10, b'T'), (13, b':'), (16, b':')] {
        if bytes[index] != expected {
            return false;
        }
    }
    for (begin, end) in [(0, 4), (5, 7), (8, 10), (11, 13), (14, 16), (17, 19)] {
        if !bytes[begin..end].iter().all(u8::is_ascii_digit) {
            return false;
        }
    }
    let fraction = &bytes[19..bytes.len() - 1];
    fraction.is_empty()
        || (fraction.len() >= 2
            && fraction[0] == b'.'
            && fraction[1..].iter().all(u8::is_ascii_digit))
}

#[derive(Debug, Deserialize)]
struct EventAck {
    schema_version: u32,
    accepted_sequences: Vec<i64>,
    server_time: String,
    request_id: Uuid,
}

/// Exact internal Spring V0.1 inventory receipt, not an event-batch ACK.
#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
struct InventoryAck {
    schema_version: u32,
    device_id: Uuid,
    snapshot_id: Uuid,
    inventory_revision: i64,
    outcome: InventoryOutcome,
    server_time: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "snake_case")]
enum InventoryOutcome {
    Applied,
    Duplicate,
    StaleIgnored,
}

/// V0.1 heartbeat fields. This explicit type is deliberately NOT generated
/// from arbitrary hardware collection; call only after device activation.
#[derive(Debug, Serialize)]
pub struct Heartbeat {
    pub schema_version: u32,
    pub device_id: Uuid,
    /// Unique ID for this attempt, generated locally and echoed after commit.
    pub request_id: Uuid,
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
            request_id: Uuid::now_v7(),
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

/// Only an exact authenticated HTTP 200 receipt for the sent tuple is
/// eligible for removal from the local outbox. A stale_ignored response
/// refers to a newer server state and must not be treated as an exact ACK.
pub fn classify_inventory_response(
    status: u16,
    response: &[u8],
    device_id: Uuid,
    expected: &PendingInventory,
    retry_after: Option<&str>,
) -> Result<DeliveryDisposition, TransportError> {
    if device_id.is_nil()
        || expected.snapshot_id.is_nil()
        || expected.revision < 1
        || expected.body_json.is_empty()
        || expected.body_json.len() > 128 * 1024
    {
        return Err(TransportError::InvalidInventory);
    }
    if status != 200 {
        return Ok(classify_error_status(status, retry_after));
    }
    if response.len() > MAX_RESPONSE_BYTES {
        return Err(TransportError::OversizedResponse);
    }
    let ack: InventoryAck =
        serde_json::from_slice(response).map_err(|_| TransportError::InvalidAcknowledgement)?;
    if ack.schema_version != 1
        || ack.device_id != device_id
        || ack.snapshot_id.is_nil()
        || ack.inventory_revision < 1
        || ack.server_time.len() < 20
        || ack.server_time.len() > 40
        || !ack.server_time.ends_with('Z')
    {
        return Err(TransportError::InvalidAcknowledgement);
    }
    if matches!(ack.outcome, InventoryOutcome::StaleIgnored) {
        // Server reports a *newer current snapshot*, not the submitted ID.
        // Never delete or retry indefinitely without an operator reconciliation.
        return Ok(DeliveryDisposition::Quarantine);
    }
    if ack.snapshot_id != expected.snapshot_id || ack.inventory_revision != expected.revision {
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
            // Batch A P0: pin the external Agent hop to TLS 1.3 only.
            // This does not assert Caddy listener configuration or
            // prove a completed two-hop mTLS handshake; L2 must do that.
            .tls_version_min(reqwest::tls::Version::TLS_1_3)
            .tls_version_max(reqwest::tls::Version::TLS_1_3)
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
        self.post_serialized_json(path, &body).await
    }

    /// Pass the already validated persisted inventory bytes unchanged;
    /// serializing a JSON string again would change its wire digest.
    async fn post_serialized_json(
        &self,
        path: &str,
        body: &[u8],
    ) -> Result<(u16, Vec<u8>, Option<String>), TransportError> {
        if body.is_empty() || body.len() > MAX_BATCH_BYTES {
            return Err(TransportError::InvalidEvent);
        }
        // All paths are constructed below from a parsed UUID, never supplied
        // by a remote peer. Client is HTTPS-only, redirects and proxies off.
        let url = format!("{AGENT_ORIGIN}{path}");
        let mut response = self
            .client
            .post(url)
            .header(CONTENT_TYPE, "application/json")
            .body(body.to_vec())
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

    /// A single optional activation request for an already-approved identity.
    /// Neither the AGT-01 runtime nor installer calls this method. HTTP alone
    /// cannot issue, review or authorize a certificate; the server must verify
    /// both mTLS hops and the registered pending credential transactionally.
    pub async fn send_activation(
        &self,
        activation: &ActivationRequest,
    ) -> Result<DeliveryDisposition, TransportError> {
        if !activation.is_valid() {
            return Err(TransportError::InvalidEvent);
        }
        let path = format!("/api/v1/devices/{}/activate", activation.device_id);
        let (status, body, retry_after) = self.post_json(&path, activation).await?;
        classify_activation_response(
            status,
            &body,
            activation.device_id,
            activation.request_id,
            retry_after.as_deref(),
        )
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
            || heartbeat.request_id.is_nil()
        {
            return Err(TransportError::InvalidEvent);
        }
        let path = format!("/api/v1/devices/{}/heartbeats", heartbeat.device_id);
        let (status, body, retry_after) = self.post_json(&path, heartbeat).await?;
        if status != 200 {
            return Ok(classify_error_status(status, retry_after.as_deref()));
        }
        classify_heartbeat_response(status, &body, heartbeat.request_id, retry_after.as_deref())
    }

    /// Read-only reconciliation of one previously persisted request UUID.
    /// Only a current registered mTLS credential may read accepted ledger
    /// membership. 404/5xx leave local pending state UNRESOLVED, not retried.
    /// No installed service calls this helper; no OS data is collected.
    pub async fn lookup_heartbeat_request(
        &self,
        device_id: Uuid,
        request_id: Uuid,
    ) -> Result<HeartbeatLookupDisposition, TransportError> {
        if device_id.is_nil() || request_id.is_nil() {
            return Err(TransportError::InvalidEvent);
        }
        let path = format!(
            "/api/v1/devices/{device_id}/heartbeats/requests/{request_id}"
        );
        let url = format!("{AGENT_ORIGIN}{path}");
        let mut response = self.client.get(url).send().await.map_err(TransportError::Network)?;
        let status = response.status().as_u16();
        let mut bytes = Vec::new();
        while let Some(chunk) = response.chunk().await.map_err(TransportError::Network)? {
            if bytes.len() + chunk.len() > MAX_RESPONSE_BYTES {
                return Err(TransportError::OversizedResponse);
            }
            bytes.extend_from_slice(&chunk);
        }
        classify_heartbeat_lookup_response(status, &bytes, device_id, request_id)
    }

    /// Staged opt-in inventory attempt. NEVER called by AGT-01 service.
    /// Read only the integrity-checked, device-bound SQLite wire payload.
    /// No internal retry loop, scheduler, OS probing, or service startup.
    pub async fn send_next_inventory(
        &self,
        store: &OutboxStore,
        device_id: Uuid,
    ) -> Result<Option<DeliveryDisposition>, TransportError> {
        if device_id.is_nil() {
            return Err(TransportError::InvalidInventory);
        }
        let Some(pending) = store.verified_pending_inventory(device_id).await? else {
            return Ok(None);
        };
        let path = format!("/api/v1/devices/{device_id}/inventory");
        let (status, response, retry_after) = self
            .post_serialized_json(&path, pending.body_json.as_bytes())
            .await?;
        let disposition = classify_inventory_response(
            status,
            &response,
            device_id,
            &pending,
            retry_after.as_deref(),
        )?;
        if disposition == DeliveryDisposition::Accepted {
            // A newer local snapshot may supersede this one in flight.
            // Exact device+snapshot+revision CAS protects the replacement.
            let _ = store
                .acknowledge_inventory_snapshot(device_id, pending.snapshot_id, pending.revision)
                .await?;
        }
        Ok(Some(disposition))
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

#[cfg(test)]
#[path = "transport_inventory_tests.rs"]
mod inventory_transport_tests;

#[cfg(test)]
#[path = "transport_heartbeat_tests.rs"]
mod heartbeat_transport_tests;

#[cfg(test)]
#[path = "transport_activation_tests.rs"]
mod activation_transport_tests;
