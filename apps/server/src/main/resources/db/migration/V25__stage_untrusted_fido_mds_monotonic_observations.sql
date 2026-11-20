-- A11 D4-C4: SYNTHETIC OFFLINE MDS observation records, never a trusted
-- FIDO metadata cache or authorization source. No production MDS client.
CREATE TABLE admin_fido_mds_untrusted_streams_staging (
  stream_id UUID PRIMARY KEY DEFAULT uuidv7(),
  created_by UUID NOT NULL REFERENCES users(id),
  creator_session_version BIGINT NOT NULL CHECK (creator_session_version >= 0),
  stream_state VARCHAR(32) NOT NULL DEFAULT 'synthetic_untrusted'
    CHECK (stream_state = 'synthetic_untrusted'),
  created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE FUNCTION guard_admin_fido_mds_untrusted_stream_insert()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM users actor
    JOIN user_role_scopes scope ON scope.user_id=actor.id
    JOIN roles role ON role.id=scope.role_id
    WHERE actor.id=NEW.created_by AND actor.disabled_at IS NULL
      AND actor.session_version=NEW.creator_session_version
      AND role.code='owner_security_admin'
    FOR SHARE OF actor,scope,role
  ) THEN
    RAISE EXCEPTION USING ERRCODE='23514',
      MESSAGE='synthetic MDS stream requires current owner security scope';
  END IF;
  NEW.created_at := clock_timestamp();
  RETURN NEW;
END;
$$;
CREATE TRIGGER admin_fido_mds_untrusted_stream_guard
BEFORE INSERT ON admin_fido_mds_untrusted_streams_staging
FOR EACH ROW EXECUTE FUNCTION guard_admin_fido_mds_untrusted_stream_insert();
CREATE TRIGGER admin_fido_mds_untrusted_stream_immutable
BEFORE UPDATE OR DELETE ON admin_fido_mds_untrusted_streams_staging
FOR EACH ROW EXECUTE FUNCTION reject_admin_passkey_record_history_mutation();

CREATE TABLE admin_fido_mds_untrusted_observations_staging (
  observation_id UUID PRIMARY KEY DEFAULT uuidv7(),
  stream_id UUID NOT NULL REFERENCES admin_fido_mds_untrusted_streams_staging(stream_id),
  sequence_no BIGINT NOT NULL CHECK (sequence_no > 0),
  previous_jws_sha256 BYTEA CHECK (
    previous_jws_sha256 IS NULL OR octet_length(previous_jws_sha256)=32),
  jws_sha256 BYTEA NOT NULL CHECK (octet_length(jws_sha256)=32),
  synthetic_claimed_issued_at TIMESTAMPTZ NOT NULL,
  observed_by UUID NOT NULL REFERENCES users(id),
  observer_session_version BIGINT NOT NULL CHECK (observer_session_version >= 0),
  observation_state VARCHAR(32) NOT NULL DEFAULT 'observed_untrusted'
    CHECK (observation_state='observed_untrusted'),
  recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
  UNIQUE (stream_id,sequence_no),
  UNIQUE (stream_id,jws_sha256)
);
CREATE INDEX admin_fido_mds_untrusted_observation_seq_idx
  ON admin_fido_mds_untrusted_observations_staging(stream_id,sequence_no DESC);

CREATE FUNCTION guard_admin_fido_mds_untrusted_observation_insert()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  creator UUID;
  creator_version BIGINT;
  previous_sequence BIGINT;
  previous_digest BYTEA;
BEGIN
  -- A stream row is the serialization point for every observation in that
  -- stream. No read-then-write race or concurrent divergent "latest" head.
  SELECT created_by,creator_session_version
    INTO creator,creator_version
    FROM admin_fido_mds_untrusted_streams_staging
    WHERE stream_id=NEW.stream_id AND stream_state='synthetic_untrusted'
    FOR UPDATE;
  IF creator IS NULL THEN
    RAISE EXCEPTION USING ERRCODE='23514',
      MESSAGE='synthetic MDS observation requires an existing untrusted stream';
  END IF;
  -- Current owner must still be valid; observer must share active scope.
  IF NOT EXISTS (
    SELECT 1 FROM users owner_actor
    JOIN user_role_scopes owner_scope ON owner_scope.user_id=owner_actor.id
    JOIN roles r ON r.id=owner_scope.role_id AND r.code='owner_security_admin'
    JOIN user_role_scopes observer_scope ON observer_scope.role_id=r.id
        AND observer_scope.device_group_id=owner_scope.device_group_id
        AND observer_scope.user_id=NEW.observed_by
    JOIN users observer ON observer.id=observer_scope.user_id
    WHERE owner_actor.id=creator AND owner_actor.disabled_at IS NULL
      AND owner_actor.session_version=creator_version
      AND observer.disabled_at IS NULL
      AND observer.session_version=NEW.observer_session_version
    FOR SHARE OF owner_actor,owner_scope,r,observer_scope,observer
  ) THEN
    RAISE EXCEPTION USING ERRCODE='23514',
      MESSAGE='synthetic MDS stream owner or observer is no longer authorized';
  END IF;
  SELECT sequence_no,jws_sha256
    INTO previous_sequence,previous_digest
    FROM admin_fido_mds_untrusted_observations_staging
    WHERE stream_id=NEW.stream_id ORDER BY sequence_no DESC LIMIT 1;

  IF previous_sequence IS NULL THEN
    IF NEW.sequence_no<>1 OR NEW.previous_jws_sha256 IS NOT NULL THEN
      RAISE EXCEPTION USING ERRCODE='23514',
        MESSAGE='synthetic MDS first observation must begin at sequence one';
    END IF;
  ELSIF previous_sequence=9223372036854775807
     OR NEW.sequence_no<>previous_sequence+1
     OR NEW.previous_jws_sha256 IS DISTINCT FROM previous_digest THEN
    RAISE EXCEPTION USING ERRCODE='23514',
      MESSAGE='synthetic MDS observation replay, gap or digest fork rejected';
  END IF;
  -- DB server time is NOT a trusted production time source, and the iat has
  -- NOT been bound to a FIDO-approved signer by this SQL trigger.
  IF NEW.synthetic_claimed_issued_at>clock_timestamp()+INTERVAL '5 minutes'
     OR NEW.synthetic_claimed_issued_at<clock_timestamp()-INTERVAL '48 hours' THEN
    RAISE EXCEPTION USING ERRCODE='23514',
      MESSAGE='synthetic MDS observation claimed time is outside test window';
  END IF;
  NEW.recorded_at := clock_timestamp();
  RETURN NEW;
END;
$$;
CREATE TRIGGER admin_fido_mds_untrusted_observation_guard
BEFORE INSERT ON admin_fido_mds_untrusted_observations_staging
FOR EACH ROW EXECUTE FUNCTION guard_admin_fido_mds_untrusted_observation_insert();
CREATE TRIGGER admin_fido_mds_untrusted_observation_immutable
BEFORE UPDATE OR DELETE ON admin_fido_mds_untrusted_observations_staging
FOR EACH ROW EXECUTE FUNCTION reject_admin_passkey_record_history_mutation();
