# Data Model: 三类写作编排（008）

Phase 1 输出。本节**不新增任何数据表 / ES 索引 / Redis key**，全部复用前序节既有存储：

## 持久化数据（复用，不改结构）

| 实体 | 存储 | 来源节 | 本节使用 |
|------|------|--------|----------|
| `WritingArticle`（`writing_article`） | MySQL（JPA） | 第 1 节 | 稿件历史：`save`/分页/重新编辑/导出 |
| `TemplateDoc`（`writing_template`） | ES | 第 6 节 | 模板写作编排：读模板 + 校验 `status` |
| Redis `session:{userId}:{sessionId}` | Redis | 第 1 节 | 短期会话（Agent 内写，编排不碰） |
| Redis `sse:cache:{userId}:{sessionId}` | Redis | 第 1 节常量 | **本节新增读写**：SSE 断线缓存（复用 `RedisKeys.sseCache`） |

**writing_article 关键字段**（已由第 1 节交付，本节约束在 Service 层执行）：
- `user_id` BIGINT NOT NULL —— 归属用户，强制携带（宪法五）
- `article_title` VARCHAR(255)、`article_content` LONGTEXT
- `write_type` VARCHAR(20) —— `dialog` / `rag` / `template`
- `material_id` / `template_id` BIGINT（可空）
- 逻辑删除 `@SQLDelete` + `@Where`，审计 `create_time`/`update_time`

## 请求 / 响应 DTO（新，writing-api/dto）

| DTO | 字段 | 校验约束 |
|-----|------|----------|
| `DialogRequest` | `content` String；`sessionId` String(可空) | `content` @NotBlank；user_id 不在请求体 |
| `RagRequest` | `requirement` String；`sessionId` String(可空) | `requirement` @NotBlank |
| `TemplateRequest` | `templateId` String；`params` Map<String,Object>；`sessionId` String(可空) | `templateId` @NotBlank、`params` @NotNull |
| `ReeditResponse` | `id`、`articleTitle`、`articleContent`、`writeType`、`materialId`、`templateId`、`createTime` | 由 `WritingArticle` 映射（重新编辑上下文） |

> user_id 不来自任何请求体字段；一律 `UserContext.require()`（H4 不变量①）。

## 状态 / 规则（编排层约束）

- **写作文式**：`dialog` / `rag` / `template`，路由分支据此分发，未知值拒绝（FR-002）。
- **模板启停**：`status != "enabled"` 拒绝用于新写作（FR-008），历史稿件不受影响。
- **稿件隔离**：详情 / 重新编辑 / 导出 MUST 同时校验 `id` + `user_id`（FR-011），越权一律按"不存在"处理。
- **SSE 缓存生命周期**：生成过程逐段追加写 `sse:cache:{userId}:{sessionId}`；完成 / 失败后前端取回；断线重连从缓存恢复（FR-004）。
