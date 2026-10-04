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

CREATE TABLE IF NOT EXISTS favorite (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  resource_id BIGINT UNSIGNED NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  CONSTRAINT uk_favorite_user_resource UNIQUE (user_id, resource_id),
  KEY idx_favorite_resource_active (resource_id, active),
  CONSTRAINT fk_favorite_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE RESTRICT,
  CONSTRAINT fk_favorite_resource FOREIGN KEY (resource_id) REFERENCES teaching_resource(id) ON DELETE RESTRICT,
  CONSTRAINT ck_favorite_active CHECK (active IN (0, 1))
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
