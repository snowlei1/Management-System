-- Publication visibility is separate from the immutable audit result.
USE `management-system`;
SET @migration_sql=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='teaching_resource' AND column_name='withdrawn_at')=0,
 'ALTER TABLE teaching_resource ADD COLUMN withdrawn_at DATETIME(3) NULL, ADD CONSTRAINT ck_resource_withdrawn CHECK (withdrawn_at IS NULL OR status=''APPROVED'')','SELECT 1');
PREPARE migration_statement FROM @migration_sql;EXECUTE migration_statement;DEALLOCATE PREPARE migration_statement;
CREATE TABLE IF NOT EXISTS publication_event (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY, resource_id BIGINT UNSIGNED NOT NULL,
 actor_id BIGINT UNSIGNED NOT NULL, action VARCHAR(16) NOT NULL, reason VARCHAR(1000) NULL,
 occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 KEY idx_publication_resource(resource_id,id),
 CONSTRAINT fk_publication_resource FOREIGN KEY(resource_id) REFERENCES teaching_resource(id) ON DELETE RESTRICT,
 CONSTRAINT fk_publication_actor FOREIGN KEY(actor_id) REFERENCES app_user(id) ON DELETE RESTRICT,
 CONSTRAINT ck_publication_action CHECK(action IN ('WITHDRAW','RESTORE')),
 CONSTRAINT ck_publication_reason CHECK(action<>'WITHDRAW' OR (reason IS NOT NULL AND CHAR_LENGTH(TRIM(reason))>0))
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS resource_feedback (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,resource_id BIGINT UNSIGNED NOT NULL,user_id BIGINT UNSIGNED NOT NULL,
 kind VARCHAR(20) NOT NULL,description VARCHAR(1000) NOT NULL,status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
 response VARCHAR(1000) NULL,handled_by BIGINT UNSIGNED NULL,handled_at DATETIME(3) NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 KEY idx_feedback_state(status,created_at),KEY idx_feedback_user(user_id,id),KEY idx_feedback_resource(resource_id),
 CONSTRAINT fk_feedback_resource FOREIGN KEY(resource_id) REFERENCES teaching_resource(id) ON DELETE RESTRICT,
 CONSTRAINT fk_feedback_user FOREIGN KEY(user_id) REFERENCES app_user(id) ON DELETE RESTRICT,
 CONSTRAINT fk_feedback_handler FOREIGN KEY(handled_by) REFERENCES app_user(id) ON DELETE RESTRICT,
 CONSTRAINT ck_feedback_kind CHECK(kind IN ('FILE','CONTENT','CLASSIFICATION','RIGHTS','OTHER')),
 CONSTRAINT ck_feedback_state CHECK(status IN ('OPEN','RESOLVED','DISMISSED')),
 CONSTRAINT ck_feedback_result CHECK((status='OPEN' AND handled_by IS NULL AND handled_at IS NULL AND response IS NULL) OR (status<>'OPEN' AND handled_by IS NOT NULL AND handled_at IS NOT NULL AND response IS NOT NULL))
) ENGINE=InnoDB;
