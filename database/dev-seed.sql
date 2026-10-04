-- 仅用于本地毕业设计开发和测试；不得在正式环境执行。
-- 密码为 BCrypt(12) 散列，不在数据库中保存明文。
SET NAMES utf8mb4;
USE `management-system`;

INSERT INTO role (code, name) VALUES
  ('ADMIN', '管理员'), ('TEACHER', '教师'), ('STUDENT', '学生')
ON DUPLICATE KEY UPDATE name = name;

INSERT INTO app_user (username, password_hash, display_name, role_id)
SELECT 'dev_admin', '$2a$12$HlDVPiQ1Ht8EgDzppG49xeylPLMs92xTM6QN.lGc1eTbunNZ00Zp2',
       '本地测试管理员', id FROM role WHERE code='ADMIN'
  AND NOT EXISTS (SELECT 1 FROM app_user WHERE username='dev_admin');

INSERT INTO app_user (username, password_hash, display_name, role_id)
SELECT 'dev_teacher', '$2a$12$zDYHhqQ6Pdbi/Fp/nt5cfeU5tGMWSoaA1jSA6JlmI07EoFPh.oiv2',
       '本地测试教师', id FROM role WHERE code='TEACHER'
  AND NOT EXISTS (SELECT 1 FROM app_user WHERE username='dev_teacher');

INSERT INTO app_user (username, password_hash, display_name, role_id)
SELECT 'dev_student', '$2a$12$ouxuRjip9Xdn3kdJsbUiaObwzC/w3TRU9Za0vHP93irJTpl6zfljG',
       '本地测试学生', id FROM role WHERE code='STUDENT'
  AND NOT EXISTS (SELECT 1 FROM app_user WHERE username='dev_student');
