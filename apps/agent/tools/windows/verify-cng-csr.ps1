# Windows synthetic-only CNG P-256 CSR acceptance.
# Requires pwsh 7/.NET 8+ and an ephemeral GitHub-hosted Windows user.
# Never run on a real/managed endpoint. No Agent service, enrollment token,
# mTLS transport, issuer CA or end-user identities are involved.
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string] $PublicCsrPath,
    [switch] $ProbeRustlsSigning,
    [switch] $ProbeKeyAcl
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not $IsWindows) {
    throw 'Windows CNG synthetic acceptance must run on Windows'
}
if ([System.Environment]::Version.Major -lt 8) {
    throw 'Requires a maintained .NET runtime (8+) for CSR verification'
}
if (-not [System.IO.Path]::IsPathFullyQualified($PublicCsrPath)) {
    throw 'Synthetic public CSR output must use an absolute path'
}
if ([System.IO.File]::Exists($PublicCsrPath)) {
    throw 'Refusing to overwrite a prior synthetic public CSR'
}

$keyName = 'lanveriq-synthetic-csr-' + [Guid]::NewGuid().ToString('N')
$provider = [System.Security.Cryptography.CngProvider]::MicrosoftSoftwareKeyStorageProvider
$policy = [System.Security.Cryptography.CngKeyCreationParameters]::new()
$policy.Provider = $provider
$policy.ExportPolicy = [System.Security.Cryptography.CngExportPolicies]::None
$policy.KeyUsage = [System.Security.Cryptography.CngKeyUsages]::Signing
$policy.KeyCreationOptions = [System.Security.Cryptography.CngKeyCreationOptions]::None

