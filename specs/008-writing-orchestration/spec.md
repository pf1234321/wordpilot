# Feature Specification: 三类写作编排（api/business 路由与协调层）

**Feature Branch**: `lesson8-writing-orchestration`

**Created**: 2026-10-04

**Status**: Draft

**Input**: User description: "开发第8节 三类写作编排 api/business——写作业务编排层：按用户选择把请求路由到对话/仿写/模板三类写作 Agent，协调记忆、工具、模型、存储跑完一次写作，SSE 流式返回并落稿件历史。模块落位：WritingService（路由，writing-api/business）、ArticleService（稿件历史，writing-business）、SseStreamingService（SSE 流式封装 + Redis 缓存，writing-api）。"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 三类写作路由编排 (Priority: P1)

用户在写作工作台三个 Tab（一句话对话 / 素材仿写 / 模板写作）分别提交写作请求。系统按所选写作方式路由到对应 Agent 模式，统一经 SSE 流式把生成内容逐段返回前端，并回写会话。用户无需关心底层 Agent 差异，只看到"选了哪种方式就得到对应风格的稿件"。

**Why this priority**: 三类写作编排是本节核心，是 writing-api 与 writing-agent-core / writing-storage / writing-model 之间的调度中枢；不路由就没法写作，是能力一/二/三的前端入口。

**Independent Test**: 用 mock Agent/存储/模型，分别调用对话、仿写、模板三个入口，断言路由到正确 Agent 配置（仿写必须挂检索工具、对话/模板不挂），未知模式被拒绝。可独立验证"按模式路由"这一核心价值。

**Acceptance Scenarios**:

1. **Given** 用户已登录且持有有效 Token，**When** 提交对话写作请求（含 content），**Then** 系统路由到对话 Agent（无检索工具），SSE 流式逐段返回，user_id 取自 UserContext。
2. **Given** 用户已登录且选择了素材仿写，**When** 提交仿写请求（含 requirement、sessionId），**Then** 系统路由到仿写 Agent（挂 ESRetrieveTool），Agent 按 user_id 检索参考素材注入 Prompt，SSE 流式返回。
3. **Given** 用户已登录且选择了模板写作，**When** 提交模板请求（含 templateId、params），**Then** 系统读取该用户的 ES 模板、校验必填项、渲染填充后组装 Prompt，路由到模板 Agent，SSE 流式返回。
4. **Given** 系统收到一个未知写作模式的请求，**When** 尝试路由，**Then** 明确拒绝并返回业务错误，不静默降级为任意模式。

---

### User Story 2 - 稿件历史统一保存与查询 (Priority: P1)

三类写作完成后，系统统一把稿件落库，记录归属用户、写作文式（dialog/rag/template）、关联素材/模板。用户在稿件历史页能分页查看"仅自己"的稿件，并可对历史稿件重新编辑（把历史稿件作为上下文继续迭代）、导出 Word。

**Why this priority**: 稿件历史是四类支撑能力之一，三类写作共用统一落稿入口（带 user_id + write_type），是历史查准、用户隔离的关键；ArticleService 在 business 层承载。

**Independent Test**: mock Repository，调用 save（带 user_id + write_type）与分页查询，断言查询仅返回当前用户、write_type 正确；重新编辑把历史稿件作为上下文；导出 Word 生成 docx。可独立验证"统一落稿 + 用户隔离"。

**Acceptance Scenarios**:

1. **Given** 用户完成一次写作，**When** 调用稿件保存（带 writeType、title、content、可选 materialId/templateId），**Then** 稿件带 user_id + write_type 落库。
2. **Given** 用户查看稿件历史，**When** 分页查询，**Then** 仅返回当前用户稿件，按 create_time 倒序，且标注了写作文式。
3. **Given** 用户选择一条历史稿件重新编辑，**When** 触发重新编辑，**Then** 该稿件作为上下文继续迭代。
4. **Given** 用户导出稿件，**When** 触发导出 Word，**Then** 生成 docx 文件。
5. **Given** 用户 A 尝试访问用户 B 的稿件（仅凭稿件 id），**When** 查询详情/编辑/导出，**Then** 被拒绝（越权隔离）。

---

### User Story 3 - SSE 流式封装与断线恢复 (Priority: P2)

写作生成过程经 SSE 流式逐段推送前端；流式内容同时写 Redis 缓存（`sse:cache:{userId}:{sessionId}`），支持断线重连后从缓存恢复未收全的内容。

**Why this priority**: 长文生成体验依赖 SSE；断线恢复避免丢稿。相对路由与落稿，优先级次之但仍是决策六（SSE + Redis 缓存）的落地。

**Independent Test**: mock SseEmitter 与 Redis，调用 SseStreamingService 逐段推送，断言每段都发出 SSE 帧并写入 Redis 缓存；断线后用 sessionId 从缓存读回已生成内容。可独立验证"流式 + 可恢复"。

**Acceptance Scenarios**:

1. **Given** 一次写作开始，**When** 生成结果逐段到达，**Then** SseStreamingService 将每段作为 SSE 事件推送，并追加写入 Redis `sse:cache:{userId}:{sessionId}`。
2. **Given** 用户 SSE 连接中途断开，**When** 以同一 sessionId 重连，**Then** 能从 Redis 缓存恢复已生成内容。
3. **Given** 生成完成，**When** 推送完成事件，**Then** 前端收到完成信号，缓存含完整内容。

---

### Edge Cases

