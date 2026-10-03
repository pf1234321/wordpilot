# Data Model: 存储层实体

**目的**：定义本节 4 个 JPA Entity + 1 个 ES doc 的字段、约束、关系。

## SysUser（MySQL `sys_user`）

| 字段 | 类型 | 约束 | 说明 |
|------|-----|------|------|
| id | BIGINT | PK, auto increment | 主键 |
| username | VARCHAR(64) | NOT NULL, UNIQUE | 登录账号，唯一 |
| password | VARCHAR(100) | NOT NULL | BCrypt 哈希（cost=10） |
| nickname | VARCHAR(64) | NULL | 昵称（可空） |
| createTime | DATETIME | NOT NULL | 创建时间，@CreatedDate |
| updateTime | DATETIME | NOT NULL | 更新时间，@LastModifiedDate |
| deleted | TINYINT(1) | NOT NULL, DEFAULT 0 | 逻辑删除标记 |

**JPA 注解**：
- `@Entity` + `@Table(name = "sys_user")`
- `@SQLDelete(sql = "UPDATE sys_user SET deleted=1 WHERE id=?")`
- `@Where(clause = "deleted=0")`
- `@EntityListeners(AuditEntityListener.class)`
- 实现 `Auditable`（writing-common）

## WritingMaterial（MySQL `writing_material`）

| 字段 | 类型 | 约束 | 说明 |
|------|-----|------|------|
| id | BIGINT | PK, auto increment | 主键 |
| userId | BIGINT | NOT NULL, INDEX | 归属用户（强制携带） |
| fileName | VARCHAR(255) | NOT NULL | 原始文件名 |
| fileType | VARCHAR(20) | NOT NULL | pdf / docx / txt / md |
| fileSize | BIGINT | NOT NULL | 文件字节数 |
| filePath | VARCHAR(500) | NOT NULL | 存储路径 |
| contentText | LONGTEXT | NULL | 解析后纯文本（预览用） |
| chunkCount | INT | NOT NULL, DEFAULT 0 | ES 切片数量 |
| createTime | DATETIME | NOT NULL | @CreatedDate |
| updateTime | DATETIME | NOT NULL | @LastModifiedDate |
| deleted | TINYINT(1) | NOT NULL, DEFAULT 0 | 逻辑删除标记 |

**索引**：`INDEX idx_user_create (user_id, create_time DESC)` —— 隐藏统一查"当前用户最新素材"

**JPA 注解**：同 SysUser

## WritingArticle（MySQL `writing_article`）

| 字段 | 类型 | 约束 | 说明 |
|------|-----|------|------|
| id | BIGINT | PK, auto increment | 主键 |
| userId | BIGINT | NOT NULL, INDEX | 归属用户（强制携带） |
| articleTitle | VARCHAR(255) | NULL | 稿件标题 |
| articleContent | LONGTEXT | NULL | 稿件正文 |
| writeType | VARCHAR(20) | NOT NULL | dialog / rag / template |
| materialId | BIGINT | NULL, INDEX | 关联素材（仿写模式；可空） |
| templateId | BIGINT | NULL | 关联模板（模板模式；可空） |
| createTime | DATETIME | NOT NULL | @CreatedDate |
| updateTime | DATETIME | NOT NULL | @LastModifiedDate |
| deleted | TINYINT(1) | NOT NULL, DEFAULT 0 | 逻辑删除标记 |

**索引**：`INDEX idx_user_create (user_id, create_time DESC)`

**JPA 注解**：同 SysUser

## WritingMemory（MySQL `writing_memory`）

| 字段 | 类型 | 约束 | 约束 |
|------|-----|------|------|
| id | BIGINT | PK, auto increment | 主键 |
| userId | BIGINT | NOT NULL, INDEX | 归属用户（强制携带） |
| memoryType | VARCHAR(20) | NOT NULL | style / term / habit / other |
| content | TEXT | NOT NULL | 一条用户偏好/事实 |
| source | VARCHAR(50) | NOT NULL | save_memory / session_compress |
| createTime | DATETIME | NOT NULL | @CreatedDate |
| updateTime | DATETIME | NOT NULL | @LastModifiedDate |
| deleted | TINYINT(1) | NOT NULL, DEFAULT 0 | 逻辑删除标记 |

**索引**：`INDEX idx_user_type (userId, memoryType)`

**JPA 注解**：同 SysUser

## MaterialChunk（ES `writing_material_chunk`）

| 字段 | ES type | 说明 |
|------|---------|------|
| id | long | 主键（= materialId_chunkIndex 拼接，或 ES auto） |
| materialId | long | 素材 id |
| userId | long | 用户 id（kNN must filter） |
| chunkIndex | integer | 切片序号 |
| chunkText | text | 切片内容 |
| embedding | dense_vector (dims=1024, similarity=cosine) | bge-m3 向量（FR-005）

## SessionMessage（Redis String）

```
{
  "role": "user" | "assistant",     // SessionRole enum
  "content": "string",              // 对话文本（不含 think/tool 中间态）
  "timestamp": "ISO-8601 UTC"       // 该条消息时间
}
```

**存储形态**：Redis String（JSON 序列化），key = `session:{userId}:{sessionId}`，TTL = 30 分钟。

## 关系

- `WritingMaterial.userId` → `SysUser.id`（逻辑外键，应用层维护，不加 FK 约束）
- `WritingArticle.userId` → `SysUser.id`
- `WritingArticle.materialId` → `WritingMaterial.id`
- `WritingArticle.templateId` → `Template.id`（本节不实现 Template，第 6 节）
- `WritingMemory.userId` → `SysUser.id`
- `MaterialChunk.materialId` → `WritingMaterial.id`
- `MaterialChunk.userId` → `SysUser.id`

> 全部为逻辑外键：constitution 决策六要求"不依赖 Hibernate ddl-auto=update 维护外键"，生产用 SQL 脚本显式建或不建 INDEX，本节只定义索引注解。