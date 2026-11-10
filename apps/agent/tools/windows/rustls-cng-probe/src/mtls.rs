//! Batch A2: disposable **in-memory** TLS 1.3 mTLS using distinct non-
//! exportable Windows Software KSP client and server signing keys.
//! No listening sockets, installed services, production CA or endpoint data.
use rustls::client::ResolvesClientCert;
use rustls::pki_types::{CertificateDer, ServerName};
use rustls::server::{ClientHello, ResolvesServerCert, WebPkiClientVerifier};
use rustls::sign::{CertifiedKey, SigningKey};
use rustls::{
    ClientConfig, ClientConnection, ProtocolVersion, RootCertStore, ServerConfig, ServerConnection,
    SignatureScheme,
};
use rustls_cng::key::NCryptKey;
use rustls_cng::signer::CngSigningKey;
use std::error::Error;
use std::fs;
use std::io::{Cursor, Read, Write};
use std::path::Path;
use std::sync::Arc;
use windows_sys::Win32::Security::Cryptography::{
    NCryptFreeObject, NCryptOpenKey, NCryptOpenStorageProvider, NCRYPT_KEY_HANDLE,
    NCRYPT_PROV_HANDLE,
};

const PROVIDER: &str = "Microsoft Software Key Storage Provider";

fn utf16(s: &str) -> Vec<u16> {
    s.encode_utf16().chain(std::iter::once(0)).collect()
}

fn open_signer(name: &str) -> Result<Arc<dyn SigningKey>, Box<dyn Error>> {
    if name.len() > 100
        || !name.starts_with("lanveriq-synthetic-csr-")
        || !name
            .bytes()
            .all(|c| c.is_ascii_alphanumeric() || c == b'-')
    {
        return Err("key name is outside the disposable test namespace".into());
    }
    let mut provider: NCRYPT_PROV_HANDLE = 0;
    let mut key: NCRYPT_KEY_HANDLE = 0;
    // SAFETY: Named Windows provider/key are UTF-16 NUL-terminated; successful
    // NCryptOpenKey transfers key-handle ownership to rustls-cng below.
    let status = unsafe { NCryptOpenStorageProvider(&mut provider, utf16(PROVIDER).as_ptr(), 0) };
    if status != 0 {
        return Err("cannot open native synthetic CNG provider".into());
    }
    let status = unsafe { NCryptOpenKey(provider, &mut key, utf16(name).as_ptr(), 0, 0) };
    unsafe {
        NCryptFreeObject(provider);
    }
    if status != 0 {
        return Err("cannot reopen non-exportable CNG test key".into());
    }
    let signer = CngSigningKey::new(NCryptKey::new_owned(key))
        .map_err(|_| "CNG key could not become a rustls signer")?;
    if signer.bits() != 256
        || signer
            .choose_scheme(&[SignatureScheme::ECDSA_NISTP256_SHA256])
            .is_none()
    {
        return Err("CNG key is not compatible with TLS1.3 P-256".into());
    }
    Ok(Arc::new(signer))
}

#[derive(Debug)]
struct OneClient(Arc<CertifiedKey>);

impl ResolvesClientCert for OneClient {
    fn resolve(
        &self,
        _issuers: &[&[u8]],
        schemes: &[SignatureScheme],
    ) -> Option<Arc<CertifiedKey>> {
        if schemes.contains(&SignatureScheme::ECDSA_NISTP256_SHA256) {
            Some(self.0.clone())
        } else {
            None
        }
    }

    fn has_certs(&self) -> bool {
        true
    }
}

#[derive(Debug)]
struct OneServer(Arc<CertifiedKey>);

impl ResolvesServerCert for OneServer {
    fn resolve(&self, _hello: ClientHello<'_>) -> Option<Arc<CertifiedKey>> {
        Some(self.0.clone())
    }
}

fn roots(der: &[u8]) -> Result<RootCertStore, Box<dyn Error>> {
    let mut store = RootCertStore::empty();
    store.add(CertificateDer::from(der.to_vec()))?;
    Ok(store)
}

