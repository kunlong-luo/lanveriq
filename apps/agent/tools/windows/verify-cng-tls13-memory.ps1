# Disposable synthetic, in-memory rustls TLS 1.3 mTLS with Windows CNG.
# Executed ONLY on a GitHub-hosted Windows runner. No device/service/network.
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string] $FixtureDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (-not $IsWindows -or [Environment]::Version.Major -lt 8) {
    throw 'Isolated Windows PowerShell 7 / .NET 8+ required'
}
if (-not [IO.Path]::IsPathFullyQualified($FixtureDirectory) -or
    [IO.Directory]::Exists($FixtureDirectory)) {
    throw 'A new absolute synthetic-only output directory is required'
}
if (-not $env:RUNNER_TEMP) {
    throw 'This experiment is restricted to an ephemeral GitHub runner'
}
$runnerTemp = [IO.Path]::GetFullPath($env:RUNNER_TEMP)
$parent = [IO.Path]::GetFullPath([IO.Path]::GetDirectoryName($FixtureDirectory))
if ($parent.TrimEnd('\\') -ne $runnerTemp.TrimEnd('\\')) {
    throw 'Synthetic public cert fixtures must be under the GitHub runner temp'
}

$provider = [Security.Cryptography.CngProvider]::MicrosoftSoftwareKeyStorageProvider
$policy = [Security.Cryptography.CngKeyCreationParameters]::new()
$policy.Provider = $provider
$policy.ExportPolicy = [Security.Cryptography.CngExportPolicies]::None
$policy.KeyUsage = [Security.Cryptography.CngKeyUsages]::Signing
$policy.KeyCreationOptions = [Security.Cryptography.CngKeyCreationOptions]::None
$clientName = 'lanveriq-synthetic-csr-' + [guid]::NewGuid().ToString('N')
$serverName = 'lanveriq-synthetic-csr-' + [guid]::NewGuid().ToString('N')
$clientKey = $null
$serverKey = $null
$clientSigner = $null
$serverSigner = $null
$caSigner = $null
$root = $null
$cleanupError = $null

function New-Leaf {
    param(
        [Parameter(Mandatory = $true)] $SigningKey,
        [Parameter(Mandatory = $true)] $Issuer,
        [Parameter(Mandatory = $true)] [string] $Subject,
        [Parameter(Mandatory = $true)] [string] $EkuOid,
        [Parameter(Mandatory = $true)] [bool] $Server
    )
    $request = [Security.Cryptography.X509Certificates.CertificateRequest]::new(
        [Security.Cryptography.X509Certificates.X500DistinguishedName]::new($Subject),
        $SigningKey,
        [Security.Cryptography.HashAlgorithmName]::SHA256
    )
    $request.CertificateExtensions.Add(
        [Security.Cryptography.X509Certificates.X509BasicConstraintsExtension]::new(
            $false, $false, 0, $true
        )
    )
    $request.CertificateExtensions.Add(
        [Security.Cryptography.X509Certificates.X509KeyUsageExtension]::new(
            [Security.Cryptography.X509Certificates.X509KeyUsageFlags]::DigitalSignature, $true
        )
    )
    $oidSet = [Security.Cryptography.OidCollection]::new()
    [void]$oidSet.Add([Security.Cryptography.Oid]::new($EkuOid))
    $request.CertificateExtensions.Add(
        [Security.Cryptography.X509Certificates.X509EnhancedKeyUsageExtension]::new(
            $oidSet, $true
        )
    )
    if ($Server) {
        $san = [Security.Cryptography.X509Certificates.SubjectAlternativeNameBuilder]::new()
        $san.AddDnsName('localhost')
        $request.CertificateExtensions.Add($san.Build())
    }

    [byte[]]$serial = [byte[]]::new(16)
    [Security.Cryptography.RandomNumberGenerator]::Fill($serial)
    $serial[0] = $serial[0] -band 0x7F
    $now = [DateTimeOffset]::UtcNow
    return $request.Create(
        $Issuer, $now.AddMinutes(-2), $now.AddMinutes(15), $serial
    )
}

