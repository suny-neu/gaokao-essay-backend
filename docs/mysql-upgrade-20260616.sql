-- Gaokao Essay Backend
-- MySQL upgrade script for 2026-06-16
--
-- Usage:
--   1. Backup your database first.
--   2. Run `USE your_database_name;`
--   3. Execute this script.
--   4. Restart the backend with GAOKAO_MYSQL_ENABLED=true.
--
-- This script is designed to be idempotent for the current formal schema.

SET @db_name = DATABASE();

-- 0) Guard: make sure a database is selected
SELECT IF(
  @db_name IS NULL OR @db_name = '',
  'ERROR: Please run USE <database_name> before executing this script.',
  CONCAT('Upgrading database: ', @db_name)
) AS upgrade_target;

-- 1) Core tables from current formal schema
CREATE TABLE IF NOT EXISTS user_binding (
    open_id VARCHAR(128) NOT NULL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    last_login_at TIMESTAMP NOT NULL,
    UNIQUE KEY uk_user_binding_user_id (user_id)
);

CREATE TABLE IF NOT EXISTS user_subscription (
    user_id VARCHAR(64) NOT NULL PRIMARY KEY,
    plan_code VARCHAR(32) NOT NULL,
    plan_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NULL,
    auto_renew BOOLEAN NOT NULL DEFAULT FALSE,
    provider VARCHAR(32) NOT NULL,
    provider_reference VARCHAR(128) NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS user_usage_quota (
    user_id VARCHAR(64) NOT NULL,
    quota_type VARCHAR(64) NOT NULL,
    used_count INT NOT NULL DEFAULT 0,
    limit_count INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (user_id, quota_type)
);

CREATE TABLE IF NOT EXISTS essay_record (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    open_id VARCHAR(128) NOT NULL,
    mode VARCHAR(32) NOT NULL,
    essay_type VARCHAR(32) NOT NULL,
    band VARCHAR(32) NOT NULL,
    band_label VARCHAR(32) NOT NULL,
    band_value VARCHAR(64) NOT NULL,
    created_at BIGINT NOT NULL,
    content LONGTEXT NOT NULL,
    word_count INT NOT NULL DEFAULT 0,
    score_text VARCHAR(128) NOT NULL,
    summary VARCHAR(512) NOT NULL,
    source VARCHAR(64) NOT NULL,
    task_status VARCHAR(32) NOT NULL,
    prompt_snapshot_json LONGTEXT NOT NULL,
    coach_plan_json LONGTEXT NULL,
    analysis_json LONGTEXT NULL,
    KEY idx_essay_record_user_created (user_id, created_at),
    KEY idx_essay_record_open_created (open_id, created_at)
);

CREATE TABLE IF NOT EXISTS coach_template (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    essay_type VARCHAR(32) NOT NULL,
    scenario VARCHAR(64) NOT NULL,
    task_purpose VARCHAR(64) NOT NULL,
    official_logic TEXT NOT NULL,
    opening_strategy TEXT NOT NULL,
    body_strategy TEXT NOT NULL,
    ending_strategy TEXT NOT NULL,
    must_include_json LONGTEXT NOT NULL,
    risk_points_json LONGTEXT NOT NULL,
    useful_expressions_json LONGTEXT NOT NULL,
    trigger_keywords_json LONGTEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL,
    KEY idx_coach_template_type_enabled (essay_type, enabled, sort_order)
);

CREATE TABLE IF NOT EXISTS payment_order (
    out_trade_no VARCHAR(64) NOT NULL PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    open_id VARCHAR(128) NOT NULL,
    plan_code VARCHAR(32) NOT NULL,
    plan_name VARCHAR(128) NOT NULL,
    amount_fen INT NOT NULL,
    currency VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    auto_renew BOOLEAN NOT NULL DEFAULT FALSE,
    description VARCHAR(255) NOT NULL,
    prepay_id VARCHAR(128) NULL,
    transaction_id VARCHAR(128) NULL,
    provider VARCHAR(32) NOT NULL,
    provider_reference VARCHAR(128) NOT NULL,
    payload_json LONGTEXT NULL,
    paid_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE KEY uk_payment_order_order_id (order_id),
    KEY idx_payment_order_user_created (user_id, created_at),
    KEY idx_payment_order_transaction (transaction_id)
);

-- 2) Patch old essay_record tables to current columns
SET @column_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = @db_name
    AND table_name = 'essay_record'
    AND column_name = 'coach_plan_json'
);
SET @sql = IF(
  @column_exists = 0,
  'ALTER TABLE essay_record ADD COLUMN coach_plan_json LONGTEXT NULL AFTER prompt_snapshot_json',
  'SELECT "essay_record.coach_plan_json already exists"'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = @db_name
    AND table_name = 'essay_record'
    AND column_name = 'analysis_json'
);
SET @sql = IF(
  @column_exists = 0,
  'ALTER TABLE essay_record ADD COLUMN analysis_json LONGTEXT NULL AFTER coach_plan_json',
  'SELECT "essay_record.analysis_json already exists"'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = @db_name
    AND table_name = 'essay_record'
    AND column_name = 'task_status'
);
SET @sql = IF(
  @column_exists = 0,
  'ALTER TABLE essay_record ADD COLUMN task_status VARCHAR(32) NOT NULL DEFAULT ''SUCCESS'' AFTER source',
  'SELECT "essay_record.task_status already exists"'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = @db_name
    AND table_name = 'essay_record'
    AND column_name = 'source'
);
SET @sql = IF(
  @column_exists = 0,
  'ALTER TABLE essay_record ADD COLUMN source VARCHAR(64) NOT NULL DEFAULT ''remote'' AFTER summary',
  'SELECT "essay_record.source already exists"'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = @db_name
    AND table_name = 'essay_record'
    AND column_name = 'band_value'
);
SET @sql = IF(
  @column_exists = 0,
  'ALTER TABLE essay_record ADD COLUMN band_value VARCHAR(64) NOT NULL DEFAULT '''' AFTER band_label',
  'SELECT "essay_record.band_value already exists"'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3) Backfill safe defaults for newly added columns
UPDATE essay_record
SET source = 'remote'
WHERE source IS NULL OR source = '';

UPDATE essay_record
SET task_status = 'SUCCESS'
WHERE task_status IS NULL OR task_status = '';

UPDATE essay_record
SET band_value = band_label
WHERE (band_value IS NULL OR band_value = '')
  AND band_label IS NOT NULL
  AND band_label <> '';

UPDATE essay_record
SET band_value = band
WHERE (band_value IS NULL OR band_value = '')
  AND band IS NOT NULL
  AND band <> '';

-- 4) Ensure important indexes exist
SET @index_exists = (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = @db_name
    AND table_name = 'essay_record'
    AND index_name = 'idx_essay_record_user_created'
);
SET @sql = IF(
  @index_exists = 0,
  'ALTER TABLE essay_record ADD KEY idx_essay_record_user_created (user_id, created_at)',
  'SELECT "idx_essay_record_user_created already exists"'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists = (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = @db_name
    AND table_name = 'essay_record'
    AND index_name = 'idx_essay_record_open_created'
);
SET @sql = IF(
  @index_exists = 0,
  'ALTER TABLE essay_record ADD KEY idx_essay_record_open_created (open_id, created_at)',
  'SELECT "idx_essay_record_open_created already exists"'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists = (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = @db_name
    AND table_name = 'coach_template'
    AND index_name = 'idx_coach_template_type_enabled'
);
SET @sql = IF(
  @index_exists = 0,
  'ALTER TABLE coach_template ADD KEY idx_coach_template_type_enabled (essay_type, enabled, sort_order)',
  'SELECT "idx_coach_template_type_enabled already exists"'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists = (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = @db_name
    AND table_name = 'payment_order'
    AND index_name = 'uk_payment_order_order_id'
);
SET @sql = IF(
  @index_exists = 0,
  'ALTER TABLE payment_order ADD UNIQUE KEY uk_payment_order_order_id (order_id)',
  'SELECT "uk_payment_order_order_id already exists"'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists = (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = @db_name
    AND table_name = 'payment_order'
    AND index_name = 'idx_payment_order_user_created'
);
SET @sql = IF(
  @index_exists = 0,
  'ALTER TABLE payment_order ADD KEY idx_payment_order_user_created (user_id, created_at)',
  'SELECT "idx_payment_order_user_created already exists"'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists = (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = @db_name
    AND table_name = 'payment_order'
    AND index_name = 'idx_payment_order_transaction'
);
SET @sql = IF(
  @index_exists = 0,
  'ALTER TABLE payment_order ADD KEY idx_payment_order_transaction (transaction_id)',
  'SELECT "idx_payment_order_transaction already exists"'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5) Quick verification
SELECT COUNT(*) AS essay_record_count FROM essay_record;
SELECT COUNT(*) AS coach_template_count FROM coach_template;

SELECT
  column_name,
  column_type,
  is_nullable
FROM information_schema.columns
WHERE table_schema = @db_name
  AND table_name = 'essay_record'
  AND column_name IN ('source', 'task_status', 'band_value', 'coach_plan_json', 'analysis_json')
ORDER BY ordinal_position;

-- Note:
-- coach_template seeds are bootstrapped by the backend itself.
-- After you restart the Spring Boot service with MySQL enabled,
-- the table should be auto-filled if it is still empty.
