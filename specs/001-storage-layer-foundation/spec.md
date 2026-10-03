# Feature Specification: 存储层统一封装

**Feature Branch**: `lesson1-storage-layer`
**Created**: 2026-10-03
**Status**: Draft
**Input**: User description: 第1节需求：存储层（writing-storage）—— MySQL / Redis / ES7 三层存储统一封装，是 user_id 隔离的落点

## User Scenarios & Testing

### User Story 1 — 业务 Service 通过 Repository 拿到四张表的受控访问（P1）

业务开发者要写一个"取当前用户最近 10 条稿件"的逻辑：他调用 `WritingArticleRepository.findByUser(userId, pageable)`，传入的 userId 必须来自 `UserContext`（登录拦截器注入）。系统返回的列表保证只包含当前用户的稿件——其他人即使乱填 userId 也无法越过隔离。

**Why this priority**：user_id 隔离是 ScriptAgent 整套设计的物理保障，跨用户数据泄露是 P0 安全事件；本节是上层所有业务 Service 的底座，必须先稳固。

**Independent Test**：构造 userA、userB 各一条记忆，调用 `findByUser` 各自只回自己的一条；尝试调用不带 userId 条件的接口必须编译失败或运行时报错。

**Acceptance Scenarios**：

1. **Given** 数据库有 userA 的两条 WritingMemory 与 userB 的一条，**When** 以 userA 身份调 `findByUser(userA)`，**Then** 列表 size = 2，全部属于 userA；调 `findByUser(userB)` 只回 userB 那 1 条。
2. **Given** 数据库有 1 条 deleted=1 的 WritingMemory，**When** 调 `findByUser`（任意 userId），**Then** 列表中不包含已删记录。
4. **Given** 数据库有 userA 的 5 条 WritingArticle，**When** 以 userA 身份按时间倒序调分页查询 `page=0, size=2`，**Then** 拿到 2 条，全属于 userA，顺序正确；翻到第 3 页只剩 1 条。

---

### User Story 2 — 删除走逻辑删除，物理行不丢；ES 切片按 material_id 同步清理（P1）

用户删除一条素材 / 稿件：调用 `materialRepository.deleteById(id)` 在 MySQL 端只是把 `deleted` 置 1，物理行仍在；同一笔删除需要把 ES `writing_material_chunk` 索引里 `material_id = X` 的切片清空（不留数据孤儿）。后续任何业务查询（受 `@Where` 过滤）都看不到这条素材，但审计 / 恢复仍能拿到物理行。

**Why this priority**：用户数据完整性 + 审计可恢复是 constitution 决策六；ES 切片不清理会让 RAG 检索召回已删内容，破坏用户预期。

**Independent Test**：删除素材，调 `findByUser` 应为空，但 raw SQL `SELECT * WHERE deleted=1` 仍能查到行；ES 端 `match query material_id=X` 应返回 0 条。

**Acceptance Scenarios**：

1. **Given** userA 有 1 条 WritingMaterial（含 3 条 ES 切片），**When** `materialService.delete(userA, materialId)`，**Then** `findByUser(userA)` 返回空，ES 按 `material_id=materialId` 查询 0 条；MySQL `SELECT * FROM writing_material WHERE id=materialId` 仍能查到行（deleted=1）。
2. **Given** 同一素材，**When** 在 attempts 状态多次 `delete`，**Then** 多次只更新 `deleted=1`，不重复发 ES 清理（幂等）。

---

### User Story 3 — Redis 短期会话只存对话层，含 TTL（P2）

AgentCore 需要把用户的会话塞进 Redis（短期记忆）：调用 `RedisMemoryStore.append(userId, sessionId, role, content)` 追加消息，仅接受 role 为 `user` / `assistant` 的对话层消息，role 为 `think` / `tool` 等中间态一律拒绝写入；`loadRecent` 取最近 N 轮；过期时间由 TTL 控制。

**Why this priority**：短期记忆是 Agent 多轮对话的基础；中间态不入库是 constitution 原则八的硬约束，避免冗余 / 不一致 / 上下文污染。

**Acceptance Scenarios**：

1. **Given** 短期会话 key `session:userA:sessionX`，**When** append role=user "你好"、role=assistant "你好"，**Then** Redis 中能看到这两条；调 `loadRecent(10)` 按顺序返回。
2. **Given** 同一 key，**When** append role=think "<reasoning>..."，**Then** 抛 IllegalArgumentException（中间态拒收），Redis 长度不变。
3. **Given** key 设置了 30 分钟 TTL，**When** 静置 30 分钟，**Then** Redis 自动过期；再次 `loadRecent` 返回空（不抛错）。

---

### Edge Cases

- **user_id 缺失 / null**：`UserContext.peek()` 返回 null 时，所有 Repository 查询路径必须立即拒绝（IllegalStateException 或 NotFoundException），不静默返回全集。
- **ES 切片清理失败但 MySQL 已标记 deleted=1**：MySQL 已落库的事实不回退；记录一条后续差异任务（warn 日志 + 暴露在 metric），下次该素材被访问时按 deleted=1 跳过 ES 检索；不能让 ES 切片"复活"被搜到。
- **审计字段自动填充缺失**：Service 层手填 `setCreateTime(now)` 应当被在代码审查阶段就识别（grep 模式）；不在运行期处理。
- **Redis 会话超长（> max-rounds）**：`loadRecent` 只取最近 N 条（默认 10），更早的历史由上层 AutoContextMemory 负责压缩（本节不做）。
- **物理删除调用**：任何 `delete*` 方法必须经 Hibernate 的 `@SQLDelete` 包装（标记 deleted=1），原始 `DELETE FROM` SQL 不允许出现。

