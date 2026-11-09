//! Isolated native Windows rustls-cng SigningKey experiment.
//! Re-opens an existing synthetic current-user non-exportable CNG key by name
//! in a *separate process* and asks rustls' Signer trait to sign a known
//! synthetic challenge. No service/network, TLS handshake or private export.
#![cfg(windows)]

use rustls::sign::SigningKey;
use rustls::{SignatureAlgorithm, SignatureScheme};
use rustls_cng::key::NCryptKey;
use rustls_cng::signer::CngSigningKey;
use std::error::Error;
use windows_sys::Win32::Security::Cryptography::{
    NCryptFreeObject, NCryptOpenKey, NCryptOpenStorageProvider, NCRYPT_KEY_HANDLE,
    NCRYPT_PROV_HANDLE,
};

const PROVIDER: &str = "Microsoft Software Key Storage Provider";
const NAME_ENV: &str = "LANVERIQ_SYNTHETIC_CNG_KEY_NAME";
const MESSAGE: &[u8] = b"Lanveriq isolated TLS13 CNG SigningKey proof v1";

fn utf16(value: &str) -> Vec<u16> {
    value.encode_utf16().chain(std::iter::once(0)).collect()
}

fn main() -> Result<(), Box<dyn Error>> {
    let key_name = std::env::var(NAME_ENV).map_err(|_| "missing isolated test key name")?;
    if key_name.len() > 100
        || !key_name.starts_with("lanveriq-synthetic-csr-")
        || !key_name.bytes().all(|c| c.is_ascii_alphanumeric() || c == b'-')
    {
        return Err("unapproved CNG test key namespace".into());
    }

    let mut provider: NCRYPT_PROV_HANDLE = 0;
    let mut handle: NCRYPT_KEY_HANDLE = 0;
    // SAFETY: Windows NCrypt APIs consume valid UTF-16 NUL-terminated names.
    // On success NCryptKey::new_owned owns/frees the key handle; this function
    // frees the provider handle. Errors free any acquired handles.
    let status = unsafe { NCryptOpenStorageProvider(&mut provider, utf16(PROVIDER).as_ptr(), 0) };
    if status != 0 {
        return Err("cannot open approved Windows CNG software provider".into());
    }
    let status = unsafe { NCryptOpenKey(provider, &mut handle, utf16(&key_name).as_ptr(), 0, 0) };
    unsafe {
        NCryptFreeObject(provider);
    }
    if status != 0 {
        return Err("cannot reopen synthetic current-user CNG key".into());
    }

    let key = CngSigningKey::new(NCryptKey::new_owned(handle))
        .map_err(|_| "rustls-cng cannot wrap the native CNG key")?;
    if key.bits() != 256 || key.algorithm() != SignatureAlgorithm::ECDSA {
        return Err("rustls-cng key has wrong algorithm or size".into());
    }
    let signer = key
        .choose_scheme(&[SignatureScheme::ECDSA_NISTP256_SHA256])
        .ok_or("CNG did not offer TLS1.3 ECDSA P256 SHA256")?;
    if signer.scheme() != SignatureScheme::ECDSA_NISTP256_SHA256 {
        return Err("CNG signing scheme mismatch".into());
    }
    let signature = signer.sign(MESSAGE)?;
    if signature.len() < 64 || signature.len() > 80 {
        return Err("unexpected P-256 DER signature length".into());
    }
    // Only an ordinary *public* signature leaves this process. A separate
    // .NET ECDsaCng instance verifies it against the same native public key.
    let hex: String = signature.iter().map(|v| format!("{v:02x}")).collect();
    println!("LANVERIQ_SYNTHETIC_SIGNATURE_HEX={hex}");
    Ok(())
}
