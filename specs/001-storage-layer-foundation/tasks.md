---
description: "Task list for 第 1 节：存储层"
---

# Tasks: 存储层统一封装

**Input**: Design documents from `/specs/001-storage-layer-foundation/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

**Tests**: 本节有 5 个验收 harness 测试类，harness 任务**先于或伴随**对应实现任务（先红后绿）。

**Organization**: 按 3 条 user story（P1/P1/P2）分阶段；每条 story 独立可交付、可演示。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件，无依赖）
- **[Story]**: 任务所属 user story（US1/US2/US3）
- 描述含**精确文件路径**

## Path Conventions

本节交付物集中在 `writing-storage` 模块：

- 主代码：`writing-storage/src/main/java/com/scriptagent/writing/storage/`
- 测试：`writing-storage/src/test/java/com/scriptagent/writing/storage/`
- 复用：`writing-common` 已有的 `UserContext`、`AuditEntityListener`、`Auditable`、`RedisKeys`、`EsIndexConstants`（本节不新增 writing-common 代码）

---

## Phase 1: Setup（包结构占位）

**目的**：建实体/子包占位，让 .git 能追踪目录。

- [ ] T001 [P] 建主源子包 entity/owner/repository/redises/目录占位（`writing-storage/src/main/java/com/scriptagent/writing/storage/`），含 `package-info.java`
- [ ] T002 [P] 建测试子包 repository/redises/目录占位（`writing-storage/src/test/java/com/scriptagent/writing/storage/`），含 `package-info.java`

---

## Phase 2: Foundational（基础支撑）

**目的**：本阶段任务 MUST 完成才能进 user story。

- [ ] T003 [P] 写 `EsClientConfig`：`writing-storage/src/main/java/com/scriptagent/writing/storage/es/EsClientConfig.java` —— 提供 `RestHighLevelClient` Bean（destroyMethod="close"），地址从 `${elasticsearch.uris}` 读，用户名/密码从 `${elasticsearch.username:}` / `${elasticsearch.password:}` 可选注入（basic auth）
- [ ] T004 [P] 写 `MaterialChunkIndexInitializer`：`writing-storage/src/main/java/com/scriptagent/writing/storage/es/MaterialChunkIndexInitializer.java` —— 实现 `ApplicationRunner`，启动时调 `MaterialChunkRepository.ensureIndex()` 建索引（含 `dim=1024 cosine` mapping）
- [ ] T005 [P] 测试基础：`writing-storage/pom.xml` 加 `com.h2database:h2:test`（@DataJpaTest 默认走 H2 in-memory）

**Checkpoint**: foundation ready — user story 工作可开始。

---

## Phase 3: User Story 1 — 跨用户隔离（JPA Repository 强制 user_id）(Priority: P1) 🎯 MVP

**Goal**：4 个 JPA Entity + 4 个 Repository；所有查询走 userId（不从前端取）；逻辑删除 + 审计自动填。

**Independent Test**：
- `WritingMemoryRepositoryTest.findByUser_returnsOnlyOwnData_noCrossUser`：userA / userB 各一条记忆，`findByUser(userA).size() == 1 && findByUser(userB).size() == 1`
- `WritingMemoryRepositoryTest.delete_logical_hiddenFromQuery_rowRetained`：删除后 `findByUser` 为空，但物理行 `deleted=1` 仍在

### Tests for User Story 1（harness 先行）

> **NOTE**：先写测试，跑一次确认 RED，再实现（教学 TDD 节奏，但本节允许"测试 + 实现一起落地后红就当场修"，不强制先红）

- [ ] T006 [P] [US1] 写 `WritingMemoryRepositoryTest`：`src/test/java/com/scriptagent/writing/storage/repository/WritingMemoryRepositoryTest.java`，`@DataJpaTest` + `@AutoConfigureTestDatabase(replace=ANY)` 走 H2；含测试方法 `@DisplayName("按 user_id 查、user_id 隔离；逻辑删除后查不到、历史保留")` → `findByUser_returnsOnlyOwnData_noCrossUser` 与 `@DisplayName("逻辑删除后查不到、历史保留")` → `delete_logical_hiddenFromQuery_rowRetained`
- [ ] T007 [P] [US1] 写 `ArticleRepositoryTest`：`src/test/java/com/scriptagent/writing/storage/repository/ArticleRepositoryTest.java`，`@DataJpaTest`；含 `@DisplayName("分页仅当前用户；逻辑删除生效")` → `findByUserId_returnsOnlyOwnArticlesPaged_logicalDeletedHidden`
- [ ] T008 [P] [US1] 写 `AuditTest`：`src/test/java/com/scriptagent/writing/storage/repository/AuditTest.java`，`@DataJpaTest`；含 `@DisplayName("@CreatedDate 自动填充 create_time，非手填")` → `audit_autoFillsCreateTimeAndUpdateTime`（new + save 后 `createTime` / `updateTime` 非 null 且 `updateTime >= createTime`）

### Implementation for User Story 1

- [ ] T009 [P] [US1] 写 `SysUser`：`src/main/java/com/scriptagent/writing/storage/entity/SysUser.java` —— 字段 `id`（@Id @GeneratedValue IDENTITY）、`username`（UNIQUE）、`password`（BCrypt 哈希）、`nickname`（nullable）；`@Entity @Table(name="sys_user")` + `@SQLDelete(sql="UPDATE sys_user SET deleted=1 WHERE id=?")` + `@Where(clause="deleted=0")` + `@EntityListeners(AuditEntityListener.class)` + 实现 `Auditable`
- [ ] T010 [US1] 写 `SysUserRepository`：`src/main/java/com/scriptagent/writing/storage/repository/SysUserRepository.java` —— 继承 `JpaRepository<SysUser, Long>`，含 `Optional<SysUser> findByUsername(String username)`
- [ ] T011 [P] [US1] 写 `WritingMemory`：`src/main/java/com/scriptagent/writing/storage/entity/WritingMemory.java` —— 字段 `id, userId, memoryType, content, source, createTime, updateTime, deleted`；`@Table(name="writing_memory")` + `@SQLDelete(sql="UPDATE writing_memory SET deleted=1 WHERE id=?")` + `@Where(clause="deleted=0")` + `Auditable`；`memoryType` 用 String 字面量 `style` / `term` / `habit` / `other`（不引枚举，留 Plan 阶段定 type validation；本节留字段类型 String）
- [ ] T012 [US1] 写 `WritingMemoryRepository`：`src/main/java/com/scriptagent/writing/storage/repository/WritingMemoryRepository.java` —— 含 `List<WritingMemory> findByUserId(Long userId)` + `List<WritingMemory> findByUserIdAndMemoryType(Long userId, String memoryType)`
- [ ] T013 [P] [US1] 写 `WritingArticle`：`src/main/java/com/scriptagent/writing/storage/entity/WritingArticle.java` —— 字段 `id, userId, articleTitle, articleContent, writeType, materialId（nullable）, templateId（nullable）, createTime, updateTime, deleted`；`@Table(name="writing_article")` + `@SQLDelete` + `@Where` + `Auditable`；`writeType` 用 String 字面量 `dialog` / `rag` / `template`
- [ ] T014 [US1] 写 `WritingArticleRepository`：`src/main/java/com/scriptagent/writing/storage/repository/WritingArticleRepository.java` —— 含 `Page<WritingArticle> findByUserIdOrderByCreateTimeDesc(Long userId, Pageable pageable)` + `Optional<WritingArticle> findByIdAndUserId(Long id, Long userId)`
- [ ] T015 [P] [US1] 写 `WritingMaterial`：`src/main/java/com/scriptagent/writing/storage/entity/WritingMaterial.java` —— 字段 `id, userId, fileName, fileType, fileSize, filePath, contentText, chunkCount, createTime, updateTime, deleted`；`@Table(name="writing_material")` + `@SQLDelete(sql="UPDATE writing_material SET deleted=1 WHERE id=?")` + `@Where(clause="deleted=0")` + `Auditable`；`fileType` 用 String 字面量 `pdf` / `docx` / `txt` / `md`
- [ ] T016 [US1] 写 `WritingMaterialRepository`：`src/main/java/com/scriptagent/writing/storage/repository/WritingMaterialRepository.java` —— 含 `Page<WritingMaterial> findByUserId(Long userId, Pageable pageable)` + `Optional<WritingMaterial> findByIdAndUserId(Long id, Long userId)`

**Checkpoint**：US1 全部完成；`mvn -pl writing-storage test -Dgroups='!integration'` 三个 Repository + Audit 测试全绿。

---

## Phase 4: User Story 2 — 删除走逻辑删除 + ES 切片按 material_id 同步清理 (Priority: P1)

**Goal**：素材删除时 MySQL 标记 `deleted=1`（行不丢），ES `writing_material_chunk` 索引按 `materialId` 同步清切片。

**Independent Test**：
- `MaterialChunkRepositoryTest.ensureIndex_createsWritingMaterialChunkedWithDim1024Cosine`：启动后索引存在，embedding mapping dim=1024
- `MaterialChunkRepositoryTest.searchByUser_filtersByUserId`：userA 写入 3 条 + userB 写入 2 条，按 userA 检索只回 userA 的
- `MaterialChunkRepositoryTest.deleteByMaterialId_removesAllChunks`：materialX=1 的 3 条切片清空，materialX=2 的不受影响

### Tests for User Story 2

- [ ] T017 [US2] 写 `MaterialChunkRepositoryTest`：`src/test/java/com/scriptagent/writing/storage/es/MaterialChunkRepositoryTest.java`，`@SpringBootTest` + `@Tag("integration")`（CI 跳过，本地人工跑）；含三个测试方法
  - `@DisplayName("ES 启动建索引 dim=1024 cosine")` → `ensureIndex_createsWritingMaterialChunkedWithDim1024Cosine`
  - `@DisplayName("ES 检索带 user_id")` → `searchByUser_filtersByUserId`
  - `@DisplayName("删除按 material_id 清干净")` → `deleteByMaterialId_removesAllChunks`

### Implementation for User Story 2

- [ ] T018 [P] [US2] 写 `MaterialChunk`：`src/main/java/com/scriptagent/writing/storage/es/MaterialChunk.java` —— ES doc POJO（不引 `@Entity`），字段 `id (String), materialId (Long), userId (Long), chunkIndex (int), chunkText (String), embedding (float[])`；ES mapping：id `long`, materialId/userId `long`, chunkIndex `integer`, chunkText `text`, embedding `dense_vector dims=1024 similarity=cosine`
- [ ] T019 [US2] 写 `MaterialChunkRepository`：`src/main/java/com/scriptagent/writing/storage/es/MaterialChunkRepository.java` —— `void ensureIndex()` + `void saveChunks(Long userId, Long materialId, List<MaterialChunk> chunks)` + `List<MaterialChunk> searchByUser(Long userId, float[] queryVector, int topK)` + `void deleteByMaterialId(Long materialId)`；doc id = `"{materialId}_{chunkIndex}"`；`searchByUser` 用 `match` 或 `bool query` 加 `term user_id=clause` + `knn`（依 ES 7.17 客户端 API）；`ensureIndex` 用 `GetIndexRequest` 检测 + 不存在则 `CreateIndexRequest` 带 mapping
- [ ] T020 [US2] 写 `TemplateRepository` 占位接口：`src/main/java/com/scriptagent/writing/storage/es/TemplateRepository.java` —— 空接口 + javadoc 说明由第 6 节实现

**Checkpoint**：US2 全部完成；本地起 ES 跑 `mvn -pl writing-storage test -Dgroups=integration` 三个 ES 测试全绿（CI 跳过）。

---

## Phase 5: User Story 3 — Redis 短期会话只存对话层 (Priority: P2)

**Goal**：`RedisMemoryStore` 封装 `session:{userId}:{sessionId}` 短期会话，只接受 `user` / `assistant` 对话层消息，含 TTL。

**Independent Test**：
- `RedisMemoryStoreTest.append_acceptsUserAssistantOnly_intermediateRejected`：role=user / assistant 接受，role=其他字符串（手工绕过枚举）应抛 IllegalArgumentException
- `RedisMemoryStoreTest.loadRecent_returnsLastNRoundsChronological`：append 5 条，loadRecent(3) 返回最后 3 条按时间顺序
- `RedisMemoryStoreTest.expire_appliesTtlAndExpires`：set 1 秒 TTL，过期返回 empty

### Tests for User Story 3

- [ ] T021 [US3] 写 `RedisMemoryStoreTest`：`src/test/java/com/scriptagent/writing/storage/redis/RedisMemoryStoreTest.java`，`@SpringBootTest`（依赖本地 Redis）；含 `@DisplayName("会话存取 + TTL；只存对话层、中间态不入")` → 含三个测试方法（`append_acceptsUserAssistantOnly_intermediateRejected`、`loadRecent_returnsLastNRoundsChronological`、`expire_appliesTtlAndExpires`）

### Implementation for User Story 3

- [ ] T022 [P] [US3] 写 `SessionRole` + `SessionMessage`：`src/main/java/com/scriptagent/writing/storage/redis/SessionRole.java`（enum：`USER, ASSISTANT`）+ `SessionMessage.java`（record：`SessionRole role, String content, Instant timestamp`）
- [ ] T023 [US3] 写 `RedisMemoryStore`：`src/main/java/com/scriptagent/writing/storage/redis/RedisMemoryStore.java` —— 注入 `StringRedisTemplate`；用 `RedisKeys.session(userId, sessionId)` 拼 key；value JSON 序列化（Jackson）；提供 `append(userId, sessionId, SessionRole, content)` / `loadRecent(userId, sessionId, maxRounds)` / `expire(userId, sessionId, Duration ttl)` / `clear(userId, sessionId)`；默认 TTL = 30 分钟

**Checkpoint**：US3 全部完成；本地起 Redis 跑 `mvn -pl writing-storage test -Dgroups='!integration'` RedisMemoryStoreTest 全绿。

---

## Phase 6: Polish & Cross-Cutting Concerns

**目的**：跨 user story 的工作 / 质量门禁。

- [ ] T024 [P] 改 `application.yaml`：把 `${ENV_VAR}` 占位换回（用户先前编辑成明文 key / 本机中间件硬编码，本节交付前改回占位，避免泄漏 key 入版本库；H4 全局不变量③要求）
- [ ] T025 [P] 全量静态门禁：`mvn -B -ntp verify` —— 含 Spotless + Checkstyle + SpotBugs + FindSecBugs + P3C + OWASP Dependency-Check 锁版本
- [ ] T026 [P] 关键 grep 核对：物理删除调用 0 / Service 层 setCreateTime 0 / EMBEDDING_DIM 引用正确 / 明文 key 0
  - `grep -rn "DELETE FROM\|delete.*nativeQuery" writing-storage/src/main/java` 业务 Repository 实现匹配为 0
  - `grep -rn "setCreateTime" writing-business/src/main writing-api/src/main` 业务匹配为 0（writing-common 的 Auditable setter 是接口，不算）
  - `grep -rn "1024" writing-storage/src/main/java` 业务匹配仅限 `EsIndexConstants.EMBEDDING_DIM` 引用
  - `grep -rnE "api[_-]?key|password" writing-start/src/main/resources/application*.yaml` 不含明文值（全部是 `${ENV_VAR}` 或 `${ENV_VAR:default-value}`）
- [ ] T027 [P] 更新 `README.md`：第 1 节交付清单——四张表自动建 / 五个测试类 / ES 索引 / Redis 会话 / TemplateRepository 占位
- [ ] T028 跑 quickstart.md 验证清单：`mvn -pl writing-storage test -Dgroups='!integration'` 单测全绿 + `mvn -B -ntp verify` 全绿 + （本地人工）`mvn -pl writing-storage test -Dgroups=integration` 集成测试全绿

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: 无依赖，可立即开始
- **Phase 2 (Foundational)**: 依赖 Phase 1；BLOCK 所有 user story
- **Phase 3 (US1, P1)**: 依赖 Phase 2；不依赖其他 user story
- **Phase 4 (US2, P1)**: 依赖 Phase 2 + US1（需要 WritingMaterial entity 才能写 MaterialChunk 关联）
- **Phase 5 (US3, P2)**: 依赖 Phase 2；与 US1/US2 独立（Redis 操作独立于 JPA/ES）
- **Phase 6 (Polish)**: 依赖全部 user story 完成

### User Story Dependencies

- **US1 (P1)**: Foundational 完成后即可启动；无跨 user story 依赖
- **US2 (P1)**: Foundational + US1（MaterialChunk.userId 引用 writing_material.userId 的语义，但代码层无编译依赖；可与 US1 并行，但语义上验证要 US1 的 WritingMaterialRepository 落地）
- **US3 (P2)**: Foundational 完成后即可启动；与 US1/US2 完全独立

### Within Each User Story

- Tests MUST 与 Implementation 一起提交；本任务流不要求"测试先 RED 再绿"的硬 TDD，但允许"测试 + 实现同时落地后跑红了当场修"
- Entity 先于 Repository（Repository 依赖 Entity 编译）
- Configuration（EsClientConfig / IndexInitializer）先于 Repository（Repository 注入 Client）

### Parallel Opportunities

- Phase 2 三个 Foundational 任务可并行（T003 [P] / T004 [P] / T005 [P]）
- Phase 3 中：Tests 三件并行（T006 [P] / T007 [P] / T008 [P]）；Entity 4 件并行（T009 [P] / T011 [P] / T013 [P] / T015 [P]）；Repository 4 件并行（T010 / T012 / T014 / T016 [非 P 因为依赖对应 Entity]）
- Phase 4 中：Test 1 件 + MaterialChunk 类 [P] + MaterialChunkRepository 串行（Repository 依赖 POJO）
- Phase 5 中：SessionRole/SessionMessage [P] 与 RedisMemoryStore 串行
- US2 / US3 在 US1 落地后**可并行**

---

## Parallel Example: User Story 1

```bash
# US1 三个测试类一起写（不同文件，无依赖）
Task: "WritingMemoryRepositoryTest"
Task: "ArticleRepositoryTest"
Task: "AuditTest"

