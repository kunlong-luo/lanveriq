# Windows synthetic-only, fail-closed inspection of an actual persisted
# Microsoft Software KSP key security descriptor. Caller retains ownership.
# This helper never changes the key DACL or exports private material.
param(
    [Parameter(Mandatory = $true)]
    [System.Security.Cryptography.CngKey] $Key
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not $IsWindows -or [Environment]::Version.Major -lt 8) {
    throw 'Native Windows/.NET 8+ required for CNG key ACL verification'
}
if (-not $Key.KeyName.StartsWith('lanveriq-synthetic-csr-') -or
    $Key.IsEphemeral -or
    $Key.Provider.Provider -ne 'Microsoft Software Key Storage Provider' -or
    $Key.ExportPolicy -ne [Security.Cryptography.CngExportPolicies]::None -or
    $Key.KeyUsage -ne [Security.Cryptography.CngKeyUsages]::Signing) {
    throw 'Only persistent non-exportable synthetic user CNG signing keys may be examined'
}

# Use Microsoft's NCryptGetProperty security-descriptor API, NOT the ACL of
# a presumed file path beneath AppData (the KSP chooses its own storage).
# This P/Invoke reads only OWNER_SECURITY_INFORMATION | DACL_SECURITY_INFORMATION.
# SafeNCryptKeyHandle is passed directly to P/Invoke and independently closed.
if (-not ('LanveriqSyntheticCngAclReader' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.ComponentModel;
using System.Runtime.InteropServices;
using Microsoft.Win32.SafeHandles;
public static class LanveriqSyntheticCngAclReader
{
    [DllImport("ncrypt.dll", EntryPoint="NCryptGetProperty", CharSet=CharSet.Unicode)]
    private static extern int NCryptGetProperty(
        SafeNCryptKeyHandle key, string name,
        [Out] byte[] output, int capacity,
        out int length, int flags);

    public static byte[] ReadOwnerAndDacl(SafeNCryptKeyHandle key)
    {
        // The descriptor is bounded. 0x1 OWNER | 0x4 DACL.
        byte[] buffer = new byte[65536];
        int length;
        int result = NCryptGetProperty(key, "Security Descr",
            buffer, buffer.Length, out length, 0x1 | 0x4);
        if (result != 0)
            throw new InvalidOperationException(
                "Native NCryptGetProperty(Security Descr) failed; code " +
                unchecked((uint)result).ToString("X8"));
        if (length < 20 || length > buffer.Length)
            throw new InvalidOperationException("CNG security descriptor size invalid");
        byte[] exact = new byte[length];
        Buffer.BlockCopy(buffer, 0, exact, 0, length);
        return exact;
    }
}
'@
}

$keyHandle = $Key.Handle
try {
    [byte[]] $bytes = [LanveriqSyntheticCngAclReader]::ReadOwnerAndDacl($keyHandle)
} finally {
    $keyHandle.Dispose()
}
$acl = [System.Security.AccessControl.RawSecurityDescriptor]::new($bytes, 0)
if ($null -eq $acl.Owner) {
    throw 'Native CNG signing key security descriptor lacks an owner'
}
if ($null -eq $acl.DiscretionaryAcl) {
    # A missing DACL grants unrestricted access and is not acceptable.
    throw 'Native CNG signing key has NULL DACL'
}

$currentSid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
if ($acl.Owner.Value -ne $currentSid -and
    $acl.Owner.Value -ne 'S-1-5-32-544') {
    throw 'Native current-user CNG key is not owned by the user or Administrators'
}

# These well-known principals must NEVER receive an ALLOW ACE. An explicit
# non-broad DACL is necessary but not sufficient for service-SID security.
$deniedBroadSids = @(
    'S-1-1-0',        # Everyone
    'S-1-5-11',       # Authenticated Users
    'S-1-5-32-545',   # BUILTIN\Users
    'S-1-5-7',        # Anonymous
    'S-1-5-2',        # NETWORK
    'S-1-5-4'         # INTERACTIVE
)
foreach ($ace in $acl.DiscretionaryAcl) {
    if ($ace -isnot [System.Security.AccessControl.KnownAce]) {
        throw 'Unrecognized CNG key access control entry'
    }
    $sid = $ace.SecurityIdentifier.Value
    $aceType = $ace.AceType.ToString()
    if ($aceType.StartsWith('AccessAllowed') -and
        $deniedBroadSids -contains $sid) {
        throw 'CNG private key permits a broad Windows principal'
    }
}
Write-Output 'PASS: actual persisted Windows CNG key has a non-null DACL, controlled owner and no broad-principal allow ACEs.'
