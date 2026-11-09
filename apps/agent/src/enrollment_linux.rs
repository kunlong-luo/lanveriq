//! Linux-only, explicit and offline P-256 enrollment preparation.
//!
//! Uses the installed, supported OpenSSL CLI rather than implementing
//! ECDSA, PKCS#8, or PKCS#10 ASN.1 internally. No service/scheduler calls
//! this function; no endpoint, token, network or employee data is accessed.
//! A dedicated service-owned parent directory and OpenSSL 3 are prerequisites.
//! This is mode-0600 file custody, NOT a hardware-backed/non-exportable key.
use std::fs::{self, DirBuilder, OpenOptions};
use std::os::unix::fs::{DirBuilderExt, MetadataExt, OpenOptionsExt, PermissionsExt};
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};
use thiserror::Error;

const OPENSSL: &str = "/usr/bin/openssl";
const MAX_CSR_BYTES: u64 = 16 * 1024;
const MAX_KEY_BYTES: u64 = 16 * 1024;
const KEY_NAME: &str = "device-p256-private.pem";
const CSR_NAME: &str = "device-p256-request.pem";

/// A freshly created offline bundle; the key itself never enters Rust memory.
pub struct PendingCsr {
    pub public_csr_pem: String,
    pub private_key_path: PathBuf,
}

impl std::fmt::Debug for PendingCsr {
    fn fmt(&self, formatter: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        formatter.write_str("PendingCsr[REDACTED]")
    }
}

#[derive(Debug, Error)]
pub enum CsrProvisionError {
    #[error("absolute, private, non-symlink parent and new simple bundle name required")]
    UnsafeDirectory,
    #[error("offline key bundle could not be created")]
    Io,
    #[error("trusted system OpenSSL 3 could not generate or verify P-256 CSR")]
    Crypto,
    #[error("invalid or oversized public CSR/private key material")]
    InvalidMaterial,
}

/// One explicit call on Linux, never during service boot.
///
/// The caller must provide a NEW path beneath an existing *owner-private*,
/// locally managed directory. We refuse an existing bundle rather than
/// overwriting an installed key, and on failure remove only the newly
/// created bundle. Never provide the private-key path to users via CLI,
/// logs or a web API; the returned path is for the future reviewed key store.
pub fn create_offline_p256_csr(bundle_dir: &Path) -> Result<PendingCsr, CsrProvisionError> {
    let parent = bundle_dir
        .parent()
        .ok_or(CsrProvisionError::UnsafeDirectory)?;
    let name = bundle_dir
        .file_name()
        .and_then(|v| v.to_str())
        .ok_or(CsrProvisionError::UnsafeDirectory)?;
    if !bundle_dir.is_absolute()
        || name.len() > 64
        || name.is_empty()
        || name == "."
        || name == ".."
        || !name
            .bytes()
            .all(|c| c.is_ascii_alphanumeric() || matches!(c, b'.' | b'_' | b'-'))
        || !name.as_bytes()[0].is_ascii_alphanumeric()
    {
        return Err(CsrProvisionError::UnsafeDirectory);
    }

    let metadata = fs::symlink_metadata(parent).map_err(|_| CsrProvisionError::UnsafeDirectory)?;
    if !metadata.file_type().is_dir()
        || metadata.file_type().is_symlink()
        || metadata.mode() & 0o077 != 0
        || metadata.mode() & 0o700 != 0o700
    {
        return Err(CsrProvisionError::UnsafeDirectory);
    }
    // Explicit creation excludes an existing bundle and any symlink at it.
    // A mode-0700 directory is private at creation, not chmod'ed afterward.
    DirBuilder::new()
        .mode(0o700)
        .create(bundle_dir)
        .map_err(|_| CsrProvisionError::UnsafeDirectory)?;

    let output = create_inner(bundle_dir);
    if output.is_err() {
        // No other process should access this freshly created private bundle.
        // Never delete the parent or a path that pre-existed this call.
        let _ = fs::remove_dir_all(bundle_dir);
    }
    output
}

