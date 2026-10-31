//! INV-01-A: strictly local, synthetic-first normalization of V0.1 asset
//! snapshots. No OS probes, no background collector, no network and no upload.
//! A later approved sysinfo/os_info/network-interface adapter may supply
//! these types AFTER Cargo.lock is resolved and cross-platform CI passes.
use serde::{Deserialize, Serialize};
use std::collections::{BTreeMap, BTreeSet};
use std::net::IpAddr;
use thiserror::Error;
use uuid::Uuid;

const MAX_INTERFACES: usize = 32;
const MAX_ADDRESSES_PER_INTERFACE: usize = 16;
const MAX_VOLUMES: usize = 64;
const MAX_CAPABILITIES: usize = 16;
const MAX_SNAPSHOT_BYTES: usize = 128 * 1024;

#[derive(Debug, Error, PartialEq, Eq)]
pub enum InventoryError {
    #[error("unsupported inventory schema or invalid identity/revision")]
    InvalidIdentity,
    #[error("invalid or unsafe inventory field")]
    InvalidField,
    #[error("inventory exceeds the V0.1 bounds")]
    TooLarge,
    #[error("conflicting interface or volume identity")]
    Conflict,
    #[error("inventory JSON could not be serialized")]
    Serialization,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct InventorySnapshot {
    pub schema_version: u32,
    pub snapshot_id: Uuid,
    pub inventory_revision: u64,
    pub device_id: Uuid,
    pub observed_at: String,
    pub hardware: Hardware,
    pub os: OperatingSystem,
    pub interfaces: Vec<NetworkInterface>,
    pub volumes: Vec<Volume>,
    pub capabilities: BTreeMap<String, String>,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Hardware {
    pub manufacturer: Option<String>,
    pub model: Option<String>,
    pub cpu: Option<Cpu>,
    pub memory: Option<Memory>,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Cpu {
    pub model: Option<String>,
    pub arch: Option<String>,
    pub physical_cores: Option<u32>,
    pub logical_cores: Option<u32>,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Memory {
    pub total_bytes: Option<u64>,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct OperatingSystem {
    pub family: String,
    pub name: Option<String>,
    pub version: Option<String>,
    pub build: Option<String>,
    pub kernel_release: Option<String>,
    pub arch: Option<String>,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct NetworkInterface {
    pub key: String,
    pub name: String,
    pub kind: Option<String>,
    pub mac_address: Option<String>,
    pub link_state: Option<String>,
    pub is_virtual: Option<bool>,
    pub addresses: Vec<Address>,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Address {
    pub ip: String,
    pub prefix_length: u8,
    pub family: String,
    pub scope: Option<String>,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Volume {
    pub key: String,
    pub mount_point: String,
    pub filesystem: Option<String>,
    pub total_bytes: u64,
    pub free_bytes: Option<u64>,
    pub is_system: Option<bool>,
}

/// A synthetic/test or future approved platform adapter supplies public asset
/// attributes. The caller owns the identity, revision and stable snapshot ID;
/// never infer those from MAC, IP, hostname or hardware serials.
#[derive(Clone, Debug)]
pub struct RawInventory {
    pub hardware: Hardware,
    pub os: OperatingSystem,
    pub interfaces: Vec<NetworkInterface>,
    pub volumes: Vec<Volume>,
    pub capabilities: BTreeMap<String, String>,
}

impl InventorySnapshot {
    pub fn from_raw(
        device_id: Uuid,
        snapshot_id: Uuid,
        inventory_revision: u64,
        observed_at: String,
        raw: RawInventory,
    ) -> Result<Self, InventoryError> {
        if device_id.is_nil()
            || snapshot_id.is_nil()
            || inventory_revision == 0
            || inventory_revision > i64::MAX as u64
        {
            return Err(InventoryError::InvalidIdentity);
        }
        if observed_at.len() < 20
            || observed_at.len() > 35
            || !observed_at.ends_with('Z')
            || !observed_at.bytes().all(|byte| {
                byte.is_ascii_digit() || matches!(byte, b'-' | b'T' | b':' | b'.' | b'Z')
            })
        {
            return Err(InventoryError::InvalidField);
        }
        let hardware = raw.hardware;
        optional(&hardware.manufacturer, 256)?;
        optional(&hardware.model, 256)?;
        if let Some(cpu) = &hardware.cpu {
            optional(&cpu.model, 256)?;
            optional(&cpu.arch, 64)?;
            if cpu.physical_cores.is_some_and(|n| n == 0 || n > 1024)
                || cpu.logical_cores.is_some_and(|n| n == 0 || n > 2048)
                || matches!((cpu.physical_cores, cpu.logical_cores),
                    (Some(physical), Some(logical)) if logical < physical)
            {
                return Err(InventoryError::InvalidField);
            }
        }
        if hardware.memory.as_ref().and_then(|m| m.total_bytes) == Some(0) {
            return Err(InventoryError::InvalidField);
        }
        let os = raw.os;
        if !matches!(os.family.as_str(), "linux" | "windows") {
            return Err(InventoryError::InvalidField);
        }
        for (value, limit) in [
            (&os.name, 128),
            (&os.version, 128),
            (&os.build, 128),
            (&os.kernel_release, 128),
            (&os.arch, 64),
        ] {
            optional(value, limit)?;
        }
        if raw.interfaces.len() > MAX_INTERFACES
            || raw.volumes.len() > MAX_VOLUMES
            || raw.capabilities.len() > MAX_CAPABILITIES
        {
            return Err(InventoryError::TooLarge);
        }

        // network-interface APIs can return a separate row for every IP on a
        // NIC. Merge by stable provider-supplied key, not by mutable MAC/IP.
        let mut interfaces: BTreeMap<String, NetworkInterface> = BTreeMap::new();
        for mut nic in raw.interfaces {
            key(&nic.key)?;
            required(&nic.name, 128)?;
            optional(&nic.kind, 32)?;
            if let Some(state) = &nic.link_state {
                if !matches!(state.as_str(), "up" | "down" | "unknown") {
                    return Err(InventoryError::InvalidField);
                }
            }
            if let Some(mac) = nic.mac_address.take() {
                let lowered = mac.to_ascii_lowercase();
                let valid = lowered.len() == 17
                    && lowered.split(':').count() == 6
                    && lowered.split(':').all(|part| {
                        part.len() == 2 && part.bytes().all(|ch| ch.is_ascii_hexdigit())
                    });
                if !valid {
                    return Err(InventoryError::InvalidField);
                }
                nic.mac_address = Some(lowered);
            }
            for addr in &mut nic.addresses {
                let parsed: IpAddr = addr.ip.parse().map_err(|_| InventoryError::InvalidField)?;
                if (parsed.is_ipv4() && (addr.family != "ipv4" || addr.prefix_length > 32))
                    || (parsed.is_ipv6() && (addr.family != "ipv6" || addr.prefix_length > 128))
                {
                    return Err(InventoryError::InvalidField);
                }
                if let Some(scope) = &addr.scope {
                    if !matches!(
                        scope.as_str(),
                        "global" | "private" | "link_local" | "loopback" | "unknown"
                    ) {
                        return Err(InventoryError::InvalidField);
                    }
                }
                addr.ip = parsed.to_string();
            }
            if let Some(existing) = interfaces.get_mut(&nic.key) {
                if existing.name != nic.name
                    || existing.kind != nic.kind
                    || existing.mac_address != nic.mac_address
                    || existing.link_state != nic.link_state
                    || existing.is_virtual != nic.is_virtual
                {
                    return Err(InventoryError::Conflict);
                }
                existing.addresses.extend(nic.addresses);
            } else {
                interfaces.insert(nic.key.clone(), nic);
            }
        }
        for nic in interfaces.values_mut() {
            nic.addresses.sort_by(|a, b| {
                (&a.family, &a.ip, a.prefix_length).cmp(&(&b.family, &b.ip, b.prefix_length))
            });
            let unique: BTreeSet<_> = nic
                .addresses
                .iter()
                .map(|a| (a.ip.as_str(), a.prefix_length))
                .collect();
            if unique.len() != nic.addresses.len() {
                nic.addresses
                    .dedup_by(|a, b| a.ip == b.ip && a.prefix_length == b.prefix_length);
            }
            if nic.addresses.len() > MAX_ADDRESSES_PER_INTERFACE {
                return Err(InventoryError::TooLarge);
            }
        }

        let mut volumes = BTreeMap::new();
        for volume in raw.volumes {
            key(&volume.key)?;
            required(&volume.mount_point, 256)?;
            optional(&volume.filesystem, 64)?;
            if volume
                .free_bytes
                .is_some_and(|free| free > volume.total_bytes)
            {
                return Err(InventoryError::InvalidField);
            }
            if volumes.insert(volume.key.clone(), volume).is_some() {
                return Err(InventoryError::Conflict);
            }
        }
        for (name, state) in &raw.capabilities {
            key(name)?;
            if !matches!(
                state.as_str(),
                "available"
                    | "not_collected"
                    | "unsupported"
                    | "unavailable"
                    | "permission_denied"
                    | "transient_error"
            ) {
                return Err(InventoryError::InvalidField);
            }
        }
        let candidate = Self {
            schema_version: 1,
            snapshot_id,
            inventory_revision,
            device_id,
            observed_at,
            hardware,
            os,
            interfaces: interfaces.into_values().collect(),
            volumes: volumes.into_values().collect(),
            capabilities: raw.capabilities,
        };
        if serde_json::to_vec(&candidate)
            .map_err(|_| InventoryError::Serialization)?
            .len()
            > MAX_SNAPSHOT_BYTES
        {
            return Err(InventoryError::TooLarge);
        }
        Ok(candidate)
    }
}

fn key(text: &str) -> Result<(), InventoryError> {
    if text.is_empty()
        || text.len() > 128
        || !text
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || matches!(b, b'.' | b'_' | b':' | b'-'))
    {
        return Err(InventoryError::InvalidField);
    }
    Ok(())
}

fn required(text: &str, max: usize) -> Result<(), InventoryError> {
    if text.trim().is_empty() || text.len() > max || text.chars().any(char::is_control) {
        return Err(InventoryError::InvalidField);
    }
    Ok(())
}

fn optional(text: &Option<String>, max: usize) -> Result<(), InventoryError> {
    if let Some(text) = text {
        if text.len() > max || text.chars().any(char::is_control) {
            return Err(InventoryError::InvalidField);
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn raw() -> RawInventory {
        RawInventory {
            hardware: Hardware {
                manufacturer: Some("SyntheticVendor".into()),
                model: None,
                cpu: Some(Cpu {
                    model: Some("Synthetic P-256".into()),
                    arch: Some("x86_64".into()),
                    physical_cores: Some(4),
                    logical_cores: Some(8),
                }),
                memory: Some(Memory {
                    total_bytes: Some(16_000_000_000),
                }),
            },
            os: OperatingSystem {
                family: "linux".into(),
                name: Some("Synthetic Linux".into()),
                version: Some("24.04".into()),
                build: None,
                kernel_release: None,
                arch: Some("x86_64".into()),
            },
            interfaces: vec![
                NetworkInterface {
                    key: "nic-01".into(),
                    name: "eth0".into(),
                    kind: Some("ethernet".into()),
                    mac_address: Some("02:AB:00:00:00:01".into()),
                    link_state: Some("up".into()),
                    is_virtual: Some(false),
                    addresses: vec![Address {
                        ip: "192.0.2.10".into(),
                        prefix_length: 24,
                        family: "ipv4".into(),
                        scope: Some("private".into()),
                    }],
                },
                NetworkInterface {
                    key: "nic-01".into(),
                    name: "eth0".into(),
                    kind: Some("ethernet".into()),
                    mac_address: Some("02:ab:00:00:00:01".into()),
                    link_state: Some("up".into()),
                    is_virtual: Some(false),
                    addresses: vec![Address {
                        ip: "2001:db8::1".into(),
                        prefix_length: 64,
                        family: "ipv6".into(),
                        scope: Some("global".into()),
                    }],
                },
            ],
            volumes: vec![Volume {
                key: "root".into(),
                mount_point: "/".into(),
                filesystem: Some("ext4".into()),
                total_bytes: 100_000,
                free_bytes: Some(40_000),
                is_system: Some(true),
            }],
            capabilities: BTreeMap::from([("desktop_session".into(), "unavailable".into())]),
        }
    }

    fn snapshot(raw: RawInventory) -> Result<InventorySnapshot, InventoryError> {
        InventorySnapshot::from_raw(
            Uuid::now_v7(),
            Uuid::now_v7(),
            17,
            "2026-10-31T09:00:00Z".into(),
            raw,
        )
    }

    #[test]
    fn combines_multiple_addresses_on_same_nic_and_sorts_stably() {
        let item = snapshot(raw()).unwrap();
        assert_eq!(item.interfaces.len(), 1);
        assert_eq!(item.interfaces[0].addresses.len(), 2);
        assert_eq!(
            item.interfaces[0].mac_address.as_deref(),
            Some("02:ab:00:00:00:01")
        );
        assert_eq!(item.volumes.len(), 1);
        let json = serde_json::to_value(&item).unwrap();
        assert_eq!(
            json["hardware"]["memory"]["total_bytes"],
            16_000_000_000_u64
        );
        assert_eq!(json["interfaces"][0]["addresses"][0]["family"], "ipv4");
        assert_eq!(json["inventory_revision"], 17);
        assert!(json.get("user").is_none());
        assert!(json.get("machine_id").is_none());
        assert!(json.get("wifi_ssid").is_none());
    }

    #[test]
    fn inconsistent_nic_metadata_is_rejected_without_guessing_identity() {
        let mut item = raw();
        item.interfaces[1].name = "unexpected".into();
        assert_eq!(snapshot(item), Err(InventoryError::Conflict));
    }

    #[test]
    fn no_dns_lookup_or_invalid_ip_prefix_or_mac_accepted() {
        let mut item = raw();
        item.interfaces[0].addresses[0].ip = "example.com".into();
        assert_eq!(snapshot(item), Err(InventoryError::InvalidField));
        let mut item = raw();
        item.interfaces[0].addresses[0].prefix_length = 40;
        assert_eq!(snapshot(item), Err(InventoryError::InvalidField));
        let mut item = raw();
        item.interfaces[0].mac_address = Some("not-a-mac".into());
        assert_eq!(snapshot(item), Err(InventoryError::InvalidField));
    }

    #[test]
    fn cpu_and_memory_unknown_remain_null_not_zero() {
        let mut item = raw();
        item.hardware.cpu = None;
        item.hardware.memory = None;
        let encoded = serde_json::to_value(snapshot(item).unwrap()).unwrap();
        assert!(encoded["hardware"]["cpu"].is_null());
        assert!(encoded["hardware"]["memory"].is_null());
    }

    #[test]
    fn enforces_bounds_and_no_unknown_fields() {
        let mut item = raw();
        item.interfaces = vec![item.interfaces[0].clone(); 33];
        assert_eq!(snapshot(item), Err(InventoryError::TooLarge));
        let mut item = raw();
        item.capabilities
            .insert("app_activity".into(), "enabled".into());
        assert_eq!(snapshot(item), Err(InventoryError::InvalidField));
        let mut item = raw();
        item.volumes[0].free_bytes = Some(100_001);
        assert_eq!(snapshot(item), Err(InventoryError::InvalidField));
        let encoded = serde_json::to_string(&snapshot(raw()).unwrap()).unwrap();
        let with_hidden = encoded.replacen("{", "{\"logged_in_user\":\"secret\",", 1);
        assert!(serde_json::from_str::<InventorySnapshot>(&with_hidden).is_err());
    }

    #[test]
    fn rejects_untrusted_zero_device_and_revision_without_generating_identity() {
        assert_eq!(
            InventorySnapshot::from_raw(
                Uuid::nil(),
                Uuid::now_v7(),
                1,
                "2026-10-31T09:00:00Z".into(),
                raw()
            ),
            Err(InventoryError::InvalidIdentity)
        );
        assert_eq!(
            InventorySnapshot::from_raw(
                Uuid::now_v7(),
                Uuid::now_v7(),
                0,
                "2026-10-31T09:00:00Z".into(),
                raw()
            ),
            Err(InventoryError::InvalidIdentity)
        );
    }
}
