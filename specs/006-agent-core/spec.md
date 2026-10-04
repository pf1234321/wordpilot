# Feature Specification: Agent 核心与记忆分层（Agent Core & Memory）

**Feature Branch**: `lesson5-agent-core`
**Created**: 2026-10-03
**Status**: Draft
**Input**: User description: 第5节需求：Agent 核心（writing-agent-core）——ScriptAgent 的 AI 能力底座，落在决策一（Agent 无状态）、决策二（AgentScope 使用边界）、决策九（依赖模型服务）上。全权封装 AgentScope2.0 的"Agent 管理 / 工具调用 / 记忆管理"抽象，把"生成一个能对话、能查素材、能按模板写的写作 Agent"统一起来，三类写作（对话/仿写/模板）共享这一套底座。记忆分层（§5.2，核心记忆/长期记忆/短期记忆三层）是本节硬骨头。

## 用户场景与测试

### User Story 1 — Agent 工厂按模式生成无状态写作 Agent（P1）

`AgentFactory` 根据写作模式（dialog / rag / template）生成对应的无状态 Agent 配置：三类共享同一套底座（记忆 + Prompt 管理 + 模型出口），只差 Agent 配置；Agent 不持有任何会话状态，任意时刻新建实例都能继续处理对话。未知模式抛明确错误。

**Why this priority**：这是"三类写作共享一套 Agent 底座"（宪法原则九）与"Agent 无状态"（决策一）的直接落地；是其余所有 US 的入口。未知模式必须显式报错而非静默降级。

**Independent Test**：`AgentFactoryTest` 断言三种模式生成正确配置（对话无检索工具、仿写带检索工具、模板为模板 Prompt），未知模式抛 `IllegalArgumentException`。

**Acceptance Scenarios**：

1. **Given** 模式 `dialog`，**When** `agentFactory.create("dialog", userId, sessionId)`，**Then** 生成对话 Agent 配置（对话 Prompt + 短期/长期/核心记忆 + 模型出口，无检索工具）。
2. **Given** 模式 `rag`，**When** `create("rag", userId, sessionId)`，**Then** 生成仿写 Agent 配置（附 ESRetrieveTool）。
3. **Given** 模式 `template`，**When** `create("template", userId, sessionId)`，**Then** 生成模板 Agent 配置（模板 Prompt）。
4. **Given** 未知模式 `foo`，**When** `create("foo", ...)`，**Then** 抛 `IllegalArgumentException("未知写作模式")`。
5. **Given** 同一 userId/sessionId 连续创建多个实例，**When** 每次新建 Agent，**Then** Agent 内部不持有上次会话状态（无状态，状态全在 Redis/MySQL/ES）。

---

### User Story 2 — 短期会话记忆：Redis 只存对话层、窗口滑出、中间态拒收（P1）

`RedisMemory` 对接记忆抽象（短期记忆），把多轮对话上下文落到 Redis `session:{userId}:{sessionId}`。只存 `user` 输入 + `assistant` 最终回复（对话层消息），**丢弃 think/plan/reasoning 与 tool 中间态**；默认保留最近 10 轮（yml 可配 `memory.short-term.max-rounds`），超过窗口的最早轮次滑出。

**Why this priority**：宪法原则八"短期记忆只存对话层、丢中间态、窗口滑出"的落地；存中间态会冗余又危险，是课件明确列的坑。

**Independent Test**：`RedisMemoryTest` 断言只存 user/assistant、超过 max-rounds 滑出、think/tool 中间态不写入。

**Acceptance Scenarios**：

1. **Given** 依次写入 `think`/`user`/`assistant` 三条，**When** `loadRecent(10)`，**Then** 只返回 user+assistant 两条（think 被忽略）。
2. **Given** 一个会话已积累超过 maxRounds 轮，**When** `loadRecent(maxRounds)`，**Then** 只返回最近 maxRounds 轮，最早轮次被滑出。
3. **Given** 对话历史按时间顺序追加，**When** 读取，**Then** 返回按时间顺序（最早在前）的消息列表。
4. **Given** 会话结束（TTL 过期 / 显式清空），**When** 再读取，**Then** 短期记忆被清理（会话级失效）。

---

### User Story 3 — 长期记忆：用户偏好写 MySQL、按 user_id 隔离、对话开始注入（P1）

`LongTermMemoryService` 对接长期记忆抽象，读写 MySQL `writing_memory`（按 user_id 隔离）。对话中识别到稳定偏好时通过内置工具 `save_memory` 回写（Agent 不自作主张猜、系统不自动抽取）；每次对话开始按 user_id 加载并注入 system prompt；量大时按最新/高频截断。

**Why this priority**：宪法原则八"长期记忆存 MySQL writing_memory、按 user_id 隔离、save_memory 回写"；跨轮要长期保持的指令（如"以后都写 800 字"）必须走长期记忆，不能靠短期窗口。

**Independent Test**：`LongTermMemoryServiceTest` 断言 save_memory 写入、按 user_id 加载、user_id 隔离（B 用户读不到 A 的记忆）。

**Acceptance Scenarios**：

1. **Given** 对话中出现稳定偏好"以后都写 800 字"，**When** Agent 经 `save_memory` 回写，**Then** MySQL `writing_memory` 新增一条（携带 user_id、memory_type、content）。
2. **Given** 某用户已有长期记忆，**When** 新会话开始 `load(userId)`，**Then** 返回该用户全部长期记忆，注入 system prompt。
3. **Given** 用户 A 有记忆而用户 B 无，**When** `load(userB)`，**Then** 返回空（user_id 隔离，读不到 A 的数据）。
4. **Given** 长期记忆条数超限，**When** 注入，**Then** 按最新/高频截断，不撑爆上下文。

---

### User Story 4 — ESRetrieveTool：按 user_id 语义检索参考素材（P2）

`ESRetrieveTool` 对接工具调用抽象，封装 ES 检索：写作时按 user_id + 语义相似度在 `writing_material_chunk` 索引检索相似切片，返回 TopN 切片文本作为参考素材注入 Prompt。检索强制带 user_id（来源 `UserContext`，禁止从前端参数取），只召回当前用户的素材。向量化经 `ModelService.embed`（writing-model 唯一出口），不直连模型。

**Why this priority**：宪法原则三"检索工具对接 ES"、原则五"user_id 强制"、决策九"LLM/Embedding 经模型服务出口"在 Agent 工具侧的落地。

**Independent Test**：`ESRetrieveToolTest` 断言按 user_id 检索（filter 强制）、经 `ModelService.embed` 向量化后调 `MaterialChunkRepository.search` 返回 TopN 文本。

**Acceptance Scenarios**：

1. **Given** 当前用户有已入库素材切片，**When** `execute(query)`，**Then** 经 embed 向量化 → `search(userId, vec, topK)` → 返回 TopN 切片文本。
2. **Given** 检索发生，**When** 工具执行，**Then** user_id 来源 `UserContext.require()`，绝不从前端参数取。
3. **Given** 目标用户无任何素材，**When** `execute(query)`，**Then** 返回空参考素材（不抛错）。

---

### User Story 5 — Prompt 管理：核心记忆排最前、三类 Prompt 隔离、长期记忆注入（P2）

`PromptManager` 三类写作 Prompt 隔离与组装，按"核心记忆 → 长期记忆 → 短期记忆"顺序注入 system prompt。核心记忆（Agent 身份/任务定义/Prompt 骨架）固定排在最前面、不被压缩清除；对话/仿写/模板三种 Prompt 结构互不串扰。

**Why this priority**：宪法原则八"核心记忆固定注入最前、不被压缩"、原则九"Prompt 隔离"的落地；组装顺序决定了记忆是否有效注入。

**Independent Test**：`PromptManagerTest` 断言核心记忆排最前、三类 Prompt 结构不同、长期记忆被注入。

**Acceptance Scenarios**：

1. **Given** 组装对话 Prompt，**When** 生成 system prompt，**Then** 核心记忆在开头，其后为（注入的）长期记忆，最后为对话历史。
2. **Given** 三种模式，**When** 各自组装 Prompt，**Then** 三者的 system prompt 骨架互不相同（对话无参考素材、仿写含参考素材、模板含模板文本与参数）。
3. **Given** 长期记忆为空，**When** 组装，**Then** Prompt 不含长期记忆段（不注入空块）。

---

### User Story 6 — WritingAgent 编排 + 上下文压缩：超长先抽长期记忆再压缩（P2）

`WritingAgent` 编排一次推理：组装核心/长期/短期记忆 → 调模型服务 LLM → 流式逐段输出 → 写 Redis 会话缓存。短期记忆超长时 `AutoContextMemory` 压缩——**先抽取长期记忆再压缩短期记忆**（AgentScope 官方强调的顺序），保证有价值信息不因压缩丢失。

**Why this priority**：宪法原则八"压缩前先抽取长期记忆"；这是记忆分层正确运转的关键回归点，是课件最值钱的测试。

**Independent Test**：`WritingAgentTest` 断言三模式都能产出；超长触发压缩且压缩前先调 `longTermMemory.save`（verify 顺序）。

**Acceptance Scenarios**：

1. **Given** 一次对话推理，**When** `run(userId, sessionId, userMessage, ...)`，**Then** 注入三层记忆 → 调 `ModelService.chat` → 产出文稿 → 写 Redis 会话缓存。
2. **Given** 短期记忆超长（超上下文阈值），**When** 触发压缩，**Then** 先执行 `longTermMemory.save(...)` 落长期记忆，再压缩短期记忆（保序断言）。
3. **Given** 三种模式，**When** 各自走 `WritingAgent.run`，**Then** 都能经模型服务产出文稿（三模式共享同一编排）。

---

### Edge Cases

- **未知写作模式**：`AgentFactory.create` 抛 `IllegalArgumentException("未知写作模式")`，不静默降级。
- **中间态误写**（think/plan/tool）：短期记忆拒收（抛 `IllegalArgumentException` 或忽略，本节取"拒绝写入 + Redis 长度不变"），只存 user/assistant。
- **越权读取长期记忆**：`load(userId)` 强制按 user_id 查询，B 用户读不到 A 的记忆（隔离）。
- **检索越权**：`ESRetrieveTool` 的 user_id 一律 `UserContext.require()`，前端参数传 user_id 一律拒绝。
- **压缩顺序**：必须先落长期记忆再压缩短期记忆，否则有价值信息随压缩丢失。
- **长期记忆为空 / 短期记忆为空**：Prompt 组装不注入空块、编排正常走通（不抛错）。

---

## 功能需求（FR，从课件"想清楚"提炼，每条可测试）

- **FR1 Agent 工厂（AgentFactory → writing-agent-core）**：`create(String mode, Long userId, String sessionId)` 按 dialog/rag/template 生成对应 Agent 配置；未知模式抛 `IllegalArgumentException("未知写作模式: " + mode)`；Agent 不持有会话状态（无状态，状态外置）。
- **FR2 短期会话记忆（RedisMemory → writing-agent-core）**：对接记忆抽象（短期记忆），包装存储层 `RedisMemoryStore`，key=`session:{userId}:{sessionId}`；只接受 user/assistant 对话层消息，think/tool 中间态拒收；`loadRecent(maxRounds)` 返回最近 N 轮（最早在前），超过窗口滑出；maxRounds 取 yml `memory.short-term.max-rounds`（默认 10）。
- **FR3 长期记忆（LongTermMemoryService → writing-agent-core）**：对接长期记忆抽象，包装 `WritingMemoryRepository`（MySQL `writing_memory`）；`save(userId, memoryType, content, source)` 写入（强制 user_id）；`load(userId)` 按 user_id 加载、量大按最新/高频截断；内部工具 `save_memory` 走此服务，Agent 不自动抽取偏好。
- **FR4 ES 检索工具（ESRetrieveTool → writing-agent-core）**：对接工具调用抽象，`execute(String query)` → `ModelService.embed(query)` 向量化（唯一出口）→ `MaterialChunkRepository.search(userId, vec, topK)`；user_id 来源 `UserContext.require()`（禁止前端参数）；返回 TopN 切片文本 `\n` 连接。
- **FR5 Prompt 管理（PromptManager → writing-agent-core）**：三类 Prompt 隔离；按"核心记忆 → 长期记忆 → 短期记忆"顺序组装 system prompt；核心记忆（身份/任务定义/Prompt 骨架）固定注入最前、不被压缩清除。
- **FR6 流式封装 + 会话缓存（StreamingResponseHandler → writing-agent-core）**：把生成结果封装为流式逐段输出；同时写 Redis 会话缓存（`sse:cache:{userId}:{sessionId}`）；对话层 user 输入 + assistant 最终回复回写短期记忆。
- **FR7 编排 + 压缩（WritingAgent / AutoContextMemory → writing-agent-core）**：`run(userId, sessionId, userMessage, output)` 编排一次推理——注入三层记忆 → 调 `ModelService.chat` → 流式输出 → 写会话缓存；短期记忆超长触发压缩，**压缩前先抽长期记忆**（保序）。
- **FR8 配置（writing-start application.yaml）**：新增 `memory.short-term.max-rounds: 10`（短期窗口，可配置），`agent.*` 相关 Prompt/上下文阈值按需可配。