// True rustls wire-encrypted TLS messages, exchanged through in-memory buffers
// rather than a socket. This tests actual ClientHello/CertificateRequest,
// CertificateVerify, Finished and SNI PKIX validation without network traffic.
fn handshake(
    client_config: Arc<ClientConfig>,
    server_config: Arc<ServerConfig>,
    hostname: &'static str,
) -> Result<(ClientConnection, ServerConnection), Box<dyn Error>> {
    let mut client = ClientConnection::new(client_config, ServerName::try_from(hostname)?)?;
    let mut server = ServerConnection::new(server_config)?;
    for _ in 0..64 {
        let mut progressed = false;
        if client.wants_write() {
            let mut bytes = Vec::new();
            client.write_tls(&mut bytes)?;
            if !bytes.is_empty() {
                server.read_tls(&mut Cursor::new(bytes))?;
                server.process_new_packets()?;
                progressed = true;
            }
        }
        if server.wants_write() {
            let mut bytes = Vec::new();
            server.write_tls(&mut bytes)?;
            if !bytes.is_empty() {
                client.read_tls(&mut Cursor::new(bytes))?;
                client.process_new_packets()?;
                progressed = true;
            }
        }
        if !client.is_handshaking() && !server.is_handshaking() {
            return Ok((client, server));
        }
        if !progressed {
            return Err("TLS handshake stalled without a verified identity".into());
        }
    }
    Err("TLS handshake exceeded bounded memory-flight count".into())
}