fn create_inner(dir: &Path) -> Result<PendingCsr, CsrProvisionError> {
    let key = dir.join(KEY_NAME);
    let csr = dir.join(CSR_NAME);
    reserve_owner_only_file(&key)?;

    // No passwords, private key bytes, token or notice version in argv/env.
    // Openssl writes the pre-created mode-0600 private key in our mode-0700
    // directory. CLI stderr and stdout are deliberately not surfaced.
    run(&[
        "genpkey",
        "-algorithm",
        "EC",
        "-pkeyopt",
        "ec_paramgen_curve:P-256",
        "-pkeyopt",
        "ec_param_enc:named_curve",
        "-out",
        key.to_str().ok_or(CsrProvisionError::UnsafeDirectory)?,
    ])?;

    reserve_owner_only_file(&csr)?;
    run(&[
        "req",
        "-new",
        "-sha256",
        "-key",
        key.to_str().ok_or(CsrProvisionError::UnsafeDirectory)?,
        "-subj",
        "/CN=Lanveriq Pending Device",
        "-out",
        csr.to_str().ok_or(CsrProvisionError::UnsafeDirectory)?,
    ])?;
    run(&[
        "req",
        "-in",
        csr.to_str().ok_or(CsrProvisionError::UnsafeDirectory)?,
        "-verify",
        "-noout",
    ])?;

    for (path, max) in [(&key, MAX_KEY_BYTES), (&csr, MAX_CSR_BYTES)] {
        let m = fs::symlink_metadata(path).map_err(|_| CsrProvisionError::InvalidMaterial)?;
        if !m.file_type().is_file()
            || m.file_type().is_symlink()
            || m.mode() & 0o077 != 0
            || m.len() == 0
            || m.len() > max
        {
            return Err(CsrProvisionError::InvalidMaterial);
        }
    }
    let public_csr_pem =
        fs::read_to_string(&csr).map_err(|_| CsrProvisionError::InvalidMaterial)?;
    if !public_csr_pem.starts_with("-----BEGIN CERTIFICATE REQUEST-----")
        || !public_csr_pem
            .trim_end()
            .ends_with("-----END CERTIFICATE REQUEST-----")
    {
        return Err(CsrProvisionError::InvalidMaterial);
    }
    Ok(PendingCsr {
        public_csr_pem,
        private_key_path: key,
    })
}

fn reserve_owner_only_file(path: &Path) -> Result<(), CsrProvisionError> {
    OpenOptions::new()
        .write(true)
        .create_new(true)
        .mode(0o600)
        .open(path)
        .map_err(|_| CsrProvisionError::Io)?;
    Ok(())
}

fn run(args: &[&str]) -> Result<(), CsrProvisionError> {
    let status = Command::new(OPENSSL)
        .args(args)
        .env_clear()
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .status()
        .map_err(|_| CsrProvisionError::Crypto)?;
    if !status.success() {
        return Err(CsrProvisionError::Crypto);
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn new_private_linux_bundle_has_signed_p256_csr_and_owner_only_key() {
        let root = tempfile::tempdir().unwrap();
        let bundle = root.path().join("pending-device-1");
        let result = create_offline_p256_csr(&bundle).unwrap();
        assert!(
            result
                .public_csr_pem
                .contains("-----BEGIN CERTIFICATE REQUEST-----")
        );
        assert_eq!(
            result.private_key_path.file_name().unwrap(),
            std::ffi::OsStr::new(KEY_NAME)
        );
        assert_eq!(
            fs::metadata(&bundle).unwrap().permissions().mode() & 0o777,
            0o700
        );
        assert_eq!(
            fs::metadata(&result.private_key_path)
                .unwrap()
                .permissions()
                .mode()
                & 0o777,
            0o600
        );
        assert!(!format!("{result:?}").contains("PRIVATE KEY"));
        assert!(!result.public_csr_pem.contains("PRIVATE KEY"));
        assert!(bundle.join(CSR_NAME).exists());

        let details = Command::new(OPENSSL)
            .args(["req", "-in"])
            .arg(bundle.join(CSR_NAME))
            .args(["-text", "-noout"])
            .output()
            .unwrap();
        assert!(details.status.success());
        let text = String::from_utf8(details.stdout).unwrap();
        assert!(text.contains("prime256v1"), "{text}");
        assert!(text.contains("ecdsa-with-SHA256"), "{text}");
        assert!(!text.contains("CA:TRUE"));
    }

    #[test]
    fn unique_attempts_use_different_keys_and_never_overwrite_existing_key() {
        let root = tempfile::tempdir().unwrap();
        let first = create_offline_p256_csr(&root.path().join("first")).unwrap();
        let second = create_offline_p256_csr(&root.path().join("second")).unwrap();
        assert_ne!(first.public_csr_pem, second.public_csr_pem);
        let first_key = fs::read(&first.private_key_path).unwrap();
        let second_key = fs::read(&second.private_key_path).unwrap();
        assert_ne!(first_key, second_key);
        assert!(matches!(
            create_offline_p256_csr(&root.path().join("first")),
            Err(CsrProvisionError::UnsafeDirectory)
        ));
        assert_eq!(fs::read(first.private_key_path).unwrap(), first_key);
    }

    #[test]
    fn refuses_public_parents_symlinks_relative_and_unsafe_names() {
        let root = tempfile::tempdir().unwrap();
        let open = root.path().join("public");
        fs::create_dir(&open).unwrap();
        fs::set_permissions(&open, fs::Permissions::from_mode(0o755)).unwrap();
        assert!(matches!(
            create_offline_p256_csr(&open.join("new")),
            Err(CsrProvisionError::UnsafeDirectory)
        ));
        let target = root.path().join("target");
        fs::create_dir(&target).unwrap();
        let alias = root.path().join("alias");
        std::os::unix::fs::symlink(&target, &alias).unwrap();
        assert!(matches!(
            create_offline_p256_csr(&alias.join("new")),
            Err(CsrProvisionError::UnsafeDirectory)
        ));
        for input in [
            Path::new("relative"),
            Path::new("../escape"),
            Path::new("/tmp/../unsafe"),
            Path::new("/tmp/has space"),
        ] {
            assert!(create_offline_p256_csr(input).is_err());
        }
        assert!(!open.join("new").exists());
    }
}
