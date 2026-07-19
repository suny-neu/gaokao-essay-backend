-- Gaokao Essay Backend
-- PostgreSQL upgrade script for 2026-06-25
--
-- Goal:
--   Add client_request_id for idempotent essay submission,
--   so repeated submits do not double-charge quota or duplicate records.

ALTER TABLE essay_record
    ADD COLUMN IF NOT EXISTS client_request_id VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uk_essay_record_user_request
    ON essay_record (user_id, client_request_id);
