//! Windows SCM lifecycle bridge via the maintained windows-service crate.
//! No installer, OS privileges, collection or network access is implemented.
#![cfg(windows)]

use crate::config::AgentConfig;
use crate::runtime::run_until_shutdown;
use std::ffi::OsString;
use std::path::PathBuf;
use std::sync::{OnceLock, mpsc};
use std::time::Duration;
use windows_service::define_windows_service;
use windows_service::service::{
    ServiceControl, ServiceControlAccept, ServiceExitCode, ServiceState, ServiceStatus, ServiceType,
};
use windows_service::service_control_handler::{self, ServiceControlHandlerResult};
use windows_service::{Result, service_dispatcher};

const SERVICE_NAME: &str = "LanveriqAgent";
static CONFIG_PATH: OnceLock<PathBuf> = OnceLock::new();

define_windows_service!(ffi_service_main, service_main);

pub fn dispatch(path: PathBuf) -> Result<()> {
    CONFIG_PATH.get_or_init(|| path);
    service_dispatcher::start(SERVICE_NAME, ffi_service_main)
}

fn service_main(_arguments: Vec<OsString>) {
    // SCM receives state changes via ServiceStatus. No console, no private
    // credentials in logs and no panic-based service control handler.
    if let Err(error) = run_service() {
        tracing::error!(error = %error, "Windows service failed");
    }
}

fn status(state: ServiceState, error: u32) -> ServiceStatus {
    let pending = matches!(
        state,
        ServiceState::StartPending | ServiceState::StopPending
    );
    ServiceStatus {
        service_type: ServiceType::OWN_PROCESS,
        current_state: state,
        controls_accepted: if state == ServiceState::Running {
            ServiceControlAccept::STOP
        } else {
            ServiceControlAccept::empty()
        },
        exit_code: if error == 0 {
            ServiceExitCode::Win32(0)
        } else {
            ServiceExitCode::ServiceSpecific(error)
        },
        checkpoint: if pending { 1 } else { 0 },
        wait_hint: if pending {
            Duration::from_secs(10)
        } else {
            Duration::ZERO
        },
        process_id: None,
    }
}

fn run_service() -> Result<()> {
    let (shutdown_tx, shutdown_rx) = mpsc::channel();
    let handler = move |control| match control {
        ServiceControl::Stop => {
            let _ = shutdown_tx.send(());
            ServiceControlHandlerResult::NoError
        }
        ServiceControl::Interrogate => ServiceControlHandlerResult::NoError,
        _ => ServiceControlHandlerResult::NotImplemented,
    };
    let handle = service_control_handler::register(SERVICE_NAME, handler)?;
    handle.set_service_status(status(ServiceState::StartPending, 0))?;

    let config = CONFIG_PATH
        .get()
        .and_then(|path| AgentConfig::load(path).ok());
    let Some(config) = config else {
        handle.set_service_status(status(ServiceState::Stopped, 1))?;
        return Ok(());
    };

    let runtime = match tokio::runtime::Builder::new_current_thread()
        .enable_time()
        .build()
    {
        Ok(runtime) => runtime,
        Err(_) => {
            handle.set_service_status(status(ServiceState::Stopped, 2))?;
            return Ok(());
        }
    };

    handle.set_service_status(status(ServiceState::Running, 0))?;
    runtime.block_on(run_until_shutdown(&config, async move {
        // The blocking SCM receiver stays off the async scheduler.
        let _ = tokio::task::spawn_blocking(move || shutdown_rx.recv()).await;
    }));
    handle.set_service_status(status(ServiceState::Stopped, 0))?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn scm_running_status_accepts_stop_only() {
        let running = status(ServiceState::Running, 0);
        assert_eq!(running.current_state, ServiceState::Running);
        assert_eq!(running.controls_accepted, ServiceControlAccept::STOP);
        assert_eq!(running.checkpoint, 0);
    }

    #[test]
    fn unsuccessful_config_start_never_claims_running() {
        let failed = status(ServiceState::Stopped, 1);
        assert_eq!(failed.current_state, ServiceState::Stopped);
        assert_eq!(failed.controls_accepted, ServiceControlAccept::empty());
        assert_eq!(failed.exit_code, ServiceExitCode::ServiceSpecific(1));
    }
}
