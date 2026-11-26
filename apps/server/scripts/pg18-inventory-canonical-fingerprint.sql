-- CI-only canonical PostgreSQL18 inventory + Flyway full-row serialization.
-- Shared by source and separate-runner verifier. Not independent custody.
SELECT jsonb_build_object(
  'inventory_current', COALESCE(
    (SELECT jsonb_agg(to_jsonb(r) ORDER BY r.device_id)
     FROM device_inventory_current r), '[]'::jsonb),
  'inventory_interfaces', COALESCE(
    (SELECT jsonb_agg(to_jsonb(r) ORDER BY r.device_id, r.interface_key)
     FROM device_inventory_interfaces r), '[]'::jsonb),
  'inventory_addresses', COALESCE(
    (SELECT jsonb_agg(to_jsonb(r) ORDER BY r.device_id, r.interface_key, r.ip, r.prefix_length)
     FROM device_inventory_addresses r), '[]'::jsonb),
  'inventory_volumes', COALESCE(
    (SELECT jsonb_agg(to_jsonb(r) ORDER BY r.device_id, r.volume_key)
     FROM device_inventory_volumes r), '[]'::jsonb),
  'flyway_history', COALESCE(
    (SELECT jsonb_agg(to_jsonb(r) ORDER BY r.installed_rank)
     FROM flyway_schema_history r), '[]'::jsonb)
)::text;