$key = $null
$ecdsa = $null
$verified = $null
$cleanupIssue = $null
try {
    # Exactly one new current-user KSP key in a disposable Windows runner.
    # Never request MachineKeySet/admin privileges or export private bytes.
    $key = [System.Security.Cryptography.CngKey]::Create(
        [System.Security.Cryptography.CngAlgorithm]::ECDsaP256,
        $keyName,
        $policy
    )
    if ($key.Provider.Provider -ne $provider.Provider) {
        throw 'Unexpected CNG key storage provider'
    }
    if ($key.Algorithm.Algorithm -ne 'ECDSA_P256') {
        throw 'Key is not CNG ECDSA P-256'
    }
    if ($key.ExportPolicy -ne [System.Security.Cryptography.CngExportPolicies]::None) {
        throw 'CNG key export policy is not non-exportable'
    }
    if ($key.KeyUsage -ne [System.Security.Cryptography.CngKeyUsages]::Signing) {
        throw 'CNG key usage must be restricted to signing'
    }

    if ($ProbeKeyAcl) {
        # Test the *actual CNG key object's* security descriptor rather than
        # assuming Windows file paths or ACLs. No DACL change or export.
        & (Join-Path $PSScriptRoot 'verify-cng-key-acl.ps1') -Key $key
    }

    $ecdsa = [System.Security.Cryptography.ECDsaCng]::new($key)
    $subject = [System.Security.Cryptography.X509Certificates.X500DistinguishedName]::new(
        'CN=Lanveriq Synthetic Untrusted Pending CSR'
    )
    $request = [System.Security.Cryptography.X509Certificates.CertificateRequest]::new(
        $subject,
        $ecdsa,
        [System.Security.Cryptography.HashAlgorithmName]::SHA256
    )
    [byte[]] $der = $request.CreateSigningRequest()
    if ($der.Length -lt 100 -or $der.Length -gt 16384) {
        throw 'CNG PKCS#10 CSR size outside the bounded enrollment profile'
    }

    # .NET loads the whole PKCS#10 and independently checks its signature
    # against the embedded *public* key; no ASN.1 or ECDSA implementation.
    $verified = [System.Security.Cryptography.X509Certificates.CertificateRequest]::LoadSigningRequest(
        $der,
        [System.Security.Cryptography.HashAlgorithmName]::SHA256
    )
    if ($verified.PublicKey.Oid.Value -ne '1.2.840.10045.2.1') {
        throw 'CSR does not contain an EC public key'
    }
    $reader = [System.Formats.Asn1.AsnReader]::new(
        $verified.PublicKey.EncodedParameters.RawData,
        [System.Formats.Asn1.AsnEncodingRules]::DER
    )
    if ($reader.ReadObjectIdentifier() -ne '1.2.840.10045.3.1.7') {
        throw 'CSR elliptic curve is not secp256r1'
    }
    $reader.ThrowIfNotEmpty()

    [byte[]] $tampered = [byte[]]$der.Clone()
    $tampered[$tampered.Length - 1] = $tampered[$tampered.Length - 1] -bxor 1
    $badSignatureRejected = $false
    try {
        $unused = [System.Security.Cryptography.X509Certificates.CertificateRequest]::LoadSigningRequest(
            $tampered,
            [System.Security.Cryptography.HashAlgorithmName]::SHA256
        )
        $unused = $null
    } catch [System.Security.Cryptography.CryptographicException] {
        $badSignatureRejected = $true
    }
    if (-not $badSignatureRejected) {
        throw 'Modified CSR signature was accepted'
    }

    if ($ProbeRustlsSigning) {
        # Compile and run the maintained rustls-cng native signing adapter in
        # ANOTHER process. This intentionally reopens the named non-exportable
        # user CNG key. No key bytes, PIN, tokens or key handles cross processes.
        $manifest = Join-Path $PSScriptRoot 'rustls-cng-probe/Cargo.toml'
        $message = [Text.Encoding]::UTF8.GetBytes(
            'Lanveriq isolated TLS13 CNG SigningKey proof v1'
        )
        $env:LANVERIQ_SYNTHETIC_CNG_KEY_NAME = $keyName
        try {
            $cargoOutput = @(& cargo +1.94.0 run --quiet --manifest-path $manifest 2>&1)
            if ($LASTEXITCODE -ne 0) {
                throw 'Isolated Rust rustls-cng signer failed'
            }
            $matches = @($cargoOutput | Where-Object {
                [string]$_ -like 'LANVERIQ_SYNTHETIC_SIGNATURE_HEX=*'
            })
            if ($matches.Count -ne 1) {
                throw 'Expected exactly one synthetic public Rustls signature'
            }
            $prefix = 'LANVERIQ_SYNTHETIC_SIGNATURE_HEX='
            [byte[]] $signature = [Convert]::FromHexString(
                ([string]$matches[0]).Substring($prefix.Length)
            )
        } finally {
            Remove-Item Env:LANVERIQ_SYNTHETIC_CNG_KEY_NAME -ErrorAction SilentlyContinue
        }

        $signatureFormat = [System.Security.Cryptography.DSASignatureFormat]::Rfc3279DerSequence
        if (-not $ecdsa.VerifyData(
            $message,
            $signature,
            [System.Security.Cryptography.HashAlgorithmName]::SHA256,
            $signatureFormat
        )) {
            throw 'Independent .NET verifier rejected rustls-cng native signature'
        }
        [byte[]] $wrongMessage = [byte[]]$message.Clone()
        $wrongMessage[0] = $wrongMessage[0] -bxor 1
        if ($ecdsa.VerifyData(
            $wrongMessage,
            $signature,
            [System.Security.Cryptography.HashAlgorithmName]::SHA256,
            $signatureFormat
        )) {
            throw 'Native Rustls CNG signature accepted a wrong message'
        }
        [byte[]] $wrongSignature = [byte[]]$signature.Clone()
        $wrongSignature[$wrongSignature.Length - 1] =
            $wrongSignature[$wrongSignature.Length - 1] -bxor 1
        if ($ecdsa.VerifyData(
            $message,
            $wrongSignature,
            [System.Security.Cryptography.HashAlgorithmName]::SHA256,
            $signatureFormat
        )) {
            throw 'Native Rustls CNG signature accepted a tampered signature'
        }
        Write-Output 'PASS: rustls-cng ECDSA_P256_SHA256 signed via separate-process non-exportable CNG handle; independent .NET positive and two negative checks passed.'
    }

    # Only the *public* CSR can leave this function. No private key blobs,
    # store handles, or key names are written into an artifact or workflow log.
    $pem = -join ([System.Security.Cryptography.PemEncoding]::Write('CERTIFICATE REQUEST', $der))
    if (-not $pem.StartsWith('-----BEGIN CERTIFICATE REQUEST-----') -or
        -not $pem.TrimEnd().EndsWith('-----END CERTIFICATE REQUEST-----')) {
        throw 'Unexpected PKCS#10 PEM framing'
    }
    [System.IO.File]::WriteAllText(
        $PublicCsrPath,
        $pem + [System.Environment]::NewLine,
        [System.Text.UTF8Encoding]::new($false)
    )
    Write-Output 'PASS: ephemeral Windows CNG P-256 SHA256 PKCS#10 CSR was signed and verified; export policy None; private key never exported.'
} finally {
    if ($null -ne $ecdsa) {
        $ecdsa.Dispose()
    }
    if ($null -ne $key) {
        try {
            # Prove we can erase this named test key rather than persisting
            # a long-lived user credential on the CI runner.
            $key.Delete()
            if ([System.Security.Cryptography.CngKey]::Exists($keyName, $provider)) {
                throw 'Deleted disposable CNG key still exists in the user KSP'
            }
        } catch {
            $cleanupIssue = $_
        } finally {
            $key.Dispose()
        }
    }
    if ($null -ne $cleanupIssue) {
        throw 'Could not delete disposable Windows CNG test key'
    }
}
