//! INV-01-B: explicit on-demand OS asset collection using maintained crates.
//! This is deliberately NOT called from the Agent service loop or network.
//! No process list, home directory scan, serial/TPM, Wi-Fi SSID, session user,
//! activity metadata, clipboard, or screen information is retrieved.
use crate::inventory::{
    Address, Cpu, Hardware, Memory, NetworkInterface, OperatingSystem, RawInventory, Volume,
};
use network_interface::{Addr, NetworkInterface as OsNetworkInterface, NetworkInterfaceConfig};
use sha2::{Digest, Sha256};
use std::collections::BTreeMap;
use std::net::IpAddr;
#[cfg(test)]
use std::net::{Ipv4Addr, Ipv6Addr};
use sysinfo::{CpuRefreshKind, Disks, MemoryRefreshKind, RefreshKind, System};

/// This call is an explicit local opt-in; never schedule or upload it
/// automatically. On missing OS permissions leave fields absent and report
/// a capability state, rather than fabricating an address or zero capacity.
pub fn collect_local_inventory() -> RawInventory {
    // Narrow refresh deliberately excludes processes, users, command-lines,
    // open files and any application/window history.
    let system = System::new_with_specifics(
        RefreshKind::nothing()
            .with_cpu(CpuRefreshKind::everything())
            .with_memory(MemoryRefreshKind::everything()),
    );
    let cpus = system.cpus();
    let cpu = if cpus.is_empty() {
        None
    } else {
        Some(Cpu {
            model: cpus
                .first()
                .map(|value| value.brand().to_string())
                .filter(|value| !value.is_empty()),
            arch: Some(std::env::consts::ARCH.to_string()),
            physical_cores: System::physical_core_count().and_then(|n| u32::try_from(n).ok()),
            logical_cores: u32::try_from(cpus.len()).ok(),
        })
    };
    let ram = (system.total_memory() > 0).then_some(Memory {
        total_bytes: Some(system.total_memory()),
    });
    let os_info = os_info::get();
    let family = if cfg!(windows) { "windows" } else { "linux" };
    let os = OperatingSystem {
        family: family.to_string(),
        name: Some(os_info.os_type().to_string()),
        version: match os_info.version() {
            os_info::Version::Unknown => None,
            value => Some(value.to_string()),
        },
        build: if cfg!(windows) {
            System::kernel_version()
        } else {
            None
        },
        kernel_release: if cfg!(windows) {
            None
        } else {
            System::kernel_version()
        },
        arch: Some(std::env::consts::ARCH.to_string()),
    };

    let (interfaces, nic_state) = match OsNetworkInterface::show() {
        Ok(interfaces) => (
            interfaces
                .into_iter()
                .filter(|nic| !nic.internal)
                .map(normalize_interface)
                .collect(),
            "available",
        ),
        Err(_) => (Vec::new(), "unavailable"),
    };

    // sysinfo intentionally ignores tmpfs / network shares by default.
    // Further drop personal-session mounts to avoid embedding user names in
    // mount paths. No filesystem traversal, file listing or metadata scan.
    let disks = Disks::new_with_refreshed_list();
    let volumes: Vec<_> = disks
        .iter()
        .filter_map(|disk| {
            let mount = disk.mount_point().to_string_lossy().into_owned();
            if mount.contains("/home/")
                || mount.starts_with("/run/user/")
                || mount.starts_with("/media/")
                || mount.starts_with("/mnt/")
                || mount.contains("\\Users\\")
                || disk.total_space() == 0
                || disk.available_space() > disk.total_space()
            {
                return None;
            }
            let filesystem = disk.file_system().to_string_lossy().into_owned();
            Some(Volume {
                key: stable_attribute_key("vol", &mount),
                mount_point: mount.clone(),
                filesystem: (!filesystem.is_empty()).then_some(filesystem),
                total_bytes: disk.total_space(),
                free_bytes: Some(disk.available_space()),
                is_system: Some(mount == "/" || mount.eq_ignore_ascii_case("C:\\")),
            })
        })
        .collect();
    let volume_state = if volumes.is_empty() {
        "unavailable"
    } else {
        "available"
    };

    let capabilities = BTreeMap::from([
        (
            "hardware_inventory".to_string(),
            if cpu.is_some() || ram.is_some() {
                "available"
            } else {
                "unavailable"
            }
            .to_string(),
        ),
        ("network_interfaces".to_string(), nic_state.to_string()),
        ("mounted_volumes".to_string(), volume_state.to_string()),
        ("desktop_session".to_string(), "not_collected".to_string()),
        ("manufacturer_model".to_string(), "unavailable".to_string()),
    ]);

    RawInventory {
        hardware: Hardware {
            manufacturer: None,
            model: None,
            cpu,
            memory: ram,
        },
        os,
        interfaces,
        volumes,
        capabilities,
    }
}

