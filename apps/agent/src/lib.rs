//! AGT-01 service lifecycle only. No enrollment, telemetry, browsing,
//! network connection, private-key handling, persistence or screen capture.
pub mod config;
pub mod runtime;

#[cfg(windows)]
pub mod windows_service;
