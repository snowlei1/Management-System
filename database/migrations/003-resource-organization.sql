-- Additive, repeatable local migration; retains all existing resources and history.
USE `management-system`;
CREATE TABLE IF NOT EXISTS favorite_folder (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT UNSIGNED NOT NULL, name VARCHAR(80) NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 CONSTRAINT uk_folder_user_name UNIQUE(user_id,name),
 CONSTRAINT fk_folder_user FOREIGN KEY(user_id) REFERENCES app_user(id) ON DELETE RESTRICT
) ENGINE=InnoDB;
SET @migration_sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='favorite' AND column_name='folder_id')=0,
 'ALTER TABLE favorite ADD COLUMN folder_id BIGINT UNSIGNED NULL, ADD KEY idx_favorite_folder(folder_id), ADD CONSTRAINT fk_favorite_folder FOREIGN KEY(folder_id) REFERENCES favorite_folder(id) ON DELETE SET NULL', 'SELECT 1');
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;
CREATE TABLE IF NOT EXISTS resource_metadata (
 resource_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
 original_author VARCHAR(120) NULL, source_note VARCHAR(1000) NULL, usage_rights VARCHAR(1000) NULL,
 cover_key VARCHAR(80) NULL, cover_size INT UNSIGNED NOT NULL DEFAULT 0,
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 CONSTRAINT fk_metadata_resource FOREIGN KEY(resource_id) REFERENCES teaching_resource(id) ON DELETE RESTRICT,
 CONSTRAINT ck_metadata_cover CHECK ((cover_key IS NULL AND cover_size=0) OR (cover_key IS NOT NULL AND cover_size>0))
) ENGINE=InnoDB;
