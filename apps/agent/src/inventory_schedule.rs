//! INV-01-E: pure, opt-in inventory scheduling decisions.
//! No clocks are read, no tasks spawned, no OS data collected, no HTTP sent.
use crate::transport::DeliveryDisposition;
use serde::{Deserialize, Serialize};
use std::time::Duration;
use thiserror::Error;
use uuid::Uuid;

pub const RECONCILE_SECONDS: u64 = 24 * 60 * 60;
pub const CHANGE_DEBOUNCE_SECONDS: u64 = 30;
const BASE_RETRY_SECONDS: u64 = 30;
const MAX_RETRY_SECONDS: u64 = 60 * 60;
const MAX_FAILURE_COUNT: u8 = 8;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum ScheduleHalt {
    Identity,
    Quarantine,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct InventoryScheduleCheckpoint {
    pub schema_version: u32,
    pub device_id: Uuid,
    pub last_reconciled_at: Option<u64>,
    pub change_detected_at: Option<u64>,
    pub retry_not_before: Option<u64>,
    pub failures: u8,
    pub halted: Option<ScheduleHalt>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CollectionReason {
    Startup,
    Reconcile,
    AssetChange,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ScheduleDecision {
    Disabled,
    Halted(ScheduleHalt),
    Collect(CollectionReason),
    DeliverPending,
    WaitUntil(u64),
    Idle,
}

#[derive(Debug, Error, PartialEq, Eq)]
pub enum ScheduleError {
    #[error("invalid or cross-device inventory schedule checkpoint")]
    InvalidCheckpoint,
    #[error("scheduler is halted pending an explicitly approved reconciliation")]
    Halted,
}

/// Pure decision engine, never constructed or used by the Agent service.
/// The enabled flag must come from a later explicitly authorized integration;
/// the default is OFF, and this module introduces no runtime configuration.
pub struct InventoryScheduler {
    enabled: bool,
    startup_check_due: bool,
    checkpoint: InventoryScheduleCheckpoint,
}

impl InventoryScheduleCheckpoint {
    pub fn initial(device_id: Uuid) -> Result<Self, ScheduleError> {
        if device_id.is_nil() {
            return Err(ScheduleError::InvalidCheckpoint);
        }
        Ok(Self {
            schema_version: 1,
            device_id,
            last_reconciled_at: None,
            change_detected_at: None,
            retry_not_before: None,
            failures: 0,
            halted: None,
        })
    }

    pub fn validate(&self, device_id: Uuid) -> Result<(), ScheduleError> {
        if device_id.is_nil()
            || self.schema_version != 1
            || self.device_id != device_id
            || self.failures > MAX_FAILURE_COUNT
            || (self.halted.is_some() && self.retry_not_before.is_some())
            || (self.failures == 0 && self.retry_not_before.is_some())
            || (self.failures != 0 && self.retry_not_before.is_none() && self.halted.is_none())
        {
            return Err(ScheduleError::InvalidCheckpoint);
        }
        Ok(())
    }
}

impl InventoryScheduler {
    pub fn restore(
        device_id: Uuid,
        enabled: bool,
        saved: Option<InventoryScheduleCheckpoint>,
    ) -> Result<Self, ScheduleError> {
        let checkpoint = match saved {
            Some(saved) => saved,
            None => InventoryScheduleCheckpoint::initial(device_id)?,
        };
        checkpoint.validate(device_id)?;
        Ok(Self {
            enabled,
            startup_check_due: true,
            checkpoint,
        })
    }

    pub fn checkpoint(&self) -> &InventoryScheduleCheckpoint {
        &self.checkpoint
    }

    /// Called only with an explicit synthetic/approved change notification.
    /// Coalesce repeated signals without postponing the first notification.
    pub fn note_asset_change(&mut self, now: u64) {
        self.checkpoint.change_detected_at = Some(
            self.checkpoint
                .change_detected_at
                .map_or(now, |first| first.min(now)),
        );
    }

    /// The pending flag must come from the verified device-bound SQLite outbox,
    /// never from a caller's guess or unauthenticated server response.
    pub fn decide(&self, now: u64, has_verified_pending: bool) -> ScheduleDecision {
        if !self.enabled {
            return ScheduleDecision::Disabled;
        }
        if let Some(halt) = self.checkpoint.halted {
            return ScheduleDecision::Halted(halt);
        }
        // Retries always reuse the original persisted UUID/revision. A restart
        // cannot evade a prior retry deadline or identity/security pause.
        if has_verified_pending {
            if let Some(until) = self.checkpoint.retry_not_before
                && now < until
            {
                return ScheduleDecision::WaitUntil(until);
            }
            return ScheduleDecision::DeliverPending;
        }
        if self.startup_check_due {
            return ScheduleDecision::Collect(CollectionReason::Startup);
        }
        if self
            .checkpoint
            .last_reconciled_at
            .is_none_or(|at| now >= at.saturating_add(RECONCILE_SECONDS))
        {
            return ScheduleDecision::Collect(CollectionReason::Reconcile);
        }
        if let Some(at) = self.checkpoint.change_detected_at {
            let due = at.saturating_add(CHANGE_DEBOUNCE_SECONDS);
            if now >= due {
                return ScheduleDecision::Collect(CollectionReason::AssetChange);
            }
            return ScheduleDecision::WaitUntil(due);
        }
        ScheduleDecision::Idle
    }

    /// Call only after a successful complete normalization/check of assets,
    /// including an unchanged snapshot. Does not acknowledge or upload data.
    pub fn note_collection_completed(&mut self, now: u64) -> Result<(), ScheduleError> {
        if self.checkpoint.halted.is_some() {
            return Err(ScheduleError::Halted);
        }
        self.checkpoint.last_reconciled_at = Some(now);
        self.checkpoint.change_detected_at = None;
        self.startup_check_due = false;
        Ok(())
    }

    /// Map the already-classified response from the strict transport module.
    /// Accepted means the caller has independently validated an exact ACK;
    /// this method NEVER deletes the pending SQLite inventory.
    pub fn note_delivery_result(
        &mut self,
        now: u64,
        disposition: &DeliveryDisposition,
    ) -> Result<(), ScheduleError> {
        if self.checkpoint.halted.is_some() {
            return Err(ScheduleError::Halted);
        }
        match disposition {
            DeliveryDisposition::Accepted => {
                self.checkpoint.failures = 0;
                self.checkpoint.retry_not_before = None;
            }
            DeliveryDisposition::PauseIdentity => {
                self.checkpoint.halted = Some(ScheduleHalt::Identity);
                self.checkpoint.retry_not_before = None;
            }
            DeliveryDisposition::Quarantine => {
                self.checkpoint.halted = Some(ScheduleHalt::Quarantine);
                self.checkpoint.retry_not_before = None;
            }
            DeliveryDisposition::Retry { after } => {
                self.checkpoint.failures = self
                    .checkpoint
                    .failures
                    .saturating_add(1)
                    .min(MAX_FAILURE_COUNT);
                let shift = u32::from(self.checkpoint.failures - 1);
                let exponential = BASE_RETRY_SECONDS.saturating_mul(1_u64 << shift);
                // Stable per-device spread, not an authentication or security token.
                let bytes: [u8; 8] = self.checkpoint.device_id.as_bytes()[..8]
                    .try_into()
                    .map_err(|_| ScheduleError::InvalidCheckpoint)?;
                let spread = u64::from_le_bytes(bytes);
                let jitter = spread.wrapping_add(u64::from(self.checkpoint.failures))
                    % (exponential.min(480) / 4 + 1);
                let server_delay = after
                    .as_secs()
                    .saturating_add(u64::from(after.subsec_nanos() > 0));
                let delay = exponential
                    .saturating_add(jitter)
                    .max(server_delay)
                    .min(MAX_RETRY_SECONDS);
                self.checkpoint.retry_not_before = Some(now.saturating_add(delay));
            }
        }
        Ok(())
    }

    /// Network errors/timeouts leave ACK and pending snapshot untouched.
    pub fn note_transport_failure(&mut self, now: u64) -> Result<(), ScheduleError> {
        self.note_delivery_result(
            now,
            &DeliveryDisposition::Retry {
                after: Duration::ZERO,
            },
        )
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn device() -> Uuid {
        Uuid::parse_str("018f9fad-5c71-7db1-b89e-b71f8d620f44").unwrap()
    }

    fn scheduler(enabled: bool) -> InventoryScheduler {
        InventoryScheduler::restore(device(), enabled, None).unwrap()
    }

    #[test]
    fn opt_in_is_off_even_at_startup_and_with_pending() {
        let scheduler = scheduler(false);
        assert_eq!(scheduler.decide(1_000, false), ScheduleDecision::Disabled);
        assert_eq!(scheduler.decide(1_000, true), ScheduleDecision::Disabled);
    }

    #[test]
    fn startup_then_24h_reconcile_without_heartbeat_upload_churn() {
        let mut scheduler = scheduler(true);
        assert_eq!(
            scheduler.decide(1_000, false),
            ScheduleDecision::Collect(CollectionReason::Startup)
        );
        scheduler.note_collection_completed(1_000).unwrap();
        assert_eq!(
            scheduler.decide(1_000 + RECONCILE_SECONDS - 1, false),
            ScheduleDecision::Idle
        );
        assert_eq!(
            scheduler.decide(1_000 + RECONCILE_SECONDS, false),
            ScheduleDecision::Collect(CollectionReason::Reconcile)
        );
        scheduler
            .note_collection_completed(1_000 + RECONCILE_SECONDS)
            .unwrap();
        assert_eq!(
            scheduler.decide(1_000 + RECONCILE_SECONDS + 30, false),
            ScheduleDecision::Idle
        );
    }

    #[test]
    fn asset_changes_are_coalesced_and_debounced() {
        let mut scheduler = scheduler(true);
        scheduler.note_collection_completed(1_000).unwrap();
        scheduler.note_asset_change(2_000);
        scheduler.note_asset_change(2_020);
        assert_eq!(
            scheduler.decide(2_029, false),
            ScheduleDecision::WaitUntil(2_030)
        );
        assert_eq!(
            scheduler.decide(2_030, false),
            ScheduleDecision::Collect(CollectionReason::AssetChange)
        );
        scheduler.note_collection_completed(2_031).unwrap();
        assert_eq!(scheduler.decide(2_032, false), ScheduleDecision::Idle);
    }

    #[test]
    fn pending_outbox_wins_over_startup_and_never_changes_snapshot_identity() {
        let mut scheduler = scheduler(true);
        assert_eq!(
            scheduler.decide(2_000, true),
            ScheduleDecision::DeliverPending
        );
        scheduler
            .note_delivery_result(2_000, &DeliveryDisposition::Accepted)
            .unwrap();
        assert_eq!(
            scheduler.decide(2_001, false),
            ScheduleDecision::Collect(CollectionReason::Startup)
        );
        scheduler.note_collection_completed(2_002).unwrap();
    }

    #[test]
    fn retry_deadline_survives_restart_and_is_bounded() {
        let mut scheduler = scheduler(true);
        scheduler.note_transport_failure(2_000).unwrap();
        let deadline = scheduler.checkpoint.retry_not_before.unwrap();
        assert!((2_030..=2_150).contains(&deadline));
        let json = serde_json::to_string(scheduler.checkpoint()).unwrap();
        let restored: InventoryScheduleCheckpoint = serde_json::from_str(&json).unwrap();
        let restarted = InventoryScheduler::restore(device(), true, Some(restored)).unwrap();
        assert_eq!(
            restarted.decide(2_000, true),
            ScheduleDecision::WaitUntil(deadline)
        );
        assert_eq!(
            restarted.decide(deadline, true),
            ScheduleDecision::DeliverPending
        );
    }

    #[test]
    fn server_retry_after_is_respected_and_backoff_capped() {
        let mut scheduler = scheduler(true);
        scheduler
            .note_delivery_result(
                20,
                &DeliveryDisposition::Retry {
                    after: Duration::from_secs(300),
                },
            )
            .unwrap();
        assert!(scheduler.checkpoint.retry_not_before.unwrap() >= 320);
        for _ in 0..15 {
            scheduler.note_transport_failure(10_000).unwrap();
        }
        assert_eq!(scheduler.checkpoint.failures, MAX_FAILURE_COUNT);
        assert!(scheduler.checkpoint.retry_not_before.unwrap() <= 10_000 + MAX_RETRY_SECONDS);
        scheduler
            .note_delivery_result(11_000, &DeliveryDisposition::Accepted)
            .unwrap();
        assert_eq!(scheduler.checkpoint.failures, 0);
        assert_eq!(scheduler.checkpoint.retry_not_before, None);
    }

    #[test]
    fn authentication_and_stale_receipt_halt_through_restarts() {
        for disposition in [
            DeliveryDisposition::PauseIdentity,
            DeliveryDisposition::Quarantine,
        ] {
            let mut scheduler = scheduler(true);
            scheduler.note_delivery_result(100, &disposition).unwrap();
            let saved = scheduler.checkpoint.clone();
            let restarted = InventoryScheduler::restore(device(), true, Some(saved)).unwrap();
            assert!(matches!(
                restarted.decide(10_000, true),
                ScheduleDecision::Halted(_)
            ));
            assert_eq!(
                scheduler.note_delivery_result(10_000, &DeliveryDisposition::Accepted),
                Err(ScheduleError::Halted)
            );
            assert_eq!(
                scheduler.note_collection_completed(10_000),
                Err(ScheduleError::Halted)
            );
        }
    }

    #[test]
    fn cross_device_malformed_or_extra_checkpoint_fields_fail_closed() {
        let saved = scheduler(true).checkpoint.clone();
        assert_eq!(
            InventoryScheduler::restore(Uuid::now_v7(), true, Some(saved.clone())).err(),
            Some(ScheduleError::InvalidCheckpoint)
        );
        let mut invalid = saved.clone();
        invalid.failures = 9;
        assert!(InventoryScheduler::restore(device(), true, Some(invalid)).is_err());
        let mut invalid = saved.clone();
        invalid.retry_not_before = Some(100);
        assert!(InventoryScheduler::restore(device(), true, Some(invalid)).is_err());
        let mut value = serde_json::to_value(saved).unwrap();
        value["unauthorized"] = serde_json::json!(true);
        assert!(serde_json::from_value::<InventoryScheduleCheckpoint>(value).is_err());
    }
}
