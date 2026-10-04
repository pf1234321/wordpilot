# Implementation Plan: 三类写作编排（api/business 路由与协调层）

**Branch**: `lesson8-writing-orchestration` | **Date**: 2026-10-04 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/008-writing-orchestration/spec.md`

## Summary

按决策六（SSE 流式输出 + Redis 缓存）、宪法原则九（一套 Agent 底座支撑三种模式）、宪法原则五（user_id 强制隔离）实现写作业务编排层：`WritingService`（writing-api @RestController，`/api/writing` 三端点，按 dialog/rag/template 路由到 `AgentFactory` 生成的无状态 Agent）；`SseStreamingService`（writing-api，把 writing-agent-core 的 `StreamOutput` 回调桥接为 SSE 帧，逐段写 Redis `sse:cache:{userId}:{sessionId}`，支持断线恢复）；`ArticleService`（writing-business，三类写作统一落稿入口 + 分页查询 + 重新编辑上下文 + POI 导出 Word）；`ArticleController`（writing-api，`/api/article`：列表/详情/重新编辑/导出/删除，端点沿用 CLAUDE.md 接口层字面量）。素材/模板 Controller 不在本节，沿用前序 Service/Repository。

## Technical Context

**Language/Version**: Java 21 (Spring Boot 3.3.5)，P3C 门禁 targetJdk=20（避免 Java 21-only 语法形态）

**Primary Dependencies**（前序交付物，依赖检查已核实）:
- writing-api → writing-business（compile）→ writing-agent-core / writing-storage / writing-model / writing-common，**传递依赖已可用**：`AgentFactory.create(String mode, Long userId, String sessionId)`、`WritingAgent.run(Long, String, String, StreamOutput)`、`StreamOutput`（onToken/onComplete/onError，agent-core 不依赖 spring-web，SSE 桥接归 writing-api）；`TemplateService.render(TemplateDoc, Map<String,Object>)`、`TemplateRepository.findByIdAndUserId(String, Long)`（`getStatus()` 校验启停）；`WritingArticle` / `WritingArticleRepository.findByUserIdOrderByCreateTimeDesc` / `findByIdAndUserId`（writing-storage）；`UserContext.require()`、`RedisKeys.sseCache(userId, sessionId)`、`RedisMemoryStore`（writing-common/storage）。
- **导出 Word**：POI（`poi` + `poi-ooxml`）已由 writing-storage compile 依赖传递到 writing-business，无需新增第三方依赖。
- **不新增任何第三方依赖**：AgentScope 维持占位注释（与全项目前序节既定处理一致）；本节只用已有内部模块与传递依赖。

**SSE 桥接（关键设计决策①）**:
- writing-agent-core 的 `StreamOutput` 抽象已就绪（第 5 节决策②落地：`onToken(String)`/`onComplete()`/`onError(Throwable)`，`StreamingResponseHandler.emit` 逐段回调 + 回写 Redis 会话）。
- `SseStreamingService`（writing-api，`com.scriptagent.writing.api.sse` 包）负责把 `StreamOutput` 回调转成 SSE 帧并做断线恢复缓存：
  - `stream(Long userId, String sessionId, Consumer<StreamOutput> task)`：创建 `SseEmitter`（不设超时，长文），包一个 `StreamOutput` 适配器——`onToken(token)` → `emitter.send(SseEmitter.event().data(token))` 逐段推送 + 追加写 Redis `sse:cache:{userId}:{sessionId}`；`onComplete()` → 推送完成标记 + `emitter.complete()`；`onError(t)` → `emitter.completeWithError(t)`。随后执行 `task.accept(adapter)`，返回 emitter。
  - `readCache(Long userId, String sessionId)`：读 Redis `sse:cache:{userId}:{sessionId}`，返回已累计内容（断线重连后前端取回恢复）。
- 课件 `WritingService` 骨架中的 `sse.stream(emitter -> agent.run(...))` 用真实签名 `sse.stream(userId, sessionId, output -> agent.run(userId, sessionId, msg, output))` 等价落地；关键回归断言不变。

**模板写作编排（关键设计决策②）**:
- 按技术方案 §4.3 / §7.2 与流程五：`WritingService.template` 先 `TemplateRepository.findByIdAndUserId(templateId, userId)` 取模板（缺失/越权统一按不存在报错）→ 校验 `status=enabled`（停用拒绝新写作）→ `TemplateService.render(tpl, params)` 渲染填充（含必填项校验，防残缺稿）→ 把渲染后模板 Prompt 作为 userMessage 交给模板 Agent（`TEMPLATE_CORE` 系统提示提供身份）。ES 受检 `IOException` 转 `BusinessException`。

**sessionId 策略（关键设计决策③）**: 三端点请求可携带 `sessionId`（多轮对话由前端回传以续上下文）；缺失则由服务端 `UUID.randomUUID()` 生成。`user_id` 一律 `UserContext.require()`，请求体不含 user_id（H4 不变量①，防越权）。

**稿件统一落稿与 API（关键设计决策④，用户已确认"A + 稿件 Controller"）**:
- `ArticleService.save(userId, writeType, title, content, materialId, templateId)` 为三类写作统一落稿入口，带 user_id + write_type（FR-005，关键回归）。
- `ArticleService.pageByUser(userId, pageable)`：分页仅当前用户，create_time 倒序（复用 `findByUserIdOrderByCreateTimeDesc`）。
- `ArticleService.getForReedit(userId, id)`：返回历史稿件作为"继续迭代"的上下文（复用 `findByIdAndUserId`，越权一律空）。
- `ArticleService.exportWord(userId, id)`：POI `XWPFDocument` 生成 docx（标题段 + 正文段），返回 `byte[]`。
- `ArticleController`（writing-api，`/api/article`）：`GET /`（列表分页）、`GET /{id}`（详情）、`POST /{id}/reedit`（返回历史稿件内容作上下文）、`POST /{id}/export`（下载 Word docx）、`DELETE /{id}`（逻辑删除）。

**Testing**（harness 先行，方法名英文 + `@DisplayName` 保留课件原文；核心可全 mock，单测秒级）:
- `SseStreamingServiceTest`（writing-api，单测，mock `StringRedisTemplate`）：**关键回归 `stream_pushToken_sendsSseAndWritesCache`**（每段发 SSE 帧 + 追加写 `sse:cache:`）、`stream_complete_endsEmitter`、`stream_error_endsWithError`、`readCache_returnsAccumulatedContent`（断线恢复）。
- `WritingServiceTest`（writing-api，单测，mock `AgentFactory`+`SseStreamingService`+`TemplateRepository`+`TemplateService`）：**关键回归 `route_rag_injectsEsRetrieveTool`**（仿写 Agent 挂检索工具，参考素材不漏注入）、`route_dialog_noTool`、`route_template_rendersBeforeAgent`（读模板+校验启停+渲染后交 Agent）、`route_unknownMode_rejects`（未知模式拒绝）。
- `ArticleServiceTest`（writing-business，单测，mock `WritingArticleRepository`）：**关键回归 `save_carriesUserIdAndWriteType`**（落稿带 user_id+write_type）、`page_returnsOnlyOwnArticles`（查询仅当前用户）、`getForReedit_returnsHistoricalArticle`（重新编辑上下文正确）、`exportWord_generatesDocx`（POI 产出 docx）。
- 单测默认跑；integration 冒烟 `@Tag("integration")` CI 跳过；**实现完成的定义是 `mvn clean verify` 全绿**。

**Constraints**:
- 依赖方向：writing-api → writing-business（传递 agent-core/model/storage/common），writing-business → writing-agent-core + writing-storage + writing-model + writing-common；无新增模块、无循环。SseStreamingService 归 writing-api（agent-core 不依赖 spring-web）。
- user_id 唯一合法来源 = `UserContext`（H4 不变量①）；稿件/模板/SSE 缓存全部带 user_id；禁止前端参数取。
- 稿件查询/详情/编辑/导出强制 user_id（FR-011，越权拒绝）；逻辑删除 `@SQLDelete` 生效（H4 不变量②）。
- 不新增第三方依赖；不改前序节已定字面量（`AgentFactory`/`WritingAgent`/`StreamOutput`/`TemplateService`/`TemplateRepository`/`WritingArticleRepository`/`UserContext`/`RedisKeys` 原样消费）；SSE 缓存复用 `RedisKeys.sseCache`。
- 凭证/模型路径全 `${ENV_VAR}`（H4 不变量③）；Embedding 恒 1024 维（H4 不变量④，前序回归）。

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 检查 |
|------|------|
| I 单体 7 模块单向依赖 | ✅ 只动 writing-api（WritingService/SseStreamingService/ArticleController/DTO）+ writing-business（ArticleService），无新模块无循环 |
| II Agent 无状态，状态外置 (NON-NEGOTIABLE) | ✅ WritingService 每次经 AgentFactory 按需生成 Agent；会话在 Redis、SSE 缓存 Redis、业务在 MySQL |
| III AgentScope 边界 / 模型唯一出口 (NON-NEGOTIABLE) | ✅ 编排层只路由协调，不碰 Agent 内部、不直连模型（经 writing-agent-core → writing-model 出口） |
| IV 极简登录 | 不涉及（写作接口在拦截器保护下，user_id 取自 UserContext） |
| V 查询强制 user_id (NON-NEGOTIABLE) | ✅ 稿件/模板/SSE 全部 `UserContext.require()`，前端参数取 user_id 拒绝 |
| VI JPA 规范 | ✅ 复用第 1 节 `WritingArticle`，无新表，逻辑删除 `@SQLDelete` 生效 |
| VII RAG 管线自实现 | ✅ 仿写路由挂 `ESRetrieveTool`（第 5 节），参考素材不漏注入（关键回归） |
| VIII 记忆三层分层 | ✅ 编排层不混记忆；SSE 缓存与会话缓存（Redis）各司其职 |
| IX 三类写作共享一套 Agent 底座 | ✅ 三端点共用 AgentFactory + StreamOutput 桥接 + SSE 封装 |
| X 可演示成果 | ✅ harness 测试全绿（三模式路由/仿写挂工具/稿件落稿隔离/SSE 缓存恢复），真模型/真中间件人工项见验收报告 |

## Project Structure

### Documentation (this feature)

```text
specs/008-writing-orchestration/
├── spec.md              # 本节规格（已产出，含 Clarifications）
├── checklists/
│   └── requirements.md  # spec 质量检查
├── plan.md              # 本文件
├── research.md          # Phase 0（本 plan 产出）
├── data-model.md        # Phase 1（本 plan 产出）
├── contracts/           # Phase 1（本 plan 产出：REST 端点契约）
├── quickstart.md        # Phase 1（本 plan 产出）
└── tasks.md             # /speckit-tasks 输出（本 plan 不创建）
```

### Source Code (repository root)

本节交付物落在 writing-api 与 writing-business（技术方案 §4 落位表）：

```text
writing-api/src/main/java/com/scriptagent/writing/api/
├── controller/
│   ├── WritingController.java     # 【新增】或 WritingService 作为 @RestController（/api/writing 三端点）
│   └── ArticleController.java     # 【新增】/api/article 列表/详情/重新编辑/导出/删除
├── sse/
│   └── SseStreamingService.java   # 【新增】SSE 桥接 + Redis 断线缓存（stream/readCache）
└── dto/
    ├── DialogRequest.java         # 【新增】content + sessionId(可空)
    ├── RagRequest.java            # 【新增】requirement + sessionId(可空)
    ├── TemplateRequest.java       # 【新增】templateId + params(Map) + sessionId(可空)
    └── ReeditResponse.java        # 【新增】历史稿件内容（重新编辑上下文）【或复用实体】
