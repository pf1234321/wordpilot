-- ============================================================================
-- ScriptAgent (wordpilot) 初始用户数据
-- 1 个管理员 + 3 个普通用户（占位测试用户，上线前请修改密码）
-- 密码说明: admin = admin123 ; user01/user02/user03 = 123456
-- 哈希均由项目同一 jbcrypt 库生成 (cost=10)，登录可直接通过
-- ============================================================================
-- 强制会话字符集为 utf8mb4：防止执行端默认 latin1 导致中文二次错误编码(mojibake)
SET NAMES utf8mb4;
USE wordpilot;

-- 注意: Hibernate 自动建表的 create_time/deleted 无默认值，原生 INSERT 需显式提供
INSERT INTO sys_user (username, password, nickname, create_time, deleted) VALUES
('admin',  '$2a$10$xvb7euHZ.RDuBNW0LH5PYeyEGjqyVMxYzRWIv9OShFFuymV43O/BW', '系统管理员', NOW(6), 0),
('user01', '$2a$10$F.eVlzNbVUHKsgyvDxriFuE2MnYarOPZ6NoM.oizAd2g.QWNGCvku', '测试用户一', NOW(6), 0),
('user02', '$2a$10$F.eVlzNbVUHKsgyvDxriFuE2MnYarOPZ6NoM.oizAd2g.QWNGCvku', '测试用户二', NOW(6), 0),
('user03', '$2a$10$F.eVlzNbVUHKsgyvDxriFuE2MnYarOPZ6NoM.oizAd2g.QWNGCvku', '测试用户三', NOW(6), 0);
