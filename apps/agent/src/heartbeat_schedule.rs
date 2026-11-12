//! Batch A3 local-only heartbeat attempt state machine.
//! No networking, timers, OS data, or service startup is performed here.
//! A crash after an HTTP request but before its ACK is intentionally ambiguous:
//! never mint a fresh UUID or retry automatically without reconciliation.
use crate::transport::DeliveryDisposition;
use serde::{Deserialize, Serialize};
use thiserror::Error;
use uuid::Uuid;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum HeartbeatHalt {
    Identity,
    Quarantine,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct HeartbeatCheckpoint {
    pub schema_version: u32,
    pub device_id: Uuid,
    /// Local-only scheduling observation, never the canonical server receive time.
    pub last_acknowledged_at: Option<u64>,
    /// Durable identity of a possibly in-flight request. Not an HTTP ACK.
    pub pending_request_id: Option<Uuid>,
    pub halted: Option<HeartbeatHalt>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum HeartbeatDecision {
    Disabled,
    Ready,
    WaitUntil(u64),
    NeedsReconciliation(Uuid),
    Halted(HeartbeatHalt),
}

#[derive(Debug, Error, PartialEq, Eq)]
pub enum HeartbeatScheduleError {
    #[error("invalid or cross-device heartbeat checkpoint")]
    InvalidCheckpoint,
    #[error("heartbeat request requires explicit authorization and a fresh checkpoint")]
    NotReady,
    #[error("an already halted heartbeat cannot be resumed implicitly")]
    Halted,
}

impl HeartbeatCheckpoint {
    pub fn initial(device_id: Uuid) -> Result<Self, HeartbeatScheduleError> {
        if device_id.is_nil() {
            return Err(HeartbeatScheduleError::InvalidCheckpoint);
        }
        Ok(Self {
            schema_version: 1,
            device_id,
            last_acknowledged_at: None,
            pending_request_id: None,
            halted: None,
        })
    }

    pub fn validate(&self, device_id: Uuid) -> Result<(), HeartbeatScheduleError> {
        if device_id.is_nil()
            || self.schema_version != 1
            || self.device_id != device_id
            || self.pending_request_id.is_some_and(|id| id.is_nil())
            || (self.halted.is_some() && self.pending_request_id.is_none())
        {
            return Err(HeartbeatScheduleError::InvalidCheckpoint);
        }
        Ok(())
    }

    /// Does not read a clock or schedule a task. An existing in-flight request
    /// is blocked on restart instead of being sent again with changed fields.
    pub fn decide(
        &self,
        enabled: bool,
        now: u64,
        interval_seconds: u64,
    ) -> Result<HeartbeatDecision, HeartbeatScheduleError> {
        self.validate(self.device_id)?;
        if !(5..=300).contains(&interval_seconds) {
            return Err(HeartbeatScheduleError::InvalidCheckpoint);
        }
        if !enabled {
            return Ok(HeartbeatDecision::Disabled);
        }
        if let Some(reason) = self.halted {
            return Ok(HeartbeatDecision::Halted(reason));
        }
        if let Some(id) = self.pending_request_id {
            return Ok(HeartbeatDecision::NeedsReconciliation(id));
        }
        if let Some(last) = self.last_acknowledged_at {
            let next = last.saturating_add(interval_seconds);
            if now < next {
                return Ok(HeartbeatDecision::WaitUntil(next));
            }
        }
        Ok(HeartbeatDecision::Ready)
    }

    /// Caller must persist the returned checkpoint BEFORE an authorized
    /// one-shot send. The installed Agent does not call this API.
    pub fn begin(
        &mut self,
        enabled: bool,
        now: u64,
        interval_seconds: u64,
        request_id: Uuid,
    ) -> Result<(), HeartbeatScheduleError> {
        if request_id.is_nil() {
            return Err(HeartbeatScheduleError::InvalidCheckpoint);
        }
        if self.decide(enabled, now, interval_seconds)? != HeartbeatDecision::Ready {
            return Err(HeartbeatScheduleError::NotReady);
        }
        self.pending_request_id = Some(request_id);
        Ok(())
    }

    /// Only a separately authenticated, exactly-matching 200 ACK may clear
    /// pending. 401/403 pauses the device; 409, 5xx or uncertain outcome
    /// quarantine without silently losing an ambiguous request identity.
    pub fn finish(
        &mut self,
        request_id: Uuid,
        now: u64,
        disposition: DeliveryDisposition,
    ) -> Result<(), HeartbeatScheduleError> {
        if self.halted.is_some() {
            return Err(HeartbeatScheduleError::Halted);
        }
        self.validate(self.device_id)?;
        if request_id.is_nil() || self.pending_request_id != Some(request_id) {
            return Err(HeartbeatScheduleError::InvalidCheckpoint);
        }
        match disposition {
            DeliveryDisposition::Accepted => {
                self.pending_request_id = None;
                self.last_acknowledged_at =
                    Some(self.last_acknowledged_at.map_or(now, |last| last.max(now)));
            }
            DeliveryDisposition::PauseIdentity => {
                self.halted = Some(HeartbeatHalt::Identity);
            }
            DeliveryDisposition::Quarantine | DeliveryDisposition::Retry { .. } => {
                self.halted = Some(HeartbeatHalt::Quarantine);
            }
        }
        Ok(())
    }

    /// A network error, lost response or process crash cannot imply an ACK.
    pub fn note_uncertain_outcome(&mut self) -> Result<(), HeartbeatScheduleError> {
        self.validate(self.device_id)?;
        if self.pending_request_id.is_none() || self.halted.is_some() {
            return Err(HeartbeatScheduleError::NotReady);
        }
        self.halted = Some(HeartbeatHalt::Quarantine);
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::time::Duration;

    fn device() -> Uuid {
        Uuid::parse_str("018f9fad-5c71-7db1-b89e-b71f8d620f44").unwrap()
    }

    #[test]
    fn disabled_has_no_attempt_or_network_side_effects() {
        let mut checkpoint = HeartbeatCheckpoint::initial(device()).unwrap();
        let request_id = Uuid::now_v7();
        assert_eq!(
            checkpoint.decide(false, 100, 30).unwrap(),
            HeartbeatDecision::Disabled
        );
        assert_eq!(
            checkpoint.begin(false, 100, 30, request_id),
            Err(HeartbeatScheduleError::NotReady)
        );
        assert_eq!(checkpoint.pending_request_id, None);
        assert!(HeartbeatCheckpoint::initial(Uuid::nil()).is_err());
        assert!(checkpoint.decide(true, 100, 0).is_err());
    }

    #[test]
    fn exact_ack_releases_pending_and_enforces_bounded_cadence() {
        let mut checkpoint = HeartbeatCheckpoint::initial(device()).unwrap();
        let id = Uuid::now_v7();
        checkpoint.begin(true, 1_000, 30, id).unwrap();
        assert_eq!(
            checkpoint.decide(true, 1_001, 30).unwrap(),
            HeartbeatDecision::NeedsReconciliation(id)
        );
        assert!(
            checkpoint
                .finish(Uuid::now_v7(), 1_005, DeliveryDisposition::Accepted)
                .is_err()
        );
        checkpoint
            .finish(id, 1_005, DeliveryDisposition::Accepted)
            .unwrap();
        assert_eq!(
            checkpoint.decide(true, 1_034, 30).unwrap(),
            HeartbeatDecision::WaitUntil(1_035)
        );
        assert_eq!(
            checkpoint.decide(true, 1_035, 30).unwrap(),
            HeartbeatDecision::Ready
        );
    }

    #[test]
    fn identity_replay_server_failure_and_timeout_never_restart_automatically() {
        for (disposition, halt) in [
            (DeliveryDisposition::PauseIdentity, HeartbeatHalt::Identity),
            (DeliveryDisposition::Quarantine, HeartbeatHalt::Quarantine),
            (
                DeliveryDisposition::Retry {
                    after: Duration::from_secs(30),
                },
                HeartbeatHalt::Quarantine,
            ),
        ] {
            let mut checkpoint = HeartbeatCheckpoint::initial(device()).unwrap();
            let id = Uuid::now_v7();
            checkpoint.begin(true, 100, 30, id).unwrap();
            checkpoint.finish(id, 101, disposition).unwrap();
            let saved = serde_json::to_string(&checkpoint).unwrap();
            let restored: HeartbeatCheckpoint = serde_json::from_str(&saved).unwrap();
            assert_eq!(restored.pending_request_id, Some(id));
            assert_eq!(
                restored.decide(true, 100_000, 30).unwrap(),
                HeartbeatDecision::Halted(halt)
            );
            assert_eq!(
                checkpoint.finish(id, 102, DeliveryDisposition::Accepted),
                Err(HeartbeatScheduleError::Halted)
            );
        }
        let mut unknown = HeartbeatCheckpoint::initial(device()).unwrap();
        let id = Uuid::now_v7();
        unknown.begin(true, 100, 30, id).unwrap();
        unknown.note_uncertain_outcome().unwrap();
        assert_eq!(unknown.pending_request_id, Some(id));
        assert_eq!(
            unknown.decide(true, 100_000, 30).unwrap(),
            HeartbeatDecision::Halted(HeartbeatHalt::Quarantine)
        );
    }

    #[test]
    fn malformed_cross_device_and_untrusted_json_are_rejected() {
        let mut checkpoint = HeartbeatCheckpoint::initial(device()).unwrap();
        checkpoint.pending_request_id = Some(Uuid::nil());
        assert!(checkpoint.validate(device()).is_err());
        checkpoint.pending_request_id = None;
        checkpoint.halted = Some(HeartbeatHalt::Identity);
        assert!(checkpoint.validate(device()).is_err());
        checkpoint.halted = None;
        assert!(checkpoint.validate(Uuid::now_v7()).is_err());
        let mut json = serde_json::to_value(checkpoint).unwrap();
        json["unapproved"] = serde_json::json!(true);
        assert!(serde_json::from_value::<HeartbeatCheckpoint>(json).is_err());
    }
}
