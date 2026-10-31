-- INV-01-F: CAS generation and persistent checkpoint-existed marker.
-- An absent checkpoint after one was saved must NOT reset an identity pause.
-- V4 checkpoints are migrated to generation 1; no collection is enabled.
ALTER TABLE inventory_schedule_checkpoint
    ADD COLUMN generation INTEGER NOT NULL DEFAULT 1
    CHECK(generation > 0 AND generation < 9223372036854775807);

ALTER TABLE agent_state
    ADD COLUMN inventory_schedule_seen INTEGER NOT NULL DEFAULT 0
    CHECK(inventory_schedule_seen IN (0, 1));

UPDATE agent_state SET inventory_schedule_seen=1
WHERE singleton=1 AND EXISTS (
    SELECT 1 FROM inventory_schedule_checkpoint WHERE singleton=1
);
