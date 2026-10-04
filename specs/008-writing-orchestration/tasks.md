# Tasks: 三类写作编排（008）

**Input**: Design documents from `/specs/008-writing-orchestration/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: 课件"验收 harness"显式要求测试套件，本节 **harness 先行**（测试先于/伴随实现），方法名全英文 + `@DisplayName` 保留课件原文。

**Organization**: Tasks grouped by user story (P1→P2)，每 story 可独立实现与测试。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 不同文件、无依赖，可并行
- **[Story]**: US1 / US2 / US3
- 每条含确切文件路径

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 无新第三方依赖、无新 pom、无新表；仅补齐写作编排共享的请求 DTO（跨 US1/US2 共用）。

- [ ] T001 [P] 新增请求 DTO：`DialogRequest`（content @NotBlank + sessionId 可空）于 `writing-api/src/main/java/com/scriptagent/writing/api/dto/DialogRequest.java`
- [ ] T002 [P] 新增请求 DTO：`RagRequest`（requirement @NotBlank + sessionId 可空）于 `writing-api/src/main/java/com/scriptagent/writing/api/dto/RagRequest.java`
- [ ] T003 [P] 新增请求 DTO：`TemplateRequest`（templateId @NotBlank + params @NotNull + sessionId 可空）于 `writing-api/src/main/java/com/scriptagent/writing/api/dto/TemplateRequest.java`
- [ ] T004 [P] 新增响应 DTO：`ReeditResponse`（id/title/content/writeType/materialId/templateId/createTime，由 `WritingArticle` 映射）于 `writing-api/src/main/java/com/scriptagent/writing/api/dto/ReeditResponse.java`

**Checkpoint**: 共享 DTO 就绪，user story 可开始。

---

## Phase 2: User Story 1 - 三类写作路由编排 (Priority: P1) 🎯 MVP

**Goal**: 用户在三个 Tab 提交对话/仿写/模板写作，`WritingService` 按模式经 `AgentFactory` 路由到正确 Agent，`SseStreamingService` 统一 SSE 流式返回；未知模式拒绝。

**Independent Test**: 单测 mock AgentFactory/SseStreamingService/TemplateRepository/TemplateService，断言三模式路由正确（仿写挂工具）、未知模式拒绝；SSE 逐段推送 + 写 Redis 缓存。

### Tests for User Story 1 (harness 先行，先红后绿) ⚠️

- [ ] T005 [US1] 新增 `SseStreamingServiceTest` 于 `writing-api/src/test/java/com/scriptagent/writing/api/sse/SseStreamingServiceTest.java`，含**关键回归 `stream_pushToken_sendsSseAndWritesCache`**（每段发 SSE 帧 + 追加写 `sse:cache:{userId}:{sessionId}`）、`stream_complete_endsEmitter`、`stream_error_endsWithError`、`readCache_returnsAccumulatedContent`（断线恢复读回缓存）
- [ ] T006 [US1] 新增 `WritingServiceTest` 于 `writing-api/src/test/java/com/scriptagent/writing/api/controller/WritingServiceTest.java`，含**关键回归 `route_rag_injectsEsRetrieveTool`**（仿写 Agent 挂检索工具，参考素材不漏注入）、`route_dialog_noTool`、`route_template_rendersBeforeAgent`（读模板+校验启停+渲染后交 Agent）、`route_unknownMode_rejects`（未知模式拒绝）

### Implementation for User Story 1

- [ ] T007 [US1] 实现 `SseStreamingService` 于 `writing-api/src/main/java/com/scriptagent/writing/api/sse/SseStreamingService.java`：`stream(Long userId, String sessionId, Consumer<StreamOutput> task)` 创建 `SseEmitter`（不设超时）+ `StreamOutput` 适配器（`onToken`→`emitter.send(data)`+追加写 `RedisKeys.sseCache`；`onComplete`→完成+`complete()`；`onError`→`completeWithError`），`readCache` 读回缓存
- [ ] T008 [US1] 实现 `WritingService`（@RestController `@RequestMapping("/api/writing")`）于 `writing-api/src/main/java/com/scriptagent/writing/api/controller/WritingService.java`：三端点 `dialog`/`rag`/`template`；`user_id=UserContext.require()`；`sessionId` 缺失由 `UUID.randomUUID()` 生成；`agentFactory.create(mode,userId,sessionId)` + `sse.stream(userId,sessionId, output->agent.run(...))`
- [ ] T009 [US1] `WritingService.template` 编排：`TemplateRepository.findByIdAndUserId(templateId,userId)`（缺失/越权→`BusinessException`）→ 校验 `status=enabled`（停用拒绝）→ `TemplateService.render(tpl,params)`（含必填校验）→ 渲染后 Prompt 作 userMessage 交模板 Agent；ES 受检 `IOException` 转 `BusinessException`

**Checkpoint**: US1 三模式路由 + SSE 流式可用，可独立演示。

---

## Phase 3: User Story 2 - 稿件历史统一保存与查询 (Priority: P1)

**Goal**: 三类写作统一经 `ArticleService.save` 落稿（带 user_id + write_type）；`ArticleController` 提供列表（分页仅当前用户）/详情/重新编辑上下文/导出 Word/删除。

**Independent Test**: 单测 mock `WritingArticleRepository`，断言落稿带 user_id+write_type、分页仅当前用户、重新编辑返回历史稿件上下文、POI 导出 docx；越权详情/编辑/导出被拒。

### Tests for User Story 2 (harness 先行) ⚠️

- [ ] T010 [US2] 新增 `ArticleServiceTest` 于 `writing-business/src/test/java/com/scriptagent/writing/business/ArticleServiceTest.java`，含**关键回归 `save_carriesUserIdAndWriteType`**（落稿带 user_id+write_type）、`page_returnsOnlyOwnArticles`（查询仅当前用户）、`getForReedit_returnsHistoricalArticle`（重新编辑上下文正确）、`exportWord_generatesDocx`（POI 产出 docx）

### Implementation for User Story 2

- [ ] T011 [US2] 实现 `ArticleService` 于 `writing-business/src/main/java/com/scriptagent/writing/business/ArticleService.java`：`save(userId,writeType,title,content,materialId,templateId)`（统一落稿，`@Transactional`）、`pageByUser(userId,pageable)`（复用 `findByUserIdOrderByCreateTimeDesc`）、`getForReedit(userId,id)`（复用 `findByIdAndUserId`，越权→空）、`exportWord(userId,id)`（POI `XWPFDocument`→`byte[]`，越权→异常）
- [ ] T012 [US2] 实现 `ArticleController`（@RestController `@RequestMapping("/api/article")`）于 `writing-api/src/main/java/com/scriptagent/writing/api/controller/ArticleController.java`：`GET /`（分页列表）、`GET /{id}`（详情）、`POST /{id}/reedit`（返回 `ReeditResponse` 历史稿件上下文）、`POST /{id}/export`（下载 docx，`Content-Disposition`）、`DELETE /{id}`（逻辑删除）；`user_id=UserContext.require()`

**Checkpoint**: US2 稿件历史可用，可独立演示。

---

## Phase 4: User Story 3 - SSE 断线恢复 (Priority: P2)

**Goal**: 流式内容写 Redis `sse:cache:{userId}:{sessionId}`，断线重连后从缓存恢复已生成内容。

**Independent Test**: 单测 mock Redis 与 `SseEmitter`，断言逐段写入缓存、`readCache` 读回完整内容；重连以同 sessionId 从缓存重放（含显式恢复回归测试）。

### Tests for User Story 3 (harness 先行) ⚠️

- [ ] T013 [US3] 在 `SseStreamingServiceTest` 追加**关键回归 `reconnect_replaysCachedContent`**（断线后以同 sessionId 经 `readCache` 恢复完整已生成内容，不丢稿）

### Implementation for User Story 3

- [ ] T014 [US3] 校验 `SseStreamingService` 缓存写入与恢复闭环：确认 `onToken` 逐段追加写 `RedisKeys.sseCache`、`readCache` 读回、完成/失败边界不丢内容（补正实现，无需新文件）

**Checkpoint**: 断线重连可恢复，US1/US2/US3 全部独立可用。

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: 门禁全绿、跨节回归、验收收尾。

- [ ] T015 运行 `mvn clean verify`（全模块，P3C/SpotBugs/FindSecBugs/PMD/Checkstyle/Spotless），确保全绿
- [ ] T016 前序节回归确认：writing-agent-core / writing-storage / writing-model / writing-common 测试随全量 verify 全绿（跨节契约证据）
- [ ] T017 按 quickstart.md 执行验证（本节单测 + 关键回归命令），记录结果
- [ ] T018 `git status --short` / `git diff --stat` 核对交付物，产出变更总结（改动点/重点 review/如何验证），报告剩余人工项（真模型/真中间件/SSE 冒烟）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖，先行
- **US1 (Phase 2)**: 依赖 T001-T003 DTO（T004 的 `ReeditResponse` 归 US2，可并行）
- **US2 (Phase 3)**: 依赖 T001 等共享 DTO，不依赖 US1（稿件编排独立）
- **US3 (Phase 4)**: 依赖 US1 的 `SseStreamingService`（缓存写入闭环）
- **Polish (Phase 5)**: 依赖 US1/US2/US3 完成

### Within Each User Story

- Tests (T005/T006/T010/T013) 先写先红，实现后转绿
- 实现（T007-T009、T011-T012）后再跑测试，红了当场修

### Parallel Opportunities

- Phase 1: T001-T004 全并行（不同 DTO 文件）
- US2 的 T010/T011/T012 与 US1 的 T005-T009 相互独立（不同模块/文件），可并行
- US1 内 T005 与 T006 并行（两个测试文件），T007-T009 串行依赖测试先绿

---

## Implementation Strategy

### MVP First (US1 Only)

1. Phase 1 DTO 完成
2. US1：T005/T006 测试先红 → T007/T008/T009 实现 → 测试转绿
3. **STOP and VALIDATE**: 三模式路由 + SSE 流式独立可用（MVP）

### Incremental Delivery

1. US1（三类写作路由 + SSE 流式）→ 独立测试 → MVP
2. US2（稿件历史）→ 独立测试 → 增量
3. US3（断线恢复）→ 独立测试 → 收尾
4. Polish 门禁全绿 + 变更总结

---

## Notes

- 方法名全英文（驼峰），课件 `@DisplayName` 保留原文对号。
- 不新增第三方依赖 / 新表 / 新配置键；SSE 缓存复用 `RedisKeys.sseCache`。
- user_id 一律 `UserContext.require()`，请求体不含 user_id。
- 实现完成定义 = `mvn clean verify` 全绿；不自动 commit/push。
