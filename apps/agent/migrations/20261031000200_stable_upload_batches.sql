-- Stable, single outstanding AGT-03 batch identity. SQLx migration runs
-- atomically and is independent of the device's actual TLS credential.
CREATE TABLE upload_batches (
    singleton INTEGER PRIMARY KEY CHECK(singleton=1),
    batch_id TEXT NOT NULL UNIQUE,
    last_sequence INTEGER NOT NULL CHECK(last_sequence>0),
    content_fingerprint TEXT NOT NULL CHECK(length(content_fingerprint)=64)
);
