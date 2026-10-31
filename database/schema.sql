-- 概念与逻辑设计对应 docs/05-领域模型.md 和 docs/06-数据库设计.md。
-- 本脚本只建结构，不创建真实用户或业务数据。
CREATE DATABASE IF NOT EXISTS `management-system`
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `management-system`;

CREATE TABLE IF NOT EXISTS role (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  code VARCHAR(20) NOT NULL,
  name VARCHAR(30) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_role_code UNIQUE (code),
  CONSTRAINT ck_role_code CHECK (code IN ('ADMIN', 'TEACHER', 'STUDENT'))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS app_user (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(80) NOT NULL,
  role_id BIGINT UNSIGNED NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  CONSTRAINT uk_app_user_username UNIQUE (username),
  KEY idx_app_user_role_status (role_id, status),
  CONSTRAINT fk_app_user_role FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE RESTRICT,
  CONSTRAINT ck_app_user_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS course (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  course_code VARCHAR(40) NOT NULL,
  name VARCHAR(120) NOT NULL,
  description VARCHAR(1000) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  CONSTRAINT uk_course_code UNIQUE (course_code),
  KEY idx_course_status_name (status, name),
  CONSTRAINT ck_course_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS ideological_element (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  description VARCHAR(1000) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  CONSTRAINT uk_element_name UNIQUE (name),
  KEY idx_element_status_name (status, name),
  CONSTRAINT ck_element_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS resource_category (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name VARCHAR(80) NOT NULL,
  description VARCHAR(1000) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  CONSTRAINT uk_category_name UNIQUE (name),
  KEY idx_category_status_name (status, name),
  CONSTRAINT ck_category_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS teaching_resource (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  title VARCHAR(200) NOT NULL,
  description VARCHAR(2000) NULL,
  course_id BIGINT UNSIGNED NOT NULL,
  category_id BIGINT UNSIGNED NOT NULL,
  created_by BIGINT UNSIGNED NOT NULL,
  file_storage_key VARCHAR(500) NOT NULL,
  file_original_name VARCHAR(255) NOT NULL,
  file_mime_type VARCHAR(120) NOT NULL,
  file_size_bytes BIGINT UNSIGNED NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  submission_no INT UNSIGNED NOT NULL DEFAULT 0,
  published_at DATETIME(3) NULL,
  withdrawn_at DATETIME(3) NULL,
  deleted_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_resource_owner_status (created_by, status, deleted_at),
  KEY idx_resource_public (status, deleted_at, course_id, category_id),
  KEY idx_resource_title (title),
  KEY idx_resource_created_at (created_at),
  KEY idx_resource_course (course_id),
  KEY idx_resource_category (category_id),
  CONSTRAINT fk_resource_course FOREIGN KEY (course_id) REFERENCES course(id) ON DELETE RESTRICT,
  CONSTRAINT fk_resource_category FOREIGN KEY (category_id) REFERENCES resource_category(id) ON DELETE RESTRICT,
  CONSTRAINT fk_resource_creator FOREIGN KEY (created_by) REFERENCES app_user(id) ON DELETE RESTRICT,
  CONSTRAINT ck_resource_status CHECK (status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED')),
  CONSTRAINT ck_resource_file_size CHECK (file_size_bytes > 0),
  CONSTRAINT ck_resource_withdrawn CHECK (withdrawn_at IS NULL OR status='APPROVED'),
  CONSTRAINT ck_resource_publication CHECK (
    (status = 'APPROVED' AND published_at IS NOT NULL) OR
    (status <> 'APPROVED' AND published_at IS NULL)
  )
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS resource_element_relation (
  resource_id BIGINT UNSIGNED NOT NULL,
  element_id BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (resource_id, element_id),
  KEY idx_relation_element_resource (element_id, resource_id),
  CONSTRAINT fk_relation_resource FOREIGN KEY (resource_id) REFERENCES teaching_resource(id) ON DELETE RESTRICT,
  CONSTRAINT fk_relation_element FOREIGN KEY (element_id) REFERENCES ideological_element(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS audit_record (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  resource_id BIGINT UNSIGNED NOT NULL,
  submission_no INT UNSIGNED NOT NULL,
  reviewer_id BIGINT UNSIGNED NOT NULL,
  from_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  decision VARCHAR(16) NOT NULL,
  reason VARCHAR(1000) NULL,
  audited_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  CONSTRAINT uk_audit_round UNIQUE (resource_id, submission_no),
  KEY idx_audit_reviewer_time (reviewer_id, audited_at),
  KEY idx_audit_resource_time (resource_id, audited_at),
  CONSTRAINT fk_audit_resource FOREIGN KEY (resource_id) REFERENCES teaching_resource(id) ON DELETE RESTRICT,
  CONSTRAINT fk_audit_reviewer FOREIGN KEY (reviewer_id) REFERENCES app_user(id) ON DELETE RESTRICT,
  CONSTRAINT ck_audit_round CHECK (submission_no > 0),
  CONSTRAINT ck_audit_from_status CHECK (from_status = 'PENDING'),
  CONSTRAINT ck_audit_decision CHECK (decision IN ('APPROVE', 'REJECT')),
  CONSTRAINT ck_audit_reason CHECK (
    (decision = 'REJECT' AND reason IS NOT NULL AND CHAR_LENGTH(TRIM(reason)) > 0) OR
    (decision = 'APPROVE' AND reason IS NULL)
  )
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS favorite_folder (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT UNSIGNED NOT NULL, name VARCHAR(80) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  CONSTRAINT uk_folder_user_name UNIQUE(user_id,name),
  CONSTRAINT fk_folder_user FOREIGN KEY(user_id) REFERENCES app_user(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS resource_metadata (
  resource_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
  original_author VARCHAR(120) NULL, source_note VARCHAR(1000) NULL, usage_rights VARCHAR(1000) NULL,
  cover_key VARCHAR(80) NULL, cover_size INT UNSIGNED NOT NULL DEFAULT 0,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_metadata_resource FOREIGN KEY(resource_id) REFERENCES teaching_resource(id) ON DELETE RESTRICT,
  CONSTRAINT ck_metadata_cover CHECK ((cover_key IS NULL AND cover_size=0) OR (cover_key IS NOT NULL AND cover_size>0))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS favorite (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  resource_id BIGINT UNSIGNED NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  folder_id BIGINT UNSIGNED NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  CONSTRAINT uk_favorite_user_resource UNIQUE (user_id, resource_id),
  KEY idx_favorite_resource_active (resource_id, active),
  KEY idx_favorite_folder (folder_id),
  CONSTRAINT fk_favorite_folder FOREIGN KEY(folder_id) REFERENCES favorite_folder(id) ON DELETE SET NULL,
  CONSTRAINT fk_favorite_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE RESTRICT,
  CONSTRAINT fk_favorite_resource FOREIGN KEY (resource_id) REFERENCES teaching_resource(id) ON DELETE RESTRICT,
  CONSTRAINT ck_favorite_active CHECK (active IN (0, 1))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS publication_event (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,resource_id BIGINT UNSIGNED NOT NULL,
  actor_id BIGINT UNSIGNED NOT NULL,action VARCHAR(16) NOT NULL,reason VARCHAR(1000) NULL,
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

CREATE TABLE IF NOT EXISTS browse_record (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  resource_id BIGINT UNSIGNED NOT NULL,
  browsed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_browse_user_time (user_id, browsed_at),
  KEY idx_browse_resource_time (resource_id, browsed_at),
  CONSTRAINT fk_browse_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE RESTRICT,
  CONSTRAINT fk_browse_resource FOREIGN KEY (resource_id) REFERENCES teaching_resource(id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS download_record (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  resource_id BIGINT UNSIGNED NOT NULL,
  downloaded_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_download_user_time (user_id, downloaded_at),
  KEY idx_download_resource_time (resource_id, downloaded_at),
  CONSTRAINT fk_download_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE RESTRICT,
  CONSTRAINT fk_download_resource FOREIGN KEY (resource_id) REFERENCES teaching_resource(id) ON DELETE RESTRICT
) ENGINE=InnoDB;