try {
    [IO.Directory]::CreateDirectory($FixtureDirectory) | Out-Null
    $caSigner = [Security.Cryptography.ECDsa]::Create(
        [Security.Cryptography.ECCurve]::CreateFromFriendlyName('nistP256')
    )
    $caRequest = [Security.Cryptography.X509Certificates.CertificateRequest]::new(
        [Security.Cryptography.X509Certificates.X500DistinguishedName]::new(
            'CN=Lanveriq Synthetic Ephemeral TLS CA'
        ),
        $caSigner,
        [Security.Cryptography.HashAlgorithmName]::SHA256
    )
    $caRequest.CertificateExtensions.Add(
        [Security.Cryptography.X509Certificates.X509BasicConstraintsExtension]::new(
            $true, $false, 0, $true
        )
    )
    $caRequest.CertificateExtensions.Add(
        [Security.Cryptography.X509Certificates.X509KeyUsageExtension]::new(
            [Security.Cryptography.X509Certificates.X509KeyUsageFlags]::KeyCertSign, $true
        )
    )
    $caRequest.CertificateExtensions.Add(
        [Security.Cryptography.X509Certificates.X509SubjectKeyIdentifierExtension]::new(
            $caRequest.PublicKey, $false
        )
    )
    $now = [DateTimeOffset]::UtcNow
    $root = $caRequest.CreateSelfSigned($now.AddMinutes(-3), $now.AddMinutes(20))
    [IO.File]::WriteAllBytes(
        (Join-Path $FixtureDirectory 'root.der'),
        $root.Export([Security.Cryptography.X509Certificates.X509ContentType]::Cert)
    )
    # The wrong root is an unrelated synthetic CA. No private key exported.
    $otherSigner = [Security.Cryptography.ECDsa]::Create(
        [Security.Cryptography.ECCurve]::CreateFromFriendlyName('nistP256')
    )
    try {
        $otherReq = [Security.Cryptography.X509Certificates.CertificateRequest]::new(
            [Security.Cryptography.X509Certificates.X500DistinguishedName]::new(
                'CN=Lanveriq Synthetic WRONG TLS CA'
            ),
            $otherSigner,
            [Security.Cryptography.HashAlgorithmName]::SHA256
        )
        $otherReq.CertificateExtensions.Add(
            [Security.Cryptography.X509Certificates.X509BasicConstraintsExtension]::new(
                $true, $false, 0, $true
            )
        )
        $otherReq.CertificateExtensions.Add(
            [Security.Cryptography.X509Certificates.X509KeyUsageExtension]::new(
                [Security.Cryptography.X509Certificates.X509KeyUsageFlags]::KeyCertSign, $true
            )
        )
        $other = $otherReq.CreateSelfSigned($now.AddMinutes(-3), $now.AddMinutes(20))
        try {
            [IO.File]::WriteAllBytes(
                (Join-Path $FixtureDirectory 'wrong-root.der'),
                $other.Export([Security.Cryptography.X509Certificates.X509ContentType]::Cert)
            )
        } finally {
            $other.Dispose()
        }
    } finally {
        $otherSigner.Dispose()
    }

    $clientKey = [Security.Cryptography.CngKey]::Create(
        [Security.Cryptography.CngAlgorithm]::ECDsaP256, $clientName, $policy
    )
    $serverKey = [Security.Cryptography.CngKey]::Create(
        [Security.Cryptography.CngAlgorithm]::ECDsaP256, $serverName, $policy
    )
    foreach ($k in @($clientKey, $serverKey)) {
        if ($k.ExportPolicy -ne [Security.Cryptography.CngExportPolicies]::None -or
            $k.KeyUsage -ne [Security.Cryptography.CngKeyUsages]::Signing -or
            $k.Provider.Provider -ne $provider.Provider) {
            throw 'Synthetic Windows software CNG key policy mismatch'
        }
    }
    $clientSigner = [Security.Cryptography.ECDsaCng]::new($clientKey)
    $serverSigner = [Security.Cryptography.ECDsaCng]::new($serverKey)
    $clientLeaf = New-Leaf -SigningKey $clientSigner -Issuer $root -Subject 'CN=Lanveriq Synthetic mTLS Client' -EkuOid '1.3.6.1.5.5.7.3.2' -Server $false
    $serverLeaf = New-Leaf -SigningKey $serverSigner -Issuer $root -Subject 'CN=localhost' -EkuOid '1.3.6.1.5.5.7.3.1' -Server $true
    try {
        [IO.File]::WriteAllBytes(
            (Join-Path $FixtureDirectory 'client.der'),
            $clientLeaf.Export([Security.Cryptography.X509Certificates.X509ContentType]::Cert)
        )
        [IO.File]::WriteAllBytes(
            (Join-Path $FixtureDirectory 'server.der'),
            $serverLeaf.Export([Security.Cryptography.X509Certificates.X509ContentType]::Cert)
        )
    } finally {
        $clientLeaf.Dispose()
        $serverLeaf.Dispose()
    }
    $env:LANVERIQ_SYNTHETIC_CNG_KEY_NAME = $clientName
    $env:LANVERIQ_SYNTHETIC_CNG_SERVER_KEY_NAME = $serverName
    $env:LANVERIQ_SYNTHETIC_CNG_CERT_DIR = $FixtureDirectory
    try {
        $manifest = Join-Path $PSScriptRoot 'rustls-cng-probe/Cargo.toml'
        & cargo +1.94.0 run --quiet --manifest-path $manifest -- --tls13-handshake
        if ($LASTEXITCODE -ne 0) {
            throw 'Rustls CNG in-memory TLS1.3 verification failed'
        }
    } finally {
        Remove-Item Env:LANVERIQ_SYNTHETIC_CNG_KEY_NAME -ErrorAction SilentlyContinue
        Remove-Item Env:LANVERIQ_SYNTHETIC_CNG_SERVER_KEY_NAME -ErrorAction SilentlyContinue
        Remove-Item Env:LANVERIQ_SYNTHETIC_CNG_CERT_DIR -ErrorAction SilentlyContinue
    }
} finally {
    foreach ($s in @($clientSigner, $serverSigner, $caSigner, $root)) {
        if ($null -ne $s) { $s.Dispose() }
    }
    foreach ($entry in @(@($clientKey, $clientName), @($serverKey, $serverName))) {
        if ($null -ne $entry[0]) {
            try {
                $entry[0].Delete()
                if ([Security.Cryptography.CngKey]::Exists($entry[1], $provider)) {
                    throw 'Deleted disposable CNG key still exists'
                }
            } catch {
                $cleanupError = $_
            } finally {
                $entry[0].Dispose()
            }
        }
    }
    if ([IO.Directory]::Exists($FixtureDirectory)) {
        [IO.Directory]::Delete($FixtureDirectory, $true)
    }
    if ($null -ne $cleanupError) {
        throw 'A synthetic CNG key could not be deleted'
    }
}