pub fn run() -> Result<(), Box<dyn Error>> {
    let dir = std::env::var("LANVERIQ_SYNTHETIC_CNG_CERT_DIR")?;
    let dir = Path::new(&dir);
    if !dir.is_absolute() {
        return Err("synthetic PKI fixture directory is not absolute".into());
    }
    let root = fs::read(dir.join("root.der"))?;
    let wrong_root = fs::read(dir.join("wrong-root.der"))?;
    let server_leaf = fs::read(dir.join("server.der"))?;
    let client_leaf = fs::read(dir.join("client.der"))?;
    for der in [&root, &wrong_root, &server_leaf, &client_leaf] {
        if der.is_empty() || der.len() > 16_384 {
            return Err("invalid bounded test certificate DER".into());
        }
    }
    let client_key = std::env::var("LANVERIQ_SYNTHETIC_CNG_KEY_NAME")?;
    let server_key = std::env::var("LANVERIQ_SYNTHETIC_CNG_SERVER_KEY_NAME")?;
    if client_key == server_key {
        return Err("TLS client and server must never share a synthetic key".into());
    }

    let client_cert = Arc::new(CertifiedKey::new(
        vec![CertificateDer::from(client_leaf.clone())],
        open_signer(&client_key)?,
    ));
    let server_cert = Arc::new(CertifiedKey::new(
        vec![CertificateDer::from(server_leaf.clone())],
        open_signer(&server_key)?,
    ));

    // Validate a single disposable issuer with rustls/webpki, not a trust-all
    // verifier. Both directions advertise ONLY TLS 1.3 and ECDSA-P256.
    let client = ClientConfig::builder_with_provider(Arc::new(
        rustls::crypto::ring::default_provider(),
    ))
    .with_protocol_versions(&[&rustls::version::TLS13])?
    .with_root_certificates(roots(&root)?)
    .with_client_cert_resolver(Arc::new(OneClient(client_cert)));

    let verifier = WebPkiClientVerifier::builder_with_provider(
        Arc::new(roots(&root)?),
        Arc::new(rustls::crypto::ring::default_provider()),
    )
    .build()?;
    let server = ServerConfig::builder_with_provider(Arc::new(
        rustls::crypto::ring::default_provider(),
    ))
    .with_protocol_versions(&[&rustls::version::TLS13])?
    .with_client_cert_verifier(verifier)
    .with_cert_resolver(Arc::new(OneServer(server_cert)));

    // Confirm Reqwest 0.13 accepts precisely the preconfigured Rustls 0.23
    // type. This is a BUILD-ONLY, never-sent HTTP client; TLS verification and
    // single-origin restrictions cannot be replaced with default root trust.
    let _reqwest = reqwest::Client::builder()
        .tls_backend_preconfigured(client.clone())
        .https_only(true)
        .redirect(reqwest::redirect::Policy::none())
        .no_proxy()
        .build()?;

    let (mut connection, mut peer) =
        handshake(Arc::new(client.clone()), Arc::new(server.clone()), "localhost")?;
    if connection.protocol_version() != Some(ProtocolVersion::TLSv1_3)
        || peer.protocol_version() != Some(ProtocolVersion::TLSv1_3)
    {
        return Err("negotiated protocol is not TLS 1.3".into());
    }
    let certs = peer.peer_certificates().ok_or("server did not authenticate a client")?;
    if certs.len() != 1 || certs[0].as_ref() != client_leaf {
        return Err("TLS server did not verify the exact approved synthetic client leaf".into());
    }
    connection.writer().write_all(b"approved TLS13 session")?;
    let mut wire = Vec::new();
    connection.write_tls(&mut wire)?;
    peer.read_tls(&mut Cursor::new(wire))?;
    peer.process_new_packets()?;
    let mut payload = [0u8; 22];
    peer.reader().read_exact(&mut payload)?;
    if &payload != b"approved TLS13 session" {
        return Err("TLS protected application payload failed integrity check".into());
    }

    // None of these may complete mTLS. No opt-out or accepting anonymous peers.
    if handshake(Arc::new(client.clone()), Arc::new(server.clone()), "wrong.example").is_ok() {
        return Err("wrong SNI/hostname completed TLS".into());
    }
    let untrusted = ClientConfig::builder_with_provider(Arc::new(
        rustls::crypto::ring::default_provider(),
    ))
    .with_protocol_versions(&[&rustls::version::TLS13])?
    .with_root_certificates(roots(&wrong_root)?)
    .with_client_cert_resolver(client.client_auth_cert_resolver.clone());
    if handshake(Arc::new(untrusted), Arc::new(server.clone()), "localhost").is_ok() {
        return Err("untrusted CA completed TLS".into());
    }
    let anonymous = ClientConfig::builder_with_provider(Arc::new(
        rustls::crypto::ring::default_provider(),
    ))
    .with_protocol_versions(&[&rustls::version::TLS13])?
    .with_root_certificates(roots(&root)?)
    .with_no_client_auth();
    if handshake(Arc::new(anonymous), Arc::new(server.clone()), "localhost").is_ok() {
        return Err("missing client certificate completed TLS".into());
    }
    let bad_verifier = WebPkiClientVerifier::builder_with_provider(
        Arc::new(roots(&wrong_root)?),
        Arc::new(rustls::crypto::ring::default_provider()),
    )
    .build()?;
    let deny_client = ServerConfig::builder_with_provider(Arc::new(
        rustls::crypto::ring::default_provider(),
    ))
    .with_protocol_versions(&[&rustls::version::TLS13])?
    .with_client_cert_verifier(bad_verifier)
    .with_cert_resolver(server.cert_resolver.clone());
    if handshake(Arc::new(client), Arc::new(deny_client), "localhost").is_ok() {
        return Err("server accepted a client signed by the wrong issuer".into());
    }

    println!(
        "PASS: in-memory real TLS1.3 mutual handshake with separate non-exportable CNG client/server signing keys, exact leaf and protected payload; wrong SNI, wrong CA, absent client cert and wrong client issuer all rejected; Reqwest preconfigured Rustls builder accepted."
    );
    Ok(())
}


