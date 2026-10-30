# D-19 — Device inventory / network model (V0.1)

~~~mermaid
erDiagram
  DEVICE_GROUPS ||--o{ DEVICES : contains
  DEVICES ||--o| DEVICE_INVENTORY_CURRENT : has_snapshot
  DEVICES ||--o{ DEVICE_NETWORK_INTERFACES : has
  DEVICE_NETWORK_INTERFACES ||--o{ DEVICE_INTERFACE_ADDRESSES : assigns
  DEVICES ||--o{ DEVICE_VOLUMES : mounts
  DEVICES ||--o{ DEVICE_METRIC_SAMPLES : produces
  DEVICES ||--o{ DEVICE_CONNECTION_OBSERVATIONS : connects

  DEVICES {
    uuid id PK
    uuid group_id FK
    string hostname
    string device_label
    string asset_tag
    uuid replaces_device_id FK
  }
  DEVICE_INVENTORY_CURRENT {
    uuid device_id PK,FK
    uuid snapshot_id
    int schema_version
    datetime observed_at
    datetime received_at
    string cpu_model
    int cpu_logical_cores
    bigint ram_total_bytes
    string os_name
    string kernel_release
  }
  DEVICE_NETWORK_INTERFACES {
    uuid device_id PK,FK
    string interface_key PK
    string name
    string kind
    string mac_address
    boolean is_virtual
    string link_state
  }
  DEVICE_INTERFACE_ADDRESSES {
    uuid device_id FK
    string interface_key FK
    string ip_with_prefix
    string scope
  }
  DEVICE_VOLUMES {
    uuid device_id PK,FK
    string volume_key PK
    string mount_point
    bigint total_bytes
    bigint free_bytes
  }
  DEVICE_METRIC_SAMPLES {
    uuid device_id FK
    datetime sampled_at
    float cpu_usage_percent
    bigint memory_used_bytes
  }
  DEVICE_CONNECTION_OBSERVATIONS {
    uuid device_id FK
    string server_observed_remote_ip
    datetime received_at
  }
~~~

This expands the high-level [PostgreSQL ER diagram](database-er.md) and does not imply every property lives directly in `devices`. Real schema should use PostgreSQL `inet` for IP addresses, and normalized nullable MAC where approved. System device ID is server-generated UUIDv7; IP/MAC/hostname are **changing attributes**, not unique keys. [Inventory design](../architecture/device-inventory.md).