fn normalize_interface(value: OsNetworkInterface) -> NetworkInterface {
    let mut addresses = Vec::new();
    for addr in value.addr {
        let (ip, mask) = match addr {
            Addr::V4(v4) => (IpAddr::V4(v4.ip), v4.netmask.map(IpAddr::V4)),
            Addr::V6(v6) => (IpAddr::V6(v6.ip), v6.netmask.map(IpAddr::V6)),
        };
        let Some(prefix_length) = mask.and_then(prefix_from_netmask) else {
            // Unknown mask stays unknown: do not invent a /0 or /32.
            continue;
        };
        addresses.push(Address {
            ip: ip.to_string(),
            prefix_length,
            family: if ip.is_ipv4() { "ipv4" } else { "ipv6" }.to_string(),
            scope: Some(address_scope(ip).to_string()),
        });
    }
    NetworkInterface {
        key: stable_attribute_key("nic", &value.name),
        name: value.name,
        kind: None,
        mac_address: value.mac_addr,
        link_state: None,
        is_virtual: None,
        addresses,
    }
}

fn prefix_from_netmask(mask: IpAddr) -> Option<u8> {
    match mask {
        IpAddr::V4(mask) => contiguous_prefix(u128::from(u32::from(mask)), 32),
        IpAddr::V6(mask) => contiguous_prefix(u128::from(mask), 128),
    }
}

fn contiguous_prefix(bits: u128, width: u32) -> Option<u8> {
    let ones = bits.count_ones();
    if ones > width {
        return None;
    }
    let expected = if ones == 0 {
        0
    } else {
        u128::MAX << (128 - ones) >> (128 - width)
    };
    (bits == expected).then_some(ones as u8)
}

fn address_scope(ip: IpAddr) -> &'static str {
    match ip {
        IpAddr::V4(ip) if ip.is_loopback() => "loopback",
        IpAddr::V4(ip) if ip.is_link_local() => "link_local",
        IpAddr::V4(ip) if ip.is_private() => "private",
        IpAddr::V6(ip) if ip.is_loopback() => "loopback",
        IpAddr::V6(ip) if ip.is_unicast_link_local() => "link_local",
        IpAddr::V6(ip) if ip.is_unique_local() => "private",
        _ => "global",
    }
}

// Provider identity key is stable for a given local interface/mount name, but
// is never a credential, device_id, hardware fingerprint or user identifier.
fn stable_attribute_key(prefix: &str, name: &str) -> String {
    let digest = Sha256::digest(name.as_bytes());
    format!(
        "{prefix}-{:02x}{:02x}{:02x}{:02x}{:02x}{:02x}",
        digest[0], digest[1], digest[2], digest[3], digest[4], digest[5],
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn ip_netmasks_must_be_contiguous_and_never_guessed() {
        assert_eq!(
            prefix_from_netmask(IpAddr::V4(Ipv4Addr::new(255, 255, 255, 0))),
            Some(24)
        );
        assert_eq!(
            prefix_from_netmask(IpAddr::V4(Ipv4Addr::new(255, 0, 255, 0))),
            None
        );
        assert_eq!(
            prefix_from_netmask(IpAddr::V6(Ipv6Addr::new(
                0xffff, 0xffff, 0xffff, 0xffff, 0, 0, 0, 0,
            ))),
            Some(64)
        );
        assert_eq!(
            prefix_from_netmask(IpAddr::V6(Ipv6Addr::UNSPECIFIED)),
            Some(0)
        );
    }

    #[test]
    fn keyed_attributes_do_not_use_mutable_mac_or_network_address() {
        assert_eq!(
            stable_attribute_key("nic", "eth0"),
            stable_attribute_key("nic", "eth0")
        );
        assert_ne!(
            stable_attribute_key("nic", "eth0"),
            stable_attribute_key("nic", "eth1")
        );
    }

    #[test]
    fn address_scope_does_not_claim_publicity_for_private_addresses() {
        assert_eq!(
            address_scope(IpAddr::V4(Ipv4Addr::new(192, 168, 0, 3))),
            "private"
        );
        assert_eq!(address_scope(IpAddr::V4(Ipv4Addr::LOCALHOST)), "loopback");
        assert_eq!(address_scope(IpAddr::V6(Ipv6Addr::LOCALHOST)), "loopback");
    }
}