/// One bounded, single-use loopback HTTPS server. It NEVER listens outside
/// 127.0.0.1, and accepts exactly one TCP connection. The only response is a
/// synthetic static 4-byte body; there is no Agent request or business state.
fn serve_loopback_once(
    listener: std::net::TcpListener,
    config: ServerConfig,
    expected_leaf: Vec<u8>,
) -> Result<bool, String> {
    use rustls::StreamOwned;
    use std::time::Duration;

    let (socket, peer) = listener.accept().map_err(|_| "loopback accept failed")?;
    if !peer.ip().is_loopback() {
        return Err("unexpected non-loopback peer".into());
    }
    socket
        .set_read_timeout(Some(Duration::from_secs(9)))
        .map_err(|_| "cannot limit loopback TLS read duration")?;
    socket
        .set_write_timeout(Some(Duration::from_secs(9)))
        .map_err(|_| "cannot limit loopback TLS write duration")?;
    let connection =
        ServerConnection::new(Arc::new(config)).map_err(|_| "TLS server config failed")?;
    let mut tls = StreamOwned::new(connection, socket);
    let mut header = Vec::new();
    let mut chunk = [0_u8; 2048];
    while !header.windows(4).any(|w| w == b"\r\n\r\n") {
        let read = match tls.read(&mut chunk) {
            Ok(0) | Err(_) => return Ok(false), // expected TLS negative
            Ok(n) => n,
        };
        if header.len() + read > 8192 {
            return Err("synthetic HTTP request exceeds 8KiB".into());
        }
        header.extend_from_slice(&chunk[..read]);
    }
    if tls.conn.protocol_version() != Some(ProtocolVersion::TLSv1_3) {
        return Err("loopback server negotiated a non-TLS1.3 protocol".into());
    }
    let peer_certs = match tls.conn.peer_certificates() {
        Some(value) if value.len() == 1 && value[0].as_ref() == expected_leaf => value,
        _ => return Err("server did not verify exact synthetic CNG client certificate".into()),
    };
    if peer_certs.is_empty() {
        return Err("anonymous TLS peer not permitted".into());
    }
    let header_lower = String::from_utf8_lossy(&header).to_ascii_lowercase();
    if !header_lower.starts_with("get /synthetic-mtls http/1.1\r\n")
        || !header_lower.contains("\r\nhost: localhost:")
    {
        return Err("synthetic request route/host mismatch".into());
    }
    let response = b"HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 4\r\nCache-Control: no-store\r\nConnection: close\r\n\r\nPASS";
    tls.write_all(response).map_err(|_| "TLS response write failed")?;
    tls.conn.send_close_notify();
    tls.flush().map_err(|_| "TLS response flush failed")?;
    Ok(true)
}

/// Actual TCP and Reqwest 0.13.5 HTTPS, not an in-memory TLS handshake.
/// The ONLY reachable endpoint is a one-shot 127.0.0.1:0 listener and a
/// hard-coded synthetic GET path. Wrong CA, hostname and missing/wrong issuer
/// cert must fail BEFORE any accepted application request.
async fn request_loopback(
    client_config: ClientConfig,
    server_config: ServerConfig,
    host: &'static str,
    expected_leaf: Vec<u8>,
) -> Result<(bool, bool), Box<dyn Error>> {
    use std::net::{Ipv4Addr, SocketAddrV4, TcpListener};
    use std::time::Duration;

    if !matches!(host, "localhost" | "wrong.example") {
        return Err("only two approved synthetic hostnames may be resolved".into());
    }
    let listener = TcpListener::bind(SocketAddrV4::new(Ipv4Addr::LOCALHOST, 0))?;
    let bind = listener.local_addr()?;
    if !bind.ip().is_loopback() {
        return Err("loopback server escaped the local network namespace".into());
    }
    let worker = std::thread::spawn(move || {
        serve_loopback_once(listener, server_config, expected_leaf)
    });

    let client = reqwest::Client::builder()
        .tls_backend_preconfigured(client_config)
        .http1_only()
        .https_only(true)
        .no_proxy()
        .redirect(reqwest::redirect::Policy::none())
        .connect_timeout(Duration::from_secs(5))
        .timeout(Duration::from_secs(10))
        .resolve(host, bind)
        .build()?;
    let url = format!("https://{host}:{}/synthetic-mtls", bind.port());
    let result = client.get(url).send().await;
    let accepted = match result {
        Ok(response) if response.status() == reqwest::StatusCode::OK => {
            response.text().await.map(|s| s == "PASS").unwrap_or(false)
        }
        _ => false,
    };
    // The server's TLS certificate-verification result is independent of the
    // HTTP client's status; both must affirm the positive case.
    let server_ok = worker
        .join()
        .map_err(|_| "synthetic loopback server thread panicked")?
        .unwrap_or(false);
    Ok((accepted, server_ok))
}