## Requirements

### Functional Requirements

- **FR-001**：四个 JPA Entity（`SysUser` / `WritingMaterial` / `WritingArticle` / `WritingMemory`）加对应 Repository，所有业务查询 MUST 强制携带 `userId`；不携带 `userId` 的查询方法不允许存在。**Why**：user_id 隔离是 constitution 决策四、原则五，US-5 需专项验收越权。
- **FR-002**：所有 Entity MUST 使用 `@SQLDelete(sql="UPDATE <table> SET deleted=1 WHERE id=?")` + `@Where(clause="deleted=0")` 实现逻辑删除；物理删除（DELETE FROM）禁止使用。**Why**：用户数据完整性 + 审计可恢复（constitution 决策六）。
- **FR-003**：Entity MUST 实现 `Auditable` 接口并加 `@EntityListeners(AuditEntityListener.class)`；`@CreatedDate` / `@LastModifiedDate` 由 writing-common 的 `AuditEntityListener` 自动填充，不在 Service 层手动 `setTime`。**Why**：审计字段时间统一、不靠人记（constitution 决策六）。
- **FR-004**：`RedisMemoryStore` MUST 封装 `session:{userId}:{sessionId}` key 的短期会话，仅接受 `role=user` / `role=assistant` 的对话层消息；提供 `append` / `loadRecent` / `expire` TTL 接口。**Why**：短期记忆三层分层（constitution 原则八），中间态不入库。
- **FR-005**：`MaterialChunkRepository` MUST 创建 / 操作索引 `writing_material_chunk`，embedding 维度恒等于 1024（`EsIndexConstants.EMBEDDING_DIM`），similarity = cosine；提供按 `userId` + 向量检索 topK、按 `materialId` 清理切片。**Why**：embedding dim 与 bge-m3 一致（constitution §9.3 / 决策七），否则向量入库或检索报错。

### Key Entities

- **SysUser**：平台用户。属性：id、username（唯一）、password（BCrypt 哈希）、nickname、createTime、updateTime、deleted。
- **WritingMaterial**：用户上传的素材文档。属性：id、userId（强制携带）、file_type（pdf/docx/txt/md）、file_name、file_path、file_size、content_text（解析后纯文本）、chunk_count（切片数）、createTime、updateTime、deleted。
- **WritingArticle**：用户产生的稿件。属性：id、userId（强制携带）、article_title、article_content、write_type（dialog / rag / template）、material_id（可空）、template_id（可空）、createTime、updateTime、deleted。
- **WritingMemory**：用户长期偏好 / 事实。属性：id、userId（强制携带）、memory_type（style / term / habit / other）、content、source（save_memory 工具 / 会话压缩抽取）、createTime、updateTime、deleted。
- **MaterialChunk**（ES doc）：素材切片。属性：id、material_id、user_id、chunk_index、chunk_text、embedding（dense_vector dim=1024）。
- **SessionMessage**（Redis value）：单条对话消息。属性：role（user / assistant）、content、timestamp。

## Success Criteria

### Measurable Outcomes

- **SC-001**：五个验收 harness 测试类（`WritingMemoryRepositoryTest` / `ArticleRepositoryTest` / `RedisMemoryStoreTest` / `MaterialChunkRepositoryTest` / `AuditTest`）`mvn test` 全部通过，零失败、零 `@Disabled`。
- **SC-002**：跨用户数据隔离 100% —— `findByUser_returnsOnlyOwnData_noCrossUser` 回归测试断言 `userA.size() == 1 && userB.size() == 1`，且漏 `userId` 的查询方法在 `grep -rn "findAll\|findById" src/main/java` 中不存在业务相关条目。
- **SC-003**：物理删除调用 0 次 —— `grep -rn "DELETE FROM\|delete.*nativeQuery" src/main/java` 在 Repository 实现中匹配为 0（仅 `@SQLDelete` 标注的逻辑删除允许）。
- **SC-004**：ES 索引维度恒等于 1024 —— `MaterialChunkRepository` 创建索引时 `dim` 取 `EsIndexConstants.EMBEDDING_DIM` 常量，不允许硬编码 `1024`。
- **SC-005**：审计字段自动填充生效 —— `AuditTest` 验证 new + save 后 `createTime` 非 null、未保存 → → 不允许 Service 层手填 `setCreateTime`（`grep -rn "setCreateTime" src/main/java` 业务层匹配为 0）。

## Assumptions

- **本节是第一节**，前序仅依赖 `wordpilot-init` 已落地的工程地基（UserContext、AuditEntityListener、Auditable、RedisKeys、EsIndexConstants）。
- **MySQL 自动建表**：开发环境 `ddev.production` = update 自动建表（constitution 决策六）；生产环境手动 SQL，本节不交付 SQL 脚本（由运维侧出）。
- **登录业务不属于本节**：本节只提供 `SysUser` 实体 + Repository 给第 3 节登录使用；登录密码校验 / Token 签发是第 3 节。
- **模板库 ES 不属于本节**：本节只交付 `MaterialChunkRepository`；`TemplateRepository` 由第 6 节模板引擎实现。
- **BGE bge-m3 模型**：本节不加载 / 调用 embedding；只引用 `EsIndexConstants.EMBEDDING_DIM = 1024` 常量做 ES mapping 校验。
- **Redis 会话压缩**：本节不实现 AutoContextMemory 压缩；只提供 `loadRecent(maxRounds)` 接口，上层 Agent 决定何时触发压缩。
- **依赖注入**：本节不写 Service / Controller；仅 Entity + Repository + Redis / ES 封装类，供第 3~8 节业务模块直接用。