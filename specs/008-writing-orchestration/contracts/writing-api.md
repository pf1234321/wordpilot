# REST Contracts: 三类写作编排（008）

三类写作端点（`/api/writing`）与稿件端点（`/api/article`）。所有端点均在 Token 拦截器保护下，`user_id` 取自 `UserContext`，请求体不含 user_id。

## WritingService —— 三类写作（writing-api）

### POST `/api/writing/dialog` — 一句话多轮对话写作

- **Request** `DialogRequest`: `{ "content": "帮我写一篇新品推文", "sessionId": "可选，多轮回传续上下文" }`
- **Response**: `text/event-stream`（SSE）——`onToken` 逐段推文稿、完成后 `onComplete`；内容同时写 `sse:cache:{userId}:{sessionId}`。
- **语义**: 路由到对话 Agent（无检索工具），user_id = `UserContext.require()`；sessionId 缺失由服务端生成。

### POST `/api/writing/rag` — 素材仿写（RAG）

- **Request** `RagRequest`: `{ "requirement": "按公司年报风格写周报", "sessionId": "可选" }`
- **Response**: `text/event-stream`（SSE），同上。
- **语义**: 路由到仿写 Agent（挂 `ESRetrieveTool`），Agent 内按 user_id 检索参考素材注入 Prompt；参考素材为空时仍按需求生成。

### POST `/api/writing/template` — 模板写作

- **Request** `TemplateRequest`: `{ "templateId": "tpl-weekly-001", "params": { "title": "...", "week_work": "..." }, "sessionId": "可选" }`
- **Response**: `text/event-stream`（SSE），同上。
- **语义**: 读 ES 模板（user_id 隔离）→ 校验 `status=enabled` → `TemplateService.render`（含必填项校验，缺失拒绝）→ 渲染后 Prompt 交模板 Agent。模板不存在 / 越权 / 停用 → 业务错误。

### 通用错误（写作文式 / 模板 / 稿件）

- 未知写作文式：业务错误（FR-002，不静默降级）。
- 模板不存在 / 越权 / 停用：业务错误。
- SSE 生成异常：`onError` 帧 + emitter 结束。

## ArticleController —— 稿件历史（writing-api）

### GET `/api/article` — 稿件列表（分页，仅当前用户）

- **Query**: `page`（从 0 起）、`size`（默认 10）。
- **Response**: 分页结果，`create_time` 倒序，仅当前用户；每项含 `write_type` 标注。

### GET `/api/article/{id}` — 稿件详情

- **Response**: 稿件详情（含 `articleContent`）；越权（非本人 id）→ 按不存在处理。

### POST `/api/article/{id}/reedit` — 重新编辑（返回历史稿件作为继续迭代上下文）

- **Response**: `ReeditResponse`（`articleTitle` + `articleContent` + `writeType` 等），前端加载后继续写作；越权 → 按不存在处理。

### POST `/api/article/{id}/export` — 导出 Word

- **Response**: `application/vnd.openxmlformats-officedocument.wordprocessingml.document`，`Content-Disposition: attachment; filename=article-{id}.docx`；越权 → 按不存在处理。

### DELETE `/api/article/{id}` — 删除稿件（逻辑删除）

- **Response**: 删除成功；越权 → 按不存在处理。