pub fn run_loopback() -> Result<(), Box<dyn Error>> {
    let dir = std::env::var("LANVERIQ_SYNTHETIC_CNG_CERT_DIR")?;
    let dir = Path::new(&dir);
    if !dir.is_absolute() {
        return Err("synthetic PKI fixture directory is not absolute".into());
    }
    let root = fs::read(dir.join("root.der"))?;
    let wrong_root = fs::read(dir.join("wrong-root.der"))?;
    let server_leaf = fs::read(dir.join("server.der"))?;
    let client_leaf = fs::read(dir.join("client.der"))?;
    for item in [&root, &wrong_root, &server_leaf, &client_leaf] {
        if item.is_empty() || item.len() > 16_384 {
            return Err("synthetic public certificate is invalid or oversized".into());
        }
    }
    let client_key = std::env::var("LANVERIQ_SYNTHETIC_CNG_KEY_NAME")?;
    let server_key = std::env::var("LANVERIQ_SYNTHETIC_CNG_SERVER_KEY_NAME")?;
    if client_key == server_key {
        return Err("TLS client and server must have distinct CNG keys".into());
    }

    let certified_client = Arc::new(CertifiedKey::new(
        vec![CertificateDer::from(client_leaf.clone())],
        open_signer(&client_key)?,
    ));
    let certified_server = Arc::new(CertifiedKey::new(
        vec![CertificateDer::from(server_leaf)],
        open_signer(&server_key)?,
    ));
    let provider = Arc::new(rustls::crypto::ring::default_provider());
    let make_client = |trusted_root: &[u8], with_cert: bool| -> Result<ClientConfig, Box<dyn Error>> {
        let builder = ClientConfig::builder_with_provider(provider.clone())
            .with_protocol_versions(&[&rustls::version::TLS13])?
            .with_root_certificates(roots(trusted_root)?);
        Ok(if with_cert {
            builder.with_client_cert_resolver(Arc::new(OneClient(certified_client.clone())))
        } else {
            builder.with_no_client_auth()
        })
    };
    let make_server = |trusted_root: &[u8]| -> Result<ServerConfig, Box<dyn Error>> {
        let verifier = WebPkiClientVerifier::builder_with_provider(
            Arc::new(roots(trusted_root)?),
            provider.clone(),
        )
        .build()?;
        Ok(ServerConfig::builder_with_provider(provider.clone())
            .with_protocol_versions(&[&rustls::version::TLS13])?
            .with_client_cert_verifier(verifier)
            .with_cert_resolver(Arc::new(OneServer(certified_server.clone()))))
    };

    // No Tokio runtime is ever created by the real Agent for this probe.
    // The standalone tester runs sequentially in its own process.
    let runtime = tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()?;
    runtime.block_on(async {
        let (accepted, server_verified) = request_loopback(
            make_client(&root, true)?,
            make_server(&root)?,
            "localhost",
            client_leaf.clone(),
        )
        .await?;
        if !accepted || !server_verified {
            return Err("real Reqwest→127.0.0.1 TLS1.3/CNG mTLS request was not accepted".into());
        }
        for (label, cli, svr, host) in [
            ("wrong_hostname", make_client(&root, true)?, make_server(&root)?, "wrong.example"),
            ("wrong_server_root", make_client(&wrong_root, true)?, make_server(&root)?, "localhost"),
            ("missing_client_cert", make_client(&root, false)?, make_server(&root)?, "localhost"),
            ("wrong_client_issuer", make_client(&root, true)?, make_server(&wrong_root)?, "localhost"),
        ] {
            let (http_ok, server_ok) =
                request_loopback(cli, svr, host, client_leaf.clone()).await?;
            if http_ok || server_ok {
                return Err(format!("{label}: TLS handshake unexpectedly allowed application request").into());
            }
        }
        Ok::<(), Box<dyn Error>>(())
    })?;
    println!(
        "PASS: real Reqwest 0.13.5 HTTPS over 127.0.0.1 TCP, TLS1.3 and CNG-backed native client/server mTLS verified; wrong SNI, wrong server CA, missing client certificate and wrong client issuer all rejected before HTTP success."
    );
    Ok(())
}
