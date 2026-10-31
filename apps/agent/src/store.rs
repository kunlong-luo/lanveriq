//! SQLx WAL outbox with synthetic events and local-only typed inventory.
//! Neither path is connected to Agent service scheduling or network I/O.
use crate::inventory::{InventorySnapshot, RawInventory};
use crate::inventory_schedule::InventoryScheduleCheckpoint;
use sha2::{Digest, Sha256};
use sqlx::sqlite::{SqliteConnectOptions, SqliteJournalMode, SqlitePoolOptions, SqliteSynchronous};
use sqlx::{Row, SqlitePool};
use std::{path::Path, time::Duration};
use thiserror::Error;
use uuid::Uuid;

const MAX_EVENTS: i64 = 2048;
const MAX_QUEUED_BYTES: i64 = 16 * 1024 * 1024;
const MAX_EVENT_BYTES: usize = 16384;
const MAX_GAPS: i64 = 256;

#[derive(Debug, Error)]
pub enum StoreError {
    #[error("absolute SQLite path with an existing parent directory required")]
    InvalidPath,
    #[error("SQLite build missing WAL-reset security fix")]
    UnsafeSqlite,
    #[error("invalid synthetic fixture")]
    InvalidFixture,
    #[error("invalid normalized inventory")]
    Inventory(#[from] crate::inventory::InventoryError),
    #[error("inventory belongs to a different enrolled device or has conflicting state")]
    InventoryConflict,
    #[error("outbox full; gap recorded if space available")]
    QueueFull,
    #[error("duplicate event identity")]
    Duplicate,
    #[error("SQLite statement failed")]
    Database(#[from] sqlx::Error),
    #[error("SQLite migration failed")]
    Migration(#[from] sqlx::migrate::MigrateError),
    #[error("invalid JSON")]
    Json(#[from] serde_json::Error),
}

#[derive(Debug, Clone)]
pub struct SyntheticEvent {
    pub event_id: Uuid,
    pub occurred_at_utc: String,
    pub policy_version: i64,
    pub payload: serde_json::Value,
    pub cursor: Vec<u8>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct PendingEvent {
    pub event_id: Uuid,
    pub sequence: i64,
    pub policy_version: i64,
    pub occurred_at_utc: String,
    pub payload_json: String,
    pub content_digest: String,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct PendingInventory {
    pub snapshot_id: Uuid,
    pub revision: i64,
    pub body_json: String,
}

/// A coherent, verified local state read for a future explicitly approved
/// scheduler driver. No OS probe, timer, HTTP request or ACK is performed.
#[derive(Debug, Clone)]
pub struct InventoryScheduleRecovery {
    pub checkpoint: Option<InventoryScheduleCheckpoint>,
    pub generation: i64,
    pub pending: Option<PendingInventory>,
}

/// Local staging outcomes. No outcome implies remote upload or server ACK.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum InventoryStage {
    Staged(PendingInventory),
    AlreadyPending(PendingInventory),
    Unchanged,
}

#[derive(Debug, Clone)]
pub struct PreparedBatch {
    pub batch_id: Uuid,
    pub events: Vec<PendingEvent>,
}

pub struct OutboxStore {
    pool: SqlitePool,
    sqlite_version: String,
}

impl OutboxStore {
    pub async fn open(path: &Path) -> Result<Self, StoreError> {
        if !path.is_absolute() || !path.parent().is_some_and(Path::is_dir) {
            return Err(StoreError::InvalidPath);
        }
        let opts = SqliteConnectOptions::new()
            .filename(path)
            .create_if_missing(true)
            .foreign_keys(true)
            .journal_mode(SqliteJournalMode::Wal)
            .synchronous(SqliteSynchronous::Full)
            .busy_timeout(Duration::from_secs(5));
        let pool = SqlitePoolOptions::new()
            .max_connections(1)
            .min_connections(1)
            .connect_with(opts)
            .await?;
        let version: String = sqlx::query_scalar("SELECT sqlite_version()")
            .fetch_one(&pool)
            .await?;
        if !is_patched_sqlite(&version) {
            pool.close().await;
            return Err(StoreError::UnsafeSqlite);
        }
        sqlx::migrate!("./migrations").run(&pool).await?;
        Ok(Self {
            pool,
            sqlite_version: version,
        })
    }

    pub fn sqlite_version(&self) -> &str {
        &self.sqlite_version
    }

    /// Atomically append one synthetic event and its cursor, or neither.
    pub async fn append_synthetic(&self, event: &SyntheticEvent) -> Result<i64, StoreError> {
        if event.policy_version < 1
            || !event.payload.is_object()
            || event.cursor.is_empty()
            || event.cursor.len() > 4096
            || !(20..=40).contains(&event.occurred_at_utc.len())
            || !event.occurred_at_utc.ends_with('Z')
        {
            return Err(StoreError::InvalidFixture);
        }
        let payload = serde_json::to_string(&event.payload)?;
        if payload.is_empty() || payload.len() > MAX_EVENT_BYTES {
            return Err(StoreError::InvalidFixture);
        }
        let digest = format!("{:x}", Sha256::digest(payload.as_bytes()));
        let mut tx = self.pool.begin().await?;
        let (count, bytes): (i64, i64) =
            sqlx::query_as("SELECT COUNT(*), COALESCE(SUM(bytes),0) FROM outbox_events")
                .fetch_one(&mut *tx)
                .await?;
        if count >= MAX_EVENTS || bytes + payload.len() as i64 > MAX_QUEUED_BYTES {
            sqlx::query(
                "INSERT INTO collection_gaps(source,reason) \
                SELECT 'synthetic_test','queue_full' \
                WHERE (SELECT COUNT(*) FROM collection_gaps) < ?",
            )
            .bind(MAX_GAPS)
            .execute(&mut *tx)
            .await?;
            tx.commit().await?;
            return Err(StoreError::QueueFull);
        }
        let sequence: i64 =
            sqlx::query_scalar("SELECT next_sequence FROM agent_state WHERE singleton=1")
                .fetch_one(&mut *tx)
                .await?;
        if sequence == i64::MAX {
            return Err(StoreError::QueueFull);
        }
        let inserted = sqlx::query("INSERT INTO outbox_events \
                (event_id,device_sequence,schema_version,source,event_type,policy_version,occurred_at_utc,payload_json,content_digest,bytes) \
                VALUES (?, ?, 1, 'synthetic_test', 'protocol_fixture', ?, ?, ?, ?, ?)")
            .bind(event.event_id.to_string())
            .bind(sequence)
            .bind(event.policy_version)
            .bind(&event.occurred_at_utc)
            .bind(&payload)
            .bind(digest)
            .bind(payload.len() as i64)
            .execute(&mut *tx).await;
        if let Err(error) = inserted {
            if matches!(&error, sqlx::Error::Database(db) if db.is_unique_violation()) {
                return Err(StoreError::Duplicate);
            }
            return Err(StoreError::Database(error));
        }
        sqlx::query("UPDATE agent_state SET next_sequence=next_sequence+1 WHERE singleton=1")
            .execute(&mut *tx)
            .await?;
        sqlx::query(
            "INSERT INTO collector_cursors(source,cursor_blob,last_event_sequence) \
                VALUES ('synthetic_test', ?, ?) ON CONFLICT(source) DO UPDATE SET \
                cursor_blob=excluded.cursor_blob, last_event_sequence=excluded.last_event_sequence",
        )
        .bind(&event.cursor)
        .bind(sequence)
        .execute(&mut *tx)
        .await?;
        tx.commit().await?;
        Ok(sequence)
    }

    pub async fn pending_batch(&self) -> Result<Vec<PendingEvent>, StoreError> {
        let rows = sqlx::query(
            "SELECT event_id,device_sequence,policy_version,occurred_at_utc,payload_json,content_digest \
                FROM outbox_events ORDER BY device_sequence LIMIT 100",
        )
        .fetch_all(&self.pool)
        .await?;
        let mut total = 0_usize;
        let mut result = Vec::with_capacity(rows.len());
        for row in rows {
            let payload: String = row.try_get("payload_json")?;
            total += payload.len();
            if total > 512 * 1024 {
                break;
            }
            let uuid: String = row.try_get("event_id")?;
            result.push(PendingEvent {
                event_id: Uuid::parse_str(&uuid).map_err(|_| StoreError::InvalidFixture)?,
                sequence: row.try_get("device_sequence")?,
                policy_version: row.try_get("policy_version")?,
                occurred_at_utc: row.try_get("occurred_at_utc")?,
                payload_json: payload,
                content_digest: row.try_get("content_digest")?,
            });
        }
        Ok(result)
    }

    /// Persists a stable batch UUID and the exact selected event fingerprint.
    /// Additional local events after a timeout cannot mutate that batch.
    /// This method owns one SQLite transaction on the SQLx single connection.
    pub async fn prepare_synthetic_batch(&self) -> Result<Option<PreparedBatch>, StoreError> {
        let mut tx = self.pool.begin().await?;
        let existing: Option<(String, i64, String)> = sqlx::query_as(
            "SELECT batch_id,last_sequence,content_fingerprint FROM upload_batches WHERE singleton=1",
        )
        .fetch_optional(&mut *tx)
        .await?;

        if let Some((old_id, cutoff, old_fingerprint)) = &existing {
            let rows = sqlx::query(
                "SELECT event_id,device_sequence,policy_version,occurred_at_utc,payload_json,content_digest,bytes \
                 FROM outbox_events WHERE device_sequence <= ? ORDER BY device_sequence LIMIT 100",
            )
            .bind(cutoff)
            .fetch_all(&mut *tx)
            .await?;
            let selected = parse_batch_rows(rows)?;
            if !selected.is_empty() && fingerprint(&selected) == *old_fingerprint {
                let id = Uuid::parse_str(old_id).map_err(|_| StoreError::InvalidFixture)?;
                tx.commit().await?;
                return Ok(Some(PreparedBatch {
                    batch_id: id,
                    events: selected,
                }));
            }
            // An explicit ACK or invalid/corrupt stale state invalidates this
            // planned batch. Do not pretend its older fingerprint was sent.
            sqlx::query("DELETE FROM upload_batches WHERE singleton=1")
                .execute(&mut *tx)
                .await?;
        }

        let rows = sqlx::query(
            "SELECT event_id,device_sequence,policy_version,occurred_at_utc,payload_json,content_digest,bytes \
             FROM outbox_events ORDER BY device_sequence LIMIT 100",
        )
        .fetch_all(&mut *tx)
        .await?;
        let selected = parse_batch_rows(rows)?;
        if selected.is_empty() {
            tx.commit().await?;
            return Ok(None);
        }
        let batch_id = Uuid::now_v7();
        let end = selected.last().ok_or(StoreError::InvalidFixture)?.sequence;
        let digest = fingerprint(&selected);
        sqlx::query(
            "INSERT INTO upload_batches(singleton,batch_id,last_sequence,content_fingerprint) \
             VALUES(1,?,?,?)",
        )
        .bind(batch_id.to_string())
        .bind(end)
        .bind(digest)
        .execute(&mut *tx)
        .await?;
        tx.commit().await?;
        Ok(Some(PreparedBatch {
            batch_id,
            events: selected,
        }))
    }

    /// An ambiguous HTTP timeout MUST NOT call acknowledge. Only explicitly
    /// confirmed sequences can be removed; never a high-watermark purge.
    pub async fn acknowledge(&self, sequences: &[i64]) -> Result<u64, StoreError> {
        if sequences.len() > 100 || sequences.iter().any(|s| *s <= 0) {
            return Err(StoreError::InvalidFixture);
        }
        let mut tx = self.pool.begin().await?;
        let mut count = 0_u64;
        for seq in sequences {
            count += sqlx::query("DELETE FROM outbox_events WHERE device_sequence=?")
                .bind(seq)
                .execute(&mut *tx)
                .await?
                .rows_affected();
        }
        if count > 0 {
            sqlx::query("DELETE FROM upload_batches WHERE singleton=1")
                .execute(&mut *tx)
                .await?;
        }
        tx.commit().await?;
        Ok(count)
    }

    pub async fn cursor(&self) -> Result<Option<(Vec<u8>, i64)>, StoreError> {
        Ok(sqlx::query_as(
            "SELECT cursor_blob,last_event_sequence FROM collector_cursors \
                WHERE source='synthetic_test'",
        )
        .fetch_optional(&self.pool)
        .await?)
    }

    pub async fn next_sequence(&self) -> Result<i64, StoreError> {
        Ok(
            sqlx::query_scalar("SELECT next_sequence FROM agent_state WHERE singleton=1")
                .fetch_one(&self.pool)
                .await?,
        )
    }

    pub async fn gap_count(&self) -> Result<i64, StoreError> {
        Ok(sqlx::query_scalar("SELECT COUNT(*) FROM collection_gaps")
            .fetch_one(&self.pool)
            .await?)
    }

    /// Local-only INV-01-C durable snapshot selection. Never reads the OS:
    /// callers must provide approved, normalized whitelist data explicitly.
    ///
    /// Content digest intentionally excludes observed_at, snapshot_id and
    /// inventory_revision; their volatility must not create false changes.
    /// Latest semantic digest survives ACK and process restarts. One SQLite
    /// transaction owns all state changes and the pending wire JSON.
    pub async fn stage_inventory_snapshot(
        &self,
        device_id: Uuid,
        observed_at: &str,
        raw: RawInventory,
    ) -> Result<InventoryStage, StoreError> {
        // Normalization is also the only allowed JSON field boundary. Never
        // accept arbitrary user-supplied JSON or identity from the raw data.
        let mut body =
            InventorySnapshot::from_raw(device_id, Uuid::now_v7(), 1, observed_at.to_owned(), raw)?;
        let semantic_json = serde_json::to_vec(&(
            &body.device_id,
            &body.hardware,
            &body.os,
            &body.interfaces,
            &body.volumes,
            &body.capabilities,
        ))?;
        let semantic_digest = format!("{:x}", Sha256::digest(&semantic_json));

        let mut tx = self.pool.begin().await?;
        // A pre-staged schedule checkpoint also pins this SQLite inventory
        // to exactly one device; never mix state across re-enrollment.
        let schedule_owner: Option<String> = sqlx::query_scalar(
            "SELECT device_id FROM inventory_schedule_checkpoint WHERE singleton=1",
        )
        .fetch_optional(&mut *tx)
        .await?;
        if schedule_owner.is_some_and(|owner| owner != device_id.to_string()) {
            return Err(StoreError::InventoryConflict);
        }
        let latest: Option<(String, String, i64, String)> = sqlx::query_as(
            "SELECT device_id,snapshot_id,inventory_revision,semantic_digest \
             FROM inventory_latest_state WHERE singleton=1",
        )
        .fetch_optional(&mut *tx)
        .await?;
        let pending: Option<(String, i64, String, String)> = sqlx::query_as(
            "SELECT snapshot_id,inventory_revision,body_json,content_digest \
             FROM inventory_pending WHERE singleton=1",
        )
        .fetch_optional(&mut *tx)
        .await?;

        if let Some((ref owner, ref previous_id, last_revision, ref digest)) = latest {
            if owner != &device_id.to_string() {
                return Err(StoreError::InventoryConflict);
            }
            let checked_pending = match pending {
                Some((id, revision, json, stored_digest)) => Some(verify_typed_pending(
                    device_id,
                    (previous_id, last_revision, digest),
                    (&id, revision, &json, &stored_digest),
                )?),
                None => None,
            };
            if digest == &semantic_digest {
                return match checked_pending {
                    Some(value) => Ok(InventoryStage::AlreadyPending(value)),
                    None => Ok(InventoryStage::Unchanged),
                };
            }
        } else if pending.is_some() {
            // A pre-V3 synthetic pending row has no device binding or
            // semantic state. Fail closed rather than silently deleting it.
            return Err(StoreError::InventoryConflict);
        }

        let revision: i64 =
            sqlx::query_scalar("SELECT next_inventory_revision FROM agent_state WHERE singleton=1")
                .fetch_one(&mut *tx)
                .await?;
        if revision < 1 || revision == i64::MAX {
            return Err(StoreError::QueueFull);
        }
        body.inventory_revision = revision as u64;
        let wire_json = serde_json::to_string(&body)?;
        if wire_json.len() > 128 * 1024 {
            return Err(StoreError::Inventory(
                crate::inventory::InventoryError::TooLarge,
            ));
        }
        let wire_digest = format!("{:x}", Sha256::digest(wire_json.as_bytes()));
        sqlx::query(
            "INSERT INTO inventory_pending \
             (singleton,snapshot_id,inventory_revision,body_json,content_digest) \
             VALUES(1,?,?,?,?) ON CONFLICT(singleton) DO UPDATE SET \
             snapshot_id=excluded.snapshot_id, \
             inventory_revision=excluded.inventory_revision, \
             body_json=excluded.body_json,content_digest=excluded.content_digest",
        )
        .bind(body.snapshot_id.to_string())
        .bind(revision)
        .bind(&wire_json)
        .bind(wire_digest)
        .execute(&mut *tx)
        .await?;
        sqlx::query(
            "INSERT INTO inventory_latest_state \
             (singleton,device_id,snapshot_id,inventory_revision,semantic_digest) \
             VALUES(1,?,?,?,?) ON CONFLICT(singleton) DO UPDATE SET \
             device_id=excluded.device_id,snapshot_id=excluded.snapshot_id, \
             inventory_revision=excluded.inventory_revision, \
             semantic_digest=excluded.semantic_digest",
        )
        .bind(device_id.to_string())
        .bind(body.snapshot_id.to_string())
        .bind(revision)
        .bind(semantic_digest)
        .execute(&mut *tx)
        .await?;
        let advanced = sqlx::query(
            "UPDATE agent_state SET next_inventory_revision=next_inventory_revision+1 \
             WHERE singleton=1 AND next_inventory_revision=?",
        )
        .bind(revision)
        .execute(&mut *tx)
        .await?
        .rows_affected();
        if advanced != 1 {
            return Err(StoreError::InventoryConflict);
        }
        tx.commit().await?;
        Ok(InventoryStage::Staged(PendingInventory {
            snapshot_id: body.snapshot_id,
            revision,
            body_json: wire_json,
        }))
    }

    /// Only this typed inventory accessor may feed the staged HTTPS client.
    /// The legacy synthetic inventory getter is NOT upload-safe.
    pub async fn verified_pending_inventory(
        &self,
        device_id: Uuid,
    ) -> Result<Option<PendingInventory>, StoreError> {
        let mut tx = self.pool.begin().await?;
        let pending = load_verified_pending(&mut tx, device_id).await?;
        tx.commit().await?;
        Ok(pending)
    }

    /// Only an independently authenticated server ACK may invoke this API.
    /// Timeout / 5xx / guessed revision is never a valid ACK. The old ACK
    /// cannot clear a newer pending revision or a different device identity.
    pub async fn acknowledge_inventory_snapshot(
        &self,
        device_id: Uuid,
        snapshot_id: Uuid,
        revision: i64,
    ) -> Result<bool, StoreError> {
        if device_id.is_nil() || snapshot_id.is_nil() || revision <= 0 {
            return Err(StoreError::InvalidFixture);
        }
        let mut tx = self.pool.begin().await?;
        let Some(pending) = load_verified_pending(&mut tx, device_id).await? else {
            return Ok(false);
        };
        if pending.snapshot_id != snapshot_id || pending.revision != revision {
            return Ok(false);
        }
        let affected = sqlx::query(
            "DELETE FROM inventory_pending \
             WHERE singleton=1 AND snapshot_id=? AND inventory_revision=?",
        )
        .bind(snapshot_id.to_string())
        .bind(revision)
        .execute(&mut *tx)
        .await?
        .rows_affected();
        if affected != 1 {
            return Err(StoreError::InventoryConflict);
        }
        tx.commit().await?;
        Ok(true)
    }

    /// Current semantic revision is intentionally retained after ACK.
    pub async fn latest_inventory_revision(&self) -> Result<Option<i64>, StoreError> {
        Ok(sqlx::query_scalar(
            "SELECT inventory_revision FROM inventory_latest_state WHERE singleton=1",
        )
        .fetch_optional(&self.pool)
        .await?)
    }

    /// Latest-only inventory has an independent monotonic revision.
    pub async fn stage_synthetic_inventory(
        &self,
        snapshot: Uuid,
        body: &serde_json::Value,
    ) -> Result<i64, StoreError> {
        if !body.is_object() {
            return Err(StoreError::InvalidFixture);
        }
        let json = serde_json::to_string(body)?;
        if json.len() > 65536 {
            return Err(StoreError::InvalidFixture);
        }
        let digest = format!("{:x}", Sha256::digest(json.as_bytes()));
        let mut tx = self.pool.begin().await?;
        let bound: i64 =
            sqlx::query_scalar("SELECT COUNT(*) FROM inventory_latest_state WHERE singleton=1")
                .fetch_one(&mut *tx)
                .await?;
        if bound != 0 {
            return Err(StoreError::InventoryConflict);
        }
        let rev: i64 =
            sqlx::query_scalar("SELECT next_inventory_revision FROM agent_state WHERE singleton=1")
                .fetch_one(&mut *tx)
                .await?;
        if rev == i64::MAX {
            return Err(StoreError::QueueFull);
        }
        sqlx::query(
            "INSERT INTO inventory_pending \
                (singleton,snapshot_id,inventory_revision,body_json,content_digest) \
                VALUES(1,?,?,?,?) ON CONFLICT(singleton) DO UPDATE SET \
                snapshot_id=excluded.snapshot_id,inventory_revision=excluded.inventory_revision, \
                body_json=excluded.body_json,content_digest=excluded.content_digest",
        )
        .bind(snapshot.to_string())
        .bind(rev)
        .bind(json)
        .bind(digest)
        .execute(&mut *tx)
        .await?;
        sqlx::query(
            "UPDATE agent_state SET next_inventory_revision=next_inventory_revision+1 \
                WHERE singleton=1",
        )
        .execute(&mut *tx)
        .await?;
        tx.commit().await?;
        Ok(rev)
    }

    pub async fn pending_inventory(&self) -> Result<Option<PendingInventory>, StoreError> {
        let row = sqlx::query(
            "SELECT snapshot_id,inventory_revision,body_json \
                FROM inventory_pending WHERE singleton=1",
        )
        .fetch_optional(&self.pool)
        .await?;
        match row {
            None => Ok(None),
            Some(r) => {
                let id: String = r.try_get("snapshot_id")?;
                Ok(Some(PendingInventory {
                    snapshot_id: Uuid::parse_str(&id).map_err(|_| StoreError::InvalidFixture)?,
                    revision: r.try_get("inventory_revision")?,
                    body_json: r.try_get("body_json")?,
                }))
            }
        }
    }

    pub async fn acknowledge_inventory(&self, revision: i64) -> Result<bool, StoreError> {
        if revision <= 0 {
            return Err(StoreError::InvalidFixture);
        }
        Ok(sqlx::query(
            "DELETE FROM inventory_pending WHERE inventory_revision=? \
                 AND NOT EXISTS(SELECT 1 FROM inventory_latest_state WHERE singleton=1)",
        )
        .bind(revision)
        .execute(&self.pool)
        .await?
        .rows_affected()
            == 1)
    }

    /// INV-01-F: capture a verified outbox and schedule checkpoint in ONE
    /// SQLite read transaction. An invalid pending row or checkpoint aborts
    /// recovery; never treat corruption as a fresh first start.
    pub async fn load_inventory_schedule_recovery(
        &self,
        device_id: Uuid,
    ) -> Result<InventoryScheduleRecovery, StoreError> {
        let mut tx = self.pool.begin().await?;
        let pending = load_verified_pending(&mut tx, device_id).await?;
        let row: Option<(String, String, i64)> = sqlx::query_as(
            "SELECT device_id,state_json,generation \
             FROM inventory_schedule_checkpoint WHERE singleton=1",
        )
        .fetch_optional(&mut *tx)
        .await?;
        let ever_saved: i64 =
            sqlx::query_scalar("SELECT inventory_schedule_seen FROM agent_state WHERE singleton=1")
                .fetch_one(&mut *tx)
                .await?;
        if !matches!((ever_saved, row.is_some()), (0, false) | (1, true)) {
            // Do not reset a lost identity pause/backoff to first-start state.
            return Err(StoreError::InventoryConflict);
        }
        let (checkpoint, generation) = match row {
            Some((owner, json, generation)) => (
                Some(parse_inventory_schedule_checkpoint(
                    device_id, &owner, &json, generation,
                )?),
                generation,
            ),
            None => (None, 0),
        };
        tx.commit().await?;
        Ok(InventoryScheduleRecovery {
            checkpoint,
            generation,
            pending,
        })
    }

    /// Compare-and-swap checkpoint update. Generation 0 means no row yet;
    /// each successful write increments it. Reject stale writers, device
    /// swaps, tampered rows and attempts to silently clear security halts.
    /// This does NOT update inventory or authorize any upload.
    pub async fn save_inventory_schedule_checkpoint(
        &self,
        checkpoint: &InventoryScheduleCheckpoint,
        expected_generation: i64,
    ) -> Result<i64, StoreError> {
        checkpoint
            .validate(checkpoint.device_id)
            .map_err(|_| StoreError::InventoryConflict)?;
        if !(0..i64::MAX - 1).contains(&expected_generation) {
            return Err(StoreError::InventoryConflict);
        }
        let json = serde_json::to_string(checkpoint)?;
        let mut tx = self.pool.begin().await?;
        // A single transaction protects all checked state and the CAS write.
        let _pending = load_verified_pending(&mut tx, checkpoint.device_id).await?;
        let current: Option<(String, String, i64)> = sqlx::query_as(
            "SELECT device_id,state_json,generation \
             FROM inventory_schedule_checkpoint WHERE singleton=1",
        )
        .fetch_optional(&mut *tx)
        .await?;
        let ever_saved: i64 =
            sqlx::query_scalar("SELECT inventory_schedule_seen FROM agent_state WHERE singleton=1")
                .fetch_one(&mut *tx)
                .await?;
        if !matches!((ever_saved, current.is_some()), (0, false) | (1, true)) {
            return Err(StoreError::InventoryConflict);
        }
        let current_generation = match current {
            Some((owner, state, generation)) => {
                let previous = parse_inventory_schedule_checkpoint(
                    checkpoint.device_id,
                    &owner,
                    &state,
                    generation,
                )?;
                if previous.halted.is_some() && previous.halted != checkpoint.halted {
                    return Err(StoreError::InventoryConflict);
                }
                generation
            }
            None => 0,
        };
        if current_generation != expected_generation {
            return Err(StoreError::InventoryConflict);
        }
        let new_generation = expected_generation + 1;
        let affected = sqlx::query(
            "INSERT INTO inventory_schedule_checkpoint \
             (singleton,device_id,state_json,generation) VALUES(1,?,?,1) \
             ON CONFLICT(singleton) DO UPDATE SET \
             state_json=excluded.state_json,generation=inventory_schedule_checkpoint.generation+1 \
             WHERE inventory_schedule_checkpoint.device_id=excluded.device_id \
             AND inventory_schedule_checkpoint.generation=?",
        )
        .bind(checkpoint.device_id.to_string())
        .bind(json)
        .bind(expected_generation)
        .execute(&mut *tx)
        .await?
        .rows_affected();
        if affected != 1 {
            return Err(StoreError::InventoryConflict);
        }
        let marker_written =
            sqlx::query("UPDATE agent_state SET inventory_schedule_seen=1 WHERE singleton=1")
                .execute(&mut *tx)
                .await?
                .rows_affected();
        if marker_written != 1 {
            return Err(StoreError::InventoryConflict);
        }
        tx.commit().await?;
        Ok(new_generation)
    }

    pub async fn settings(&self) -> Result<(String, i64, i64), StoreError> {
        let journal: String = sqlx::query_scalar("PRAGMA journal_mode")
            .fetch_one(&self.pool)
            .await?;
        let sync: i64 = sqlx::query_scalar("PRAGMA synchronous")
            .fetch_one(&self.pool)
            .await?;
        let keys: i64 = sqlx::query_scalar("PRAGMA foreign_keys")
            .fetch_one(&self.pool)
            .await?;
        Ok((journal, sync, keys))
    }

    pub async fn close(self) {
        self.pool.close().await;
    }
}

fn parse_inventory_schedule_checkpoint(
    expected_device: Uuid,
    owner: &str,
    json: &str,
    generation: i64,
) -> Result<InventoryScheduleCheckpoint, StoreError> {
    if owner != expected_device.to_string() || !(1..i64::MAX).contains(&generation) {
        return Err(StoreError::InventoryConflict);
    }
    let checkpoint: InventoryScheduleCheckpoint =
        serde_json::from_str(json).map_err(|_| StoreError::InventoryConflict)?;
    checkpoint
        .validate(expected_device)
        .map_err(|_| StoreError::InventoryConflict)?;
    Ok(checkpoint)
}

/// SHA-256 detects accidental corruption; it is not a cryptographic MAC.
/// OS SQLite file custody and server-authenticated transport remain separate.
async fn load_verified_pending(
    tx: &mut sqlx::Transaction<'_, sqlx::Sqlite>,
    device_id: Uuid,
) -> Result<Option<PendingInventory>, StoreError> {
    if device_id.is_nil() {
        return Err(StoreError::InventoryConflict);
    }
    let latest: Option<(String, String, i64, String)> = sqlx::query_as(
        "SELECT device_id,snapshot_id,inventory_revision,semantic_digest \
         FROM inventory_latest_state WHERE singleton=1",
    )
    .fetch_optional(&mut **tx)
    .await?;
    let pending: Option<(String, i64, String, String)> = sqlx::query_as(
        "SELECT snapshot_id,inventory_revision,body_json,content_digest \
         FROM inventory_pending WHERE singleton=1",
    )
    .fetch_optional(&mut **tx)
    .await?;
    match (latest, pending) {
        (None, None) => Ok(None),
        (None, Some(_)) => Err(StoreError::InventoryConflict),
        (Some((owner, _, _, _)), _) if owner != device_id.to_string() => {
            Err(StoreError::InventoryConflict)
        }
        (Some(_), None) => Ok(None),
        (
            Some((_, id, revision, semantic_digest)),
            Some((pending_id, pending_rev, body, digest)),
        ) => Ok(Some(verify_typed_pending(
            device_id,
            (&id, revision, &semantic_digest),
            (&pending_id, pending_rev, &body, &digest),
        )?)),
    }
}

fn verify_typed_pending(
    device_id: Uuid,
    latest: (&str, i64, &str),
    queued: (&str, i64, &str, &str),
) -> Result<PendingInventory, StoreError> {
    let (last_id, last_revision, semantic_digest) = latest;
    let (id, revision, body_json, stored_digest) = queued;
    if device_id.is_nil()
        || revision < 1
        || last_revision != revision
        || last_id != id
        || body_json.is_empty()
        || body_json.len() > 128 * 1024
        || stored_digest != format!("{:x}", Sha256::digest(body_json.as_bytes()))
    {
        return Err(StoreError::InventoryConflict);
    }
    let parsed: InventorySnapshot =
        serde_json::from_str(body_json).map_err(|_| StoreError::InventoryConflict)?;
    if parsed.schema_version != 1
        || parsed.device_id != device_id
        || parsed.snapshot_id.to_string() != id
        || parsed.inventory_revision != revision as u64
    {
        return Err(StoreError::InventoryConflict);
    }
    // Require canonical whitelist content, even if the stored SHA-256
    // itself was altered. Reject unapproved additional JSON fields.
    let normalized = InventorySnapshot::from_raw(
        parsed.device_id,
        parsed.snapshot_id,
        parsed.inventory_revision,
        parsed.observed_at.clone(),
        RawInventory {
            hardware: parsed.hardware.clone(),
            os: parsed.os.clone(),
            interfaces: parsed.interfaces.clone(),
            volumes: parsed.volumes.clone(),
            capabilities: parsed.capabilities.clone(),
        },
    )
    .map_err(|_| StoreError::InventoryConflict)?;
    if normalized != parsed {
        return Err(StoreError::InventoryConflict);
    }
    let semantics = serde_json::to_vec(&(
        &parsed.device_id,
        &parsed.hardware,
        &parsed.os,
        &parsed.interfaces,
        &parsed.volumes,
        &parsed.capabilities,
    ))
    .map_err(|_| StoreError::InventoryConflict)?;
    if semantic_digest != format!("{:x}", Sha256::digest(&semantics)) {
        return Err(StoreError::InventoryConflict);
    }
    Ok(PendingInventory {
        snapshot_id: parsed.snapshot_id,
        revision,
        body_json: body_json.to_owned(),
    })
}

fn parse_batch_rows(rows: Vec<sqlx::sqlite::SqliteRow>) -> Result<Vec<PendingEvent>, StoreError> {
    let mut bytes = 0_usize;
    let mut selected = Vec::with_capacity(rows.len());
    for row in rows {
        let length: i64 = row.try_get("bytes")?;
        // Reserve room for up to 100 JSON envelope headers; Reqwest's
        // complete serialized request must still fit the 512 KiB wire cap.
        if length < 1 || bytes + length as usize > 480 * 1024 {
            break;
        }
        bytes += length as usize;
        let uuid: String = row.try_get("event_id")?;
        selected.push(PendingEvent {
            event_id: Uuid::parse_str(&uuid).map_err(|_| StoreError::InvalidFixture)?,
            sequence: row.try_get("device_sequence")?,
            policy_version: row.try_get("policy_version")?,
            occurred_at_utc: row.try_get("occurred_at_utc")?,
            payload_json: row.try_get("payload_json")?,
            content_digest: row.try_get("content_digest")?,
        });
    }
    Ok(selected)
}

fn fingerprint(events: &[PendingEvent]) -> String {
    let mut hash = Sha256::new();
    for item in events {
        hash.update(item.sequence.to_be_bytes());
        hash.update(item.event_id.as_bytes());
        hash.update(item.content_digest.as_bytes());
    }
    format!("{:x}", hash.finalize())
}

fn is_patched_sqlite(version: &str) -> bool {
    let parts: Option<Vec<u32>> = version.split('.').map(|v| v.parse::<u32>().ok()).collect();
    let Some(parts) = parts else {
        return false;
    };
    if parts.len() != 3 {
        return false;
    }
    (parts[0], parts[1], parts[2]) >= (3, 51, 3) && parts[1] != 52
}

#[cfg(test)]
mod tests {
    use super::*;
    #[tokio::test]
    async fn failed_cursor_write_rolls_back_event_and_sequence() {
        let dir = tempfile::tempdir().unwrap();
        let store = OutboxStore::open(&dir.path().join("rollback.sqlite"))
            .await
            .unwrap();
        sqlx::query(
            "CREATE TRIGGER fail_cursor BEFORE INSERT ON collector_cursors \
             BEGIN SELECT RAISE(ABORT, 'synthetic cursor failure'); END",
        )
        .execute(&store.pool)
        .await
        .unwrap();
        let event = SyntheticEvent {
            event_id: Uuid::now_v7(),
            occurred_at_utc: "2026-10-31T00:00:00Z".into(),
            policy_version: 1,
            payload: serde_json::json!({"synthetic": true}),
            cursor: vec![1],
        };
        assert!(matches!(
            store.append_synthetic(&event).await,
            Err(StoreError::Database(_))
        ));
        assert_eq!(store.next_sequence().await.unwrap(), 1);
        assert!(store.pending_batch().await.unwrap().is_empty());
        assert!(store.cursor().await.unwrap().is_none());
        sqlx::query("DROP TRIGGER fail_cursor")
            .execute(&store.pool)
            .await
            .unwrap();
        assert_eq!(store.append_synthetic(&event).await.unwrap(), 1);
    }

    #[tokio::test]
    async fn full_queue_records_bounded_gap_without_advancing_cursor() {
        let dir = tempfile::tempdir().unwrap();
        let store = OutboxStore::open(&dir.path().join("queue.sqlite"))
            .await
            .unwrap();
        // Seed synthetic fixtures transactionally in one test-only statement;
        // no privileged disk-fill or network operation is performed.
        sqlx::query(
            "WITH RECURSIVE counter(n) AS ( \
             SELECT 1 UNION ALL SELECT n+1 FROM counter WHERE n < 2048) \
             INSERT INTO outbox_events(event_id,device_sequence,schema_version,source, \
             event_type,policy_version,occurred_at_utc,payload_json,content_digest,bytes) \
             SELECT printf('fixture-%d',n),n,1,'synthetic_test','protocol_fixture',1, \
             '2026-10-31T00:00:00Z','{}',lower(hex(zeroblob(32))),2 FROM counter",
        )
        .execute(&store.pool)
        .await
        .unwrap();
        let event = SyntheticEvent {
            event_id: Uuid::now_v7(),
            occurred_at_utc: "2026-10-31T00:00:00Z".into(),
            policy_version: 1,
            payload: serde_json::json!({"synthetic": true}),
            cursor: vec![1],
        };
        for _ in 0..3 {
            assert!(matches!(
                store.append_synthetic(&event).await,
                Err(StoreError::QueueFull)
            ));
        }
        assert_eq!(store.gap_count().await.unwrap(), 3);
        assert!(store.cursor().await.unwrap().is_none());
        assert_eq!(store.next_sequence().await.unwrap(), 1);
    }

    #[test]
    fn rejects_old_and_withdrawn_sqlite() {
        for v in ["3.50.4", "3.51.2", "3.52.0", "bad", "3.51"] {
            assert!(!is_patched_sqlite(v));
        }
        for v in ["3.51.3", "3.53.0", "3.54.1"] {
            assert!(is_patched_sqlite(v));
        }
    }
}

#[cfg(test)]
#[path = "store_inventory_tests.rs"]
mod inventory_store_tests;