# US1 四个 Entity 一起写（不同文件，无依赖）
Task: "SysUser entity"
Task: "WritingMemory entity"
Task: "WritingArticle entity"
Task: "WritingMaterial entity"

# 四个 Repository 写（依赖对应 Entity 但互不依赖）
Task: "SysUserRepository"
Task: "WritingMemoryRepository"
Task: "WritingArticleRepository"
Task: "WritingMaterialRepository"
```

---

## Implementation Strategy

### MVP First（US1 Only）

1. Phase 1: Setup
2. Phase 2: Foundational
3. Phase 3: User Story 1
4. **STOP and VALIDATE**: `mvn -pl writing-storage test -Dgroups='!integration'` 全绿 + 第 1 节核心交付可演示（4 表建好 / 跨用户隔离 / 逻辑删除 / 审计自动）

### Incremental Delivery

1. Setup + Foundational → foundation ready
2. US1 → 验证 → 演示（**MVP：4 张表 + 4 个 Repository + 3 个 harness 测试全绿**）
3. US2 → 验证 → 演示（ES 索引 + 检索 + 清理）
4. US3 → 验证 → 演示（Redis 会话存取 + TTL）
5. 每条 story 增量叠加，前序 story 不破

### Parallel Team Strategy

单开发者按 P1 → P2 顺序；多开发者可在 US2 + US3 阶段分头干。

---

## Notes

- [P] 任务 = 不同文件，无依赖
- [Story] 标签用于 traceability
- 每条 user story 独立可交付可测试
- 实现与测试一起提交（红了当场修，不攒到最后）
- 物理删除禁止（FR-002）—— Reviewer 跑 `grep` 核对
- embedding dim=1024 必须经 `EsIndexConstants.EMBEDDING_DIM` 常量（FR-005）—— Reviewer 跑 `grep` 核对
- user_id 必须经 `UserContext`（原则五）—— 本节 Repository 只接受 `userId` 参数，不从前端取；上层 Service 注入 `UserContext.require()`
- 静态门禁是构建门禁（Spotless + Checkstyle + P3C + SpotBugs + OWASP DepCheck）—— 实现完成定义是 `mvn clean verify` 全绿