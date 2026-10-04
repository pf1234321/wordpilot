# Research: 三类写作编排（008）

Phase 0 输出——本节无外部未知依赖（前序交付物已在 H0 逐一核实），research 收敛为四项设计决策的论证。

## 决策 ①：SSE 桥接放在 writing-api

- **Decision**: `SseStreamingService`（writing-api）持有 `SseEmitter`，把 writing-agent-core 的 `StreamOutput` 回调转成 SSE 帧并写 Redis `sse:cache:{userId}:{sessionId}`。
- **Rationale**: writing-agent-core 不依赖 spring-web（第 5 节 plan 决策②显式声明，`StreamOutput` 是回调抽象以保持模块解耦）；`SseEmitter` 属 spring-webmvc，只能在 api 层使用。SSE 桥接天然归属接口层，符合"编排层不越界"。
- **Alternatives considered**: 在 agent-core 引 spring-web 直接返回 SseEmitter——被宪法模块单向依赖与解耦约束否决。

## 决策 ②：模板写作编排在 WritingService.template 完成

- **Decision**: `WritingService.template` = `TemplateRepository.findByIdAndUserId(templateId, userId)` → 校验 `status=enabled` → `TemplateService.render(tpl, params)`（含必填校验）→ 渲染后 Prompt 作为 userMessage 交模板 Agent。
- **Rationale**: 技术方案 §4.3/§7.2 与流程五把"读模板→填参→组装 Prompt→模板 Agent→落稿"串在写作编排层；`TemplateService.render` 已是纯函数（第 6 节），`TemplateRepository` 自带 user_id 隔离。ES 受检异常转 `BusinessException` 以触发事务回滚。
- **Alternatives considered**: 让 Agent 内部读模板——违背"编排层协调依赖、Agent 无状态"；被否决。

## 决策 ③：sessionId 前端可传、缺失服务端生成

- **Decision**: dialog/rag/template 请求可携带 `sessionId`（多轮对话前端回传续上下文）；缺失由服务端 `UUID.randomUUID()` 生成。
- **Rationale**: 多轮对话需要跨轮固定 sessionId 写 Redis 会话；首轮可由服务端生成，后续轮前端回传，满足"对话写作多轮迭代"。`user_id` 一律 `UserContext.require()`，不来自请求体。
- **Alternatives considered**: 固定服务端生成并返回——需额外返回通道，且前端续轮无凭据续上下文；被否决。

## 决策 ④：稿件 API 面纳入本节（用户澄清确认）

- **Decision**: 新增 `ArticleController`（writing-api，`/api/article`：列表/详情/重新编辑/导出/删除），`ArticleService`（writing-business）提供统一落稿/分页/重新编辑上下文/导出。
- **Rationale**: ArticleService 在 business 层无 API 则不可达；用户 Step3 明确选择"A + 稿件 Controller"。端点路径沿用 CLAUDE.md 接口层字面量，非新增概念。
- **Alternatives considered**: 只建 ArticleService 不建 Controller（严格最小）——稿件无 API 入口、不完整；用户已否决。素材/模板 Controller 超出本节聚焦，明确不在本节。

## 决策 ⑤：导出 Word 用 POI XWPFDocument

- **Decision**: `ArticleService.exportWord` 用 `XWPFDocument`（标题段 + 正文段）生成 docx，返回 `byte[]`。
- **Rationale**: POI `poi`+`poi-ooxml` 已由 writing-storage compile 依赖传递到 writing-business，无需新增依赖；导出在服务端异步处理，大稿不阻塞主流程（TS §13）。
- **Alternatives considered**: 引入独立导出库——增加不必要依赖；被否决。