- 未知写作模式：拒绝，不静默降级（FR-002）。
- SSE 断线：内容可从 Redis 缓存恢复，不丢稿（FR-004）。
- 仿写未检索到参考素材：参考素材为空时仍按需求生成，不报错、不吞（明确行为）。
- 模板必填变量缺失：渲染前校验，拒绝生成残缺稿（FR-007）。
- 模板停用（status != enabled）：拒绝用于新写作，历史稿件不受影响。
- 越权访问他人稿件：详情/编辑/导出一律拒绝（FR-009、FR-011）。
- 空 content / 空 requirement / 空 params：按业务校验拒绝或明确提示。
- 导出空稿件：生成合法空 docx，不抛错。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统 MUST 按写作模式（dialog / rag / template）把请求路由到对应 Agent 配置，路由用明确分支（枚举/switch），不得靠字符串散拼导致写错模式。
- **FR-002**: 系统 MUST 对未知写作模式明确拒绝并返回业务错误，不静默降级为任意模式。
- **FR-003**: 素材仿写（rag）模式 MUST 使用挂载了 ESRetrieveTool 的仿写 Agent，按 user_id 检索参考素材并注入 Prompt；无工具则参考素材无法注入（防"凭空发挥"）。
- **FR-004**: 三类写作的生成过程 MUST 经 SSE 流式逐段返回，流式内容 MUST 写 Redis 缓存（`sse:cache:{userId}:{sessionId}`），支持断线恢复。
- **FR-005**: 稿件保存 MUST 走统一入口，记录 user_id + write_type + title + content +（可选）material_id / template_id。
- **FR-006**: 稿件查询 MUST 分页且仅返回当前用户稿件，按 create_time 倒序。
- **FR-007**: 模板渲染前 MUST 校验 variables 中 required=true 的 key 在参数中存在，缺失则拒绝（防生成残缺稿）。
- **FR-008**: 模板写作 MUST 读取模板配置（含启用状态校验）、按用户参数渲染 structure、组装模板写作 Prompt 后再路由到模板 Agent。
- **FR-009**: 所有业务查询 / 检索 / 稿件保存的 user_id MUST 唯一来源 UserContext，禁止从前端参数取（防越权）。
- **FR-010**: 稿件重新编辑 MUST 把历史稿件作为上下文继续迭代。
- **FR-011**: 稿件详情 / 重新编辑 / 导出 MUST 同时校验 id 与 user_id，防止越权访问他人稿件。
- **FR-012**: 稿件导出 Word MUST 用 POI 生成 docx。

### Key Entities *(include if feature involves data)*

- **稿件（writing_article）**: 一次写作的产物，绑定 user_id（强制隔离），含 article_title / article_content / write_type（dialog|rag|template）/ material_id / template_id，逻辑删除 + 审计时间。由前序第1节交付的 `WritingArticle` + `WritingArticleRepository` 承载，本节在其上构建业务编排。
- **写作请求（DialogReq / RagReq / TemplateReq）**: API 入参，分别承载对话 content、仿写 requirement（+sessionId）、模板 templateId + params。user_id 不来自请求体。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 三个写作入口（对话/仿写/模板）各自路由到正确 Agent 模式；单元测试对三模式路由与未知模式拒绝 100% 覆盖通过（`mvn test` 全绿即达标）。
- **SC-002**: 仿写模式 Agent 的检索工具挂载被回归测试锁死（断言挂 ESRetrieveTool），防止参考素材漏注入。
- **SC-003**: 稿件历史保存、分页查询（仅当前用户）、重新编辑、导出 Word 均有对应验收测试且通过；越权访问他人稿件被测试锁死拒绝。
- **SC-004**: SSE 流式逐段推送与 Redis 缓存（断线恢复）有验收测试且通过。
- **SC-005**: `mvn clean verify`（含 P3C/SpotBugs/FindSecBugs/PMD/Checkstyle/Spotless）全绿。

## Assumptions

- 用户已登录（Token 由前序第3节全局拦截器校验），写作接口在拦截器保护下；user_id 从 UserContext 取。
- 前序第1节已交付 `WritingArticle` 实体与 `WritingArticleRepository`（含 `findByUserIdOrderByCreateTimeDesc`、`findByIdAndUserId`）；第5节已交付 `AgentFactory.create(mode,userId,sessionId)` 与 `WritingAgent.run(...,StreamOutput)`；第6节已交付 `TemplateService.render` 与 `TemplateRepository`；第4节已交付 `MaterialService`。本节在其上新增编排层，不重复造底层。
- SSE 由 writing-agent-core 的 `StreamOutput` 回调抽象承载，本节 `SseStreamingService`（writing-api）负责把回调桥接为 SSE 帧并写 Redis 缓存；writing-agent-core 不依赖 spring-web。
- 素材/模板的 Controller（上传/列表/删除、模板 CRUD、启停）不在本节交付物清单，沿用前序交付的 Service/Repository；本节聚焦三类写作路由 + 稿件历史 + SSE。
- 稿件历史在 business 层需有 API 入口才能被使用：本节新增 ArticleController（writing-api，/api/article：列表/详情/重新编辑/导出/删除），端点路径沿用 CLAUDE.md 接口层定义的固定字面量。

## Clarifications

### Session 2026-10-04

- Q: 第8节对外 REST 面建到什么程度？→ A: A + 稿件 Controller——WritingService(@RestController /api/writing 三端点) + SseStreamingService + ArticleService(business) + ArticleController(/api/article 列表/详情/重新编辑/导出/删除)。素材/模板 Controller 不在本节。
- 稿件导出 Word 用 POI（poi + poi-ooxml，已由 writing-storage 传递依赖到 writing-business），无需新增第三方依赖。
- 敏感配置走环境变量占位；本项目 AgentScope 以自研轻量编排（AgentFactory/WritingAgent）替代外部 SNAPSHOT 依赖，无新增第三方库。
- 测试策略：单测默认跑、集成冒烟打 `@Tag("integration")` CI 跳过；实现完成定义 = `mvn clean verify` 全绿。
