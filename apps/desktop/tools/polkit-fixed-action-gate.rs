//! L2B-24: standalone, NON-PRIVILEGED Rust process-side Polkit reviewer.
//! Not a Tauri IPC, not a D-Bus service or root-side installer.
//! All outcomes DENY production actions, including a hypothetical polkit yes.
//! Uses real Linux /proc identity and real pkcheck, never accepts caller PID/UID,
//! package name, version, URL, service name, command or extra arguments.
use std::env;
use std::fs;
use std::process::{Command, Stdio};

const ACTION_PREFIX: &str = "org.lanveriq.agent.package.";
const RESULT_DENIED: i32 = 21;
const RESULT_INVALID: i32 = 22;
const RESULT_UNVERIFIED: i32 = 23;
const RESULT_APPROVAL_MISSING: i32 = 24;

fn exact_action() -> Result<&'static str, &'static str> {
    let mut args = env::args_os();
    let _program = args.next();
    let value = args.next().ok_or("missing fixed operation")?;
    if args.next().is_some() {
        return Err("extra arguments forbidden");
    }
    match value.to_str() {
        Some("install") => Ok("install"),
        Some("upgrade") => Ok("upgrade"),
        Some("remove") => Ok("remove"),
        _ => Err("unreviewed operation or non-UTF8 argument"),
    }
}

fn real_kernel_subject() -> Result<String, &'static str> {
    #[cfg(not(target_os = "linux"))]
    return Err("Linux-only subject");

    #[cfg(target_os = "linux")]
    {
        let pid = std::process::id();
        let status = fs::read_to_string("/proc/self/status")
            .map_err(|_| "kernel Uid unavailable")?;
        let uid_line = status.lines()
            .find_map(|line| line.strip_prefix("Uid:"))
            .ok_or("kernel Uid missing")?;
        let mut fields = uid_line.split_ascii_whitespace();
        let uid: u32 = fields.next().ok_or("kernel Uid empty")?
            .parse().map_err(|_| "kernel Uid malformed")?;
        // This is explicitly an unprivileged request-side reviewer, never a
        // root helper; real privileged broker must use D-Bus SO_PEERCRED.
        if uid == 0 {
            return Err("root reviewer invocation forbidden");
        }
        let stat = fs::read_to_string("/proc/self/stat")
            .map_err(|_| "kernel process start unavailable")?;
        // Linux stat comm field is enclosed in parentheses, may contain
        // spaces and ')'. The last ') ' separates field 2 from field 3.
        let tail = stat.rsplit_once(") ").ok_or("kernel stat malformed")?.1;
        let start_time: u64 = tail.split_ascii_whitespace().nth(19)
            .ok_or("kernel start time missing")?
            .parse().map_err(|_| "kernel start time malformed")?;
        if start_time == 0 {
            return Err("kernel start time zero");
        }
        Ok(format!("{pid},{start_time},{uid}"))
    }
}

fn run() -> i32 {
    let op = match exact_action() {
        Ok(value) => value,
        Err(message) => {
            eprintln!("DENIED: {message}");
            return RESULT_INVALID;
        }
    };
    let subject = match real_kernel_subject() {
        Ok(value) => value,
        Err(message) => {
            eprintln!("NOT_APPROVED: {message}");
            return RESULT_UNVERIFIED;
        }
    };
    let action = format!("{ACTION_PREFIX}{op}");
    let status = Command::new("/usr/bin/pkcheck")
        .args(["--action-id", &action, "--process", &subject])
        .env_clear()
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .status();
    // 1 is an explicit polkitd denial; 127/126/2 are errors/nonresponse
    // and must not be reported as authoritative denial evidence.
    match status {
        Ok(code) if code.code() == Some(1) => {
            eprintln!("DENIED: native Polkit rejected fixed {op} action");
            RESULT_DENIED
        }
        Ok(code) if code.success() => {
            // No independent publisher approval or root-side broker exists.
            // Even unexpected "yes" NEVER invokes APT/dpkg/systemctl.
            eprintln!("NOT_APPROVED: native Polkit result alone cannot authorize release");
            RESULT_APPROVAL_MISSING
        }
        _ => {
            eprintln!("NOT_APPROVED: native Polkit subject check not verified");
            RESULT_UNVERIFIED
        }
    }
}

fn main() {
    std::process::exit(run());
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn all_authority_result_codes_are_nonzero_and_distinct() {
        let values = [
            RESULT_DENIED,
            RESULT_INVALID,
            RESULT_UNVERIFIED,
            RESULT_APPROVAL_MISSING,
        ];
        for (i, v) in values.iter().enumerate() {
            assert!(*v > 0);
            for previous in values.iter().take(i) {
                assert_ne!(previous, v);
            }
        }
    }

    #[test]
    fn action_ids_are_fixed_and_separate() {
        assert_eq!(ACTION_PREFIX, "org.lanveriq.agent.package.");
        assert_ne!(format!("{ACTION_PREFIX}install"), format!("{ACTION_PREFIX}remove"));
    }

    #[test]
    fn an_unprivileged_linux_process_has_bound_kernel_pid_start_and_uid() {
        if cfg!(target_os = "linux") && real_kernel_subject().is_ok() {
            let text = real_kernel_subject().expect("subject already checked");
            let fields: Vec<&str> = text.split(',').collect();
            assert_eq!(fields.len(), 3);
            assert_eq!(fields[0].parse::<u32>().ok(), Some(std::process::id()));
            assert!(fields[1].parse::<u64>().unwrap_or(0) > 0);
            assert!(fields[2].parse::<u32>().unwrap_or(0) > 0);
        }
    }
}
