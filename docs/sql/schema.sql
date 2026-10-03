-- ============================================================================
-- ScriptAgent (wordpilot) 数据库表结构
-- 数据库: wordpilot    字符集: utf8mb4    适用: MySQL 8.x
-- 依据: docs/DemandAnalysis.md §9 数据模型 + 代码审计约束(@CreatedDate/@SQLDelete)
-- 说明: 开发环境 ddl-auto=update 可自动建表；生产环境手动执行本脚本 (ddl-auto=none)
-- ============================================================================

SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 1. sys_user 用户表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键，自增',
    username    VARCHAR(50)     NOT NULL                COMMENT '账号，唯一，用于登录',
    password    VARCHAR(100)    NOT NULL                COMMENT 'BCrypt 加密后的密码',
    nickname    VARCHAR(50)     NULL                    COMMENT '用户昵称',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(JPA审计自动填充)',
    update_time DATETIME        NULL ON UPDATE CURRENT_TIMESTAMP   COMMENT '更新时间',
    deleted     TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0未删 1已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户表';

-- ----------------------------------------------------------------------------
-- 2. writing_material 素材主表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS writing_material (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键，自增',
    user_id      BIGINT UNSIGNED NOT NULL                COMMENT '归属用户，强制携带(数据隔离)',
    file_name    VARCHAR(255)    NULL                    COMMENT '原始文件名',
    file_type    VARCHAR(20)     NULL                    COMMENT '文件类型: pdf/docx/txt/md',
    file_size    BIGINT          NULL                    COMMENT '文件大小(字节)',
    file_path    VARCHAR(500)    NULL                    COMMENT '存储路径',
    content_text LONGTEXT        NULL                    COMMENT '解析后的纯文本(用于预览)',
    chunk_count  INT             NOT NULL DEFAULT 0      COMMENT '切片数量',
    create_time  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(审计自动填充)',
    update_time  DATETIME        NULL ON UPDATE CURRENT_TIMESTAMP   COMMENT '更新时间',
    deleted      TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0未删 1已删',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_user_deleted (user_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='素材主表';

-- ----------------------------------------------------------------------------
-- 3. writing_article 稿件历史表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS writing_article (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键，自增',
    user_id         BIGINT UNSIGNED NOT NULL                COMMENT '归属用户，强制携带(数据隔离)',
    article_title   VARCHAR(255)    NULL                    COMMENT '稿件标题',
    article_content LONGTEXT        NULL                    COMMENT '稿件正文',
    write_type      VARCHAR(20)     NULL                    COMMENT '写作类型: dialog/rag/template',
    material_id     BIGINT UNSIGNED NULL                    COMMENT '关联素材(RAG写作时)',
    template_id     BIGINT UNSIGNED NULL                    COMMENT '关联模板(模板写作时)',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(审计自动填充)',
    update_time     DATETIME        NULL ON UPDATE CURRENT_TIMESTAMP   COMMENT '更新时间',
    deleted         TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0未删 1已删',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_write_type (write_type),
    KEY idx_user_deleted (user_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='稿件历史表';

-- ----------------------------------------------------------------------------
-- 4. writing_memory 长期记忆表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS writing_memory (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键，自增',
    user_id     BIGINT UNSIGNED NOT NULL                COMMENT '归属用户，强制携带(数据隔离)',
    memory_type VARCHAR(20)     NULL                    COMMENT '记忆类型: style/term/habit/other',
    content     TEXT            NULL                    COMMENT '记忆内容(一条用户偏好/事实)',
    source      VARCHAR(50)     NULL                    COMMENT '来源: save_memory工具/会话压缩抽取',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(审计自动填充)',
    update_time DATETIME        NULL ON UPDATE CURRENT_TIMESTAMP   COMMENT '更新时间',
    deleted     TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0未删 1已删',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_user_deleted (user_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='长期记忆表';

-- ----------------------------------------------------------------------------
-- 初始数据: admin 用户
-- 密码: admin123 (BCrypt, cost=10, 由项目同一 jbcrypt 库生成，登录可直接通过)
-- 上线前务必修改该默认密码
-- ----------------------------------------------------------------------------
INSERT INTO sys_user (username, password, nickname) VALUES
('admin', '$2a$10$xvb7euHZ.RDuBNW0LH5PYeyEGjqyVMxYzRWIv9OShFFuymV43O/BW', '系统管理员');
