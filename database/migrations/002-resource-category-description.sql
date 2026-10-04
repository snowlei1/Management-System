-- 第二阶段增量迁移；可重复执行，不重建或删除已有数据。
USE `management-system`;
SET @category_description_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'resource_category' AND COLUMN_NAME = 'description'
);
SET @category_description_sql = IF(@category_description_exists = 0,
  'ALTER TABLE resource_category ADD COLUMN description VARCHAR(1000) NULL AFTER name',
  'SELECT ''resource_category.description already exists'' AS migration_result');
PREPARE category_description_stmt FROM @category_description_sql;
EXECUTE category_description_stmt;
DEALLOCATE PREPARE category_description_stmt;