```

```text
writing-business/src/main/java/com/scriptagent/writing/business/
└── ArticleService.java            # 【新增】统一落稿/分页/重新编辑上下文/导出 Word
```

```text
writing-api/src/test/java/com/scriptagent/writing/api/...
├── SseStreamingServiceTest.java   # 【新增】流式逐段 + Redis 缓存 + 断线恢复（关键回归）
└── WritingServiceTest.java        # 【新增】三模式路由 + 仿写挂工具 + 模板渲染 + 未知模式拒绝（关键回归）

writing-business/src/test/java/com/scriptagent/writing/business/
└── ArticleServiceTest.java        # 【新增】落稿带 user_id+write_type / 仅当前用户 / 重新编辑上下文 / 导出 docx（关键回归）
```

**Structure Decision**: 按技术方案 §4 模块落位表——`WritingService`（路由）→ writing-api；`ArticleService` → writing-business；`SseStreamingService` → writing-api（因 writing-agent-core 不依赖 spring-web，SSE 桥接必须在 api 层）；`ArticleController` + 写作请求 DTO → writing-api。WritingService 以 `@RestController` 形态承载三类写作端点（贴合课件主角类骨架），真实签名以 `SseStreamingService` + `StreamOutput` 等价落地。无新表、无新配置键（复用 `RedisKeys.sseCache` 与既有 ES/Redis/MySQL）。

## Complexity Tracking

> 本节无 Constitution 违例需 justify。唯一对外概念扩展（ArticleController 的 `/api/article` 端点）已由用户在 Step3 澄清确认（"A + 稿件 Controller"），端点路径沿用 CLAUDE.md 接口层既有字面量，非新增概念。