## 明确不做（边界，逐项照搬课件"想清楚"）

- **不直接连接任何大模型**（红线/决策九）：AgentCore 推理统一经 `ModelService`（writing-model）发请求，不持有 `LlmClient`/`EmbeddingClient`；换模型只改模型服务层，Agent 层无感。
- **不做 REST / Controller / SSE 端点**（对话/仿写/模板三类写作的 REST 入口与 `SseStreamingService` 属 API 层，第 8 节写作编排交付；本节只交付 writing-agent-core 的组件与编排能力）。
- **不引入 AgentScope 默认存储**（宪法原则三）：短期 Redis / 长期 MySQL / 检索 ES，全部落在项目自有存储，不引 AgentScope 自带存储方案。
- **不做 LLM 摘要压缩**（技术方案 §5.2："核心阶段可先做简单截断，LLM 摘要压缩放扩展阶段"）：AutoContextMemory 本节先做"简单截断"（保留最近 N 轮）+ 压缩前先抽长期记忆；LLM 蒸馏摘要为扩展项。
- **不做完整原始对话归档**（审计追溯为扩展阶段）。
- **不新增第三方依赖**（AgentScope 占位依赖维持注释，等发布 Maven Central 后启用；本节以自有对齐抽象实现 AgentScope 的记忆/工具契约，存储走项目 Redis/MySQL/ES）。
- **不修改前序节已定字面量**：`ModelService`、`RedisMemoryStore`、`WritingMemoryRepository`、`MaterialChunkRepository.search`、`UserContext`、`RedisKeys` 等公共接口原样消费，不改签名（如确需调整，属软门禁，先停下与用户确认）。

## 依赖与假设

- **前序交付物（依赖检查已核实存在）**：
  - 第 1 节：`RedisMemoryStore`（短期会话 Redis，append/loadRecent/expire/clear，TTL 30min）、`SessionMessage`/`SessionRole`、`WritingMemory` 实体 + `WritingMemoryRepository`（findByUserId/findByUserIdAndMemoryType，长期记忆 MySQL）、`MaterialChunkRepository.search(userId, vec, topK)`（ES 检索）、`EsIndexConstants`、`RedisKeys`（session/sseCache）；
  - 第 2 节：`ModelService.chat/embed`（LLM/Embedding 唯一出口，恒 1024 维）、`Message(role, content)`、`ChatOptions`；
  - 第 3 节：`UserContext.require()`（当前登录用户，强制携带 user_id 的来源）。
- **外部依赖**：Redis4（会话/TTL）、MySQL8（writing_memory）、ES7（writing_material_chunk）、DashScope（Qwen LLM）、BGE bge-m3（本地 ONNX，第 2 节已配）；凭证走环境变量。
- **假设**：流式输出以回调/输出抽象（`StreamOutput`）在 writing-agent-core 内落地，不依赖 Servlet API（SSE 端点与前端渲染属第 8 节 API 层）；AgentScope 依赖保持注释占位，以自有对齐抽象实现其 MemoryBase / LongTermMemoryBase / 工具调用契约（与全项目前序节的既定处理一致）。
