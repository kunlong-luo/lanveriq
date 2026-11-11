//! Disposable Linux-only POP!_OS/systemd key-custody CI probe.
//! The production Agent never invokes this example. Only a specially
//! prepared ephemeral private parent is accepted; no real device identifiers,
//! registration, network, telemetry or enrollment tokens are involved.
#[cfg(target_os = "linux")]
fn main() -> Result<(), Box<dyn std::error::Error>> {
    use std::path::PathBuf;

    if std::env::var("LANVERIQ_SYNTHETIC_CSR_ONLY").as_deref() != Ok("yes") {
        return Err("synthetic offline CSR gate must be explicitly enabled".into());
    }
    let parent = PathBuf::from(std::env::var_os("LANVERIQ_SYNTHETIC_CSR_PARENT")
        .ok_or("synthetic private parent not provided")?);
    let bundle = std::env::args_os().nth(1).ok_or("one bundle path is required")?;
    if std::env::args_os().count() != 2 {
        return Err("exactly one synthetic bundle is required".into());
    }
    let bundle = PathBuf::from(bundle);
    if !parent.is_absolute() || bundle.parent() != Some(parent.as_path()) {
        return Err("bundle must be a direct child of the explicit test parent".into());
    }
    if !parent
        .file_name()
        .and_then(|s| s.to_str())
        .is_some_and(|s| s.starts_with("lanveriq-linux-csr-ci."))
    {
        return Err("only disposable CI directory names are allowed".into());
    }

    let csr = lanveriq_agent::enrollment_linux::create_offline_p256_csr(&bundle)?;
    if !csr.public_csr_pem.contains("-----BEGIN CERTIFICATE REQUEST-----") {
        return Err("offline CSR did not contain a public PKCS#10 request".into());
    }
    // Do not print the private key path, bytes, enrollment token or CSR.
    println!("PASS: explicit offline synthetic P-256 CSR generated with owner-private file custody");
    Ok(())
}

#[cfg(not(target_os = "linux"))]
fn main() {
    panic!("Linux-only synthetic CSR sample");
}
