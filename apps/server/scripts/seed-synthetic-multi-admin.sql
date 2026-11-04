-- MANUAL, DISPOSABLE TESTCONTAINERS-ONLY SQL fixture. NEVER run on a
-- production database, persist credentials, or attach real endpoints.
-- The harness verifies the freshly bootstrapped DB has exactly ONE synthetic
-- owner before this is run, after the empty-database Chromium gate.
DO $lanveriq_synthetic_multi$
DECLARE
  owner_id uuid;
  owner_group uuid;
  owner_hash text;
  other_group uuid;
  other_id uuid;
BEGIN
  IF (SELECT count(*) FROM users) <> 1 OR
     (SELECT count(*) FROM devices) <> 0 OR
     (SELECT count(*) FROM device_groups) <> 1 THEN
    RAISE EXCEPTION 'synthetic browser fixture requires one fresh bootstrap';
  END IF;
  SELECT u.id, u.password_hash, s.device_group_id
    INTO STRICT owner_id, owner_hash, owner_group
    FROM users u
    JOIN user_role_scopes s ON s.user_id=u.id
    JOIN roles r ON r.id=s.role_id
   WHERE u.normalized_login='synthetic-owner'
     AND u.disabled_at IS NULL AND r.code='owner_security_admin';
  -- Deliberately reuse ONLY the one-time generated hash inside the throwaway
  -- PG18 test container: two separate identities/session cookies are tested.
  -- This is not an account-provisioning feature or production credential flow.
  INSERT INTO device_groups (name) VALUES ('SyntheticBrowserB')
    RETURNING id INTO other_group;
  INSERT INTO users(normalized_login,password_hash)
    VALUES ('synthetic-browser-b',owner_hash) RETURNING id INTO other_id;
  INSERT INTO user_role_scopes(user_id,role_id,device_group_id)
    SELECT other_id,id,other_group FROM roles WHERE code='it_operator';
  INSERT INTO devices (group_id,device_label,enrollment_state) VALUES
    (owner_group,'SyntheticBrowserA-1','active'),
    (owner_group,'SyntheticBrowserA-2','pending_certificate'),
    (other_group,'SyntheticBrowserB-1','active');
END
$lanveriq_synthetic_multi$;
