//! AGT-01 idle lifecycle and AGT-02 isolated synthetic SQLite state.
//! Explicit Linux-only offline CSR file preparation exists, but no actual
//! enrollment/activation runtime, live collection, network or recording.
pub mod config;
#[cfg(target_os = "linux")]
pub mod enrollment_linux;
pub mod first_pin_review;
pub mod heartbeat_credential_pin;
pub mod heartbeat_reconciliation;
pub mod heartbeat_runtime;
pub mod heartbeat_schedule;
pub mod inventory;
pub mod inventory_platform;
pub mod inventory_schedule;
pub mod runtime;
pub mod store;
pub mod transport;

#[cfg(windows)]
pub mod windows_service;
