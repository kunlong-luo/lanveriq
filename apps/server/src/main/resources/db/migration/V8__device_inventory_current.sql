-- INV-02: bounded latest-only V0.1 asset inventory. Independent of
-- event batches, audit sequence and heartbeat freshness. No seeded data.
-- All child replacements occur in the same Spring/JPA transaction after
-- locking the canonical active device and validating certificate binding.
CREATE TABLE device_inventory_current (
    device_id UUID PRIMARY KEY REFERENCES devices(id),
    schema_version INTEGER NOT NULL CHECK (schema_version = 1),
    snapshot_id UUID NOT NULL,
    inventory_revision BIGINT NOT NULL CHECK (inventory_revision > 0),
    content_digest CHAR(64) NOT NULL
        CHECK (content_digest ~ '^[0-9a-f]{64}$'),
    observed_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    manufacturer VARCHAR(256),
    model VARCHAR(256),
    cpu_model VARCHAR(256),
    cpu_arch VARCHAR(64),
    cpu_physical_cores INTEGER CHECK (cpu_physical_cores > 0 AND cpu_physical_cores <= 1024),
    cpu_logical_cores INTEGER CHECK (cpu_logical_cores > 0 AND cpu_logical_cores <= 2048),
    memory_total_bytes BIGINT CHECK (memory_total_bytes >= 0),
    os_family VARCHAR(32) NOT NULL CHECK (os_family IN ('windows','linux')),
    os_name VARCHAR(128),
    os_version VARCHAR(128),
    os_build VARCHAR(128),
    os_kernel_release VARCHAR(128),
    os_arch VARCHAR(64),
    capabilities_json JSONB NOT NULL CHECK (jsonb_typeof(capabilities_json) = 'object')
);

CREATE TABLE device_inventory_interfaces (
    device_id UUID NOT NULL REFERENCES device_inventory_current(device_id)
        ON DELETE CASCADE,
    interface_key VARCHAR(128) NOT NULL,
    interface_name VARCHAR(128) NOT NULL,
    kind VARCHAR(32),
    mac_address VARCHAR(17),
    link_state VARCHAR(16),
    is_virtual BOOLEAN,
    PRIMARY KEY (device_id, interface_key),
    CONSTRAINT inventory_mac_format CHECK (
        mac_address IS NULL OR mac_address ~ '^([0-9a-f]{2}:){5}[0-9a-f]{2}$'),
    CONSTRAINT inventory_link_state CHECK (
        link_state IS NULL OR link_state IN ('up','down','unknown'))
);

CREATE TABLE device_inventory_addresses (
    device_id UUID NOT NULL,
    interface_key VARCHAR(128) NOT NULL,
    ip INET NOT NULL,
    prefix_length SMALLINT NOT NULL,
    family VARCHAR(4) NOT NULL CHECK (family IN ('ipv4','ipv6')),
    scope VARCHAR(20),
    PRIMARY KEY (device_id,interface_key,ip,prefix_length),
    FOREIGN KEY (device_id,interface_key)
        REFERENCES device_inventory_interfaces(device_id,interface_key)
        ON DELETE CASCADE,
    CONSTRAINT inventory_address_prefix CHECK (
        (family='ipv4' AND family(ip)=4 AND prefix_length BETWEEN 0 AND 32)
        OR (family='ipv6' AND family(ip)=6 AND prefix_length BETWEEN 0 AND 128)),
    CONSTRAINT inventory_address_scope CHECK (
        scope IS NULL OR scope IN ('global','private','link_local','loopback','unknown'))
);

CREATE TABLE device_inventory_volumes (
    device_id UUID NOT NULL REFERENCES device_inventory_current(device_id)
        ON DELETE CASCADE,
    volume_key VARCHAR(128) NOT NULL,
    mount_point VARCHAR(256) NOT NULL,
    filesystem VARCHAR(64),
    total_bytes BIGINT NOT NULL CHECK (total_bytes >= 0),
    free_bytes BIGINT CHECK (free_bytes >= 0 AND free_bytes <= total_bytes),
    is_system BOOLEAN,
    PRIMARY KEY (device_id,volume_key)
);
