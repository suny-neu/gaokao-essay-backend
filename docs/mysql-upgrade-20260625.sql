-- Gaokao Essay Backend
-- MySQL upgrade script for 2026-06-25
--
-- Goal:
--   Add client_request_id for idempotent essay submission,
--   so repeated submits do not double-charge quota or duplicate records.

SET @db_name = DATABASE();

SELECT IF(
  @db_name IS NULL OR @db_name = '',
  'ERROR: Please run USE <database_name> before executing this script.',
  CONCAT('Upgrading database: ', @db_name)
) AS upgrade_target;

SET @column_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = @db_name
    AND table_name = 'essay_record'
    AND column_name = 'client_request_id'
);
SET @sql = IF(
  @column_exists = 0,
  'ALTER TABLE essay_record ADD COLUMN client_request_id VARCHAR(64) NULL AFTER id',
  'SELECT "essay_record.client_request_id already exists"'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists = (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = @db_name
    AND table_name = 'essay_record'
    AND index_name = 'uk_essay_record_user_request'
);
SET @sql = IF(
  @index_exists = 0,
  'CREATE UNIQUE INDEX uk_essay_record_user_request ON essay_record (user_id, client_request_id)',
  'SELECT "uk_essay_record_user_request already exists"'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
