//! AGT-01 idle lifecycle and AGT-02 isolated synthetic SQLite state.
//! No real collection, network, enrollment, key custody or recording.
pub mod config;
pub mod inventory;
pub mod inventory_platform;
pub mod inventory_schedule;
pub mod runtime;
pub mod store;
pub mod transport;

#[cfg(windows)]
pub mod windows_service;
