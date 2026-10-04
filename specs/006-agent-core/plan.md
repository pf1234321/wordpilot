# Implementation Plan: Agent 核心与记忆分层（Agent Core and Memory）

**Branch**: `lesson5-agent-core` | **Date**: 2026-10-03 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/006-agent-core/spec.md`

## Summary

按决策一（Agent 无状态）、决策二（AgentScope 使用边界）、决策九（AgentCore 依赖模型服务）实现 Agent 核心底座（writing-agent-core）：`AgentFactory`（按 dialog/rag/template 生成无状态 Agent 配置）；`RedisMemory`（短期会话记忆，包装 `RedisMemoryStore`，只存 user/assistant、拒收中间态、窗口滑出）；`LongTermMemoryService`（长期记忆，包装 `WritingMemoryRepository` 读写 MySQL `writing_memory`，按 user_id 隔离，`save_memory` 工具回写）；`ESRetrieveTool`（检索工具，经 `ModelService.embed` 向量化后 `MaterialChunkRepository.search`，user_id 来源 `UserContext.require()`）；`PromptManager`（三类 Prompt 隔离，按"核心 → 长期 → 短期"组装，核心记忆固定最前）；`StreamingResponseHandler`（流式封装 + Redis 会话缓存回写）；`WritingAgent`/`AutoContextMemory`（编排一次推理，超长**先抽长期记忆再压缩**短期记忆）。记忆三层存储全部落在项目自有 Redis/MySQL/ES（宪法原则三），LLM/Embedding 统一经 `ModelService` 门面（宪法原则三/决策九，不直连大模型）。

## Technical Context

**Language/Version**: Java 21 (Spring Boot 3.3.5)，P3C 门禁 targetJdk=20（代码避免 Java 21-only 语法形态）

**Primary Dependencies**:
- 前序交付物（依赖检查已核实）：`RedisMemoryStore`/`SessionMessage`/`SessionRole`（writing-storage，第 1 节，短期 Redis）；`WritingMemory`/`WritingMemoryRepository.findByUserId`（writing-storage，第 1 节，长期 MySQL）；`MaterialChunkRepository.search(Long,float[],int)`（writing-storage，第 1 节，ES 检索）；`RedisKeys.session/sseCache`、`EsIndexConstants`（writing-common，第 1 节）；`ModelService.chat/embed`、`Message`、`ChatOptions`（writing-model，第 2 节，LLM/Embedding 唯一出口）；`UserContext.require()`（writing-common，第 3 节，user_id 来源）。
- **不新增任何第三方依赖**：AgentScope 占位依赖维持注释（根 pom 与 agent-core pom 的 `io.agentscope:agentscope-core` 均注释，等发布 Maven Central 后启用）；本节以**自有对齐抽象接口**实现 AgentScope 的记忆（`MemoryBase`/`LongTermMemoryBase`）与工具调用契约，存储走项目 Redis/MySQL/ES（宪法原则三，与全项目前序节既定处理一致）。

**AgentScope 契约的落地方式（关键设计决策①，待 Step5 软停点确认）**:
- 定义自有接口 `MemoryBase`（短期记忆：`append(role,content)`/`loadRecent(maxRounds)`）、`LongTermMemoryBase`（长期记忆：`save(...)`/`load(userId)`）、`AgentTool`（工具：`name()`/`execute(query)`），命名对齐 AgentScope 契约；
- 实现均包装前序节存储组件（`RedisMemoryStore`/`WritingMemoryRepository`/`MaterialChunkRepository`），不引 AgentScope 默认存储、不引 jar。
- 若用户希望严格引入 AgentScope jar（需其发布后可达），本节先交付对齐抽象，jar 发布后仅替换接口来源、实现不变。

**流式输出抽象（关键设计决策②，待 Step5 软停点确认）**:
- writing-agent-core 是低于 writing-api 的模块，不依赖 Servlet API。定义 `StreamOutput`（回调抽象：`onToken(String)`/`onComplete()`），`StreamingResponseHandler` 把生成结果逐段推送 + 把 user 输入与 assistant 最终回复写 Redis 会话缓存（短期记忆）。
- 课件 `WritingAgent.run(userId, sessionId, msg, emitter)` 中的 `SseEmitter` 属 spring-webmvc 类型；SSE 端点与前端渲染归第 8 节 API 层（`SseStreamingService`→writing-api，模块落位表），本节以 `StreamOutput` 等价替代，方法名 `run(Long, String, String, StreamOutput)` 保留，关键回归断言不变。

**记忆分层（宪法原则八，本节硬骨头）**:
- 短期（Redis）：`RedisMemory` 只接受 `SessionRole.USER/ASSISTANT`，think/tool 中间态抛 `IllegalArgumentException` 拒收（Redis 长度不变）；`loadRecent(maxRounds)` 返回最近 N 轮（最早在前）；maxRounds 取 `memory.short-term.max-rounds`（默认 10，yml 可配）。
- 长期（MySQL）：`LongTermMemoryService.save(userId, memoryType, content, source)` 强制 user_id；`load(userId)` 按 user_id 查（`WritingMemoryRepository.findByUserId`），量大按最新截断；内置 `SaveMemoryTool`（name=`save_memory`）回写稳定偏好，Agent 不自动抽取。
- 核心（Prompt）：`PromptManager` 固定注入 system prompt 最前，三类模式骨架隔离。
- 压缩：`AutoContextMemory` 当上下文超阈值触发——**先 `longTermMemoryService.save` 落长期记忆，再截断短期记忆**（保序，关键回归）；核心阶段做"简单截断"，LLM 摘要蒸馏为扩展项（TS §5.2）。

**AgentScope 使用边界（宪法原则三）**: AgentFactory 只做"按模式生成 Agent 配置"的编排抽象；记忆→Redis/MySQL、检索→ES，全部项目自有存储；不引 AgentScope 默认存储；LLM/Embedding 统一经 `ModelService`（不持有 `LlmClient`/`EmbeddingClient`）。

**Testing**（harness 先行，方法名英文 + `@DisplayName` 保留课件原文；核心可全 mock，单测秒级）:
- `AgentFactoryTest`（writing-agent-core，单测）：三种模式生成正确配置（dialog 无检索工具 / rag 附 ESRetrieveTool / template 模板 Prompt）；未知模式抛 `IllegalArgumentException`。
- `RedisMemoryTest`（writing-agent-core，单测，mock `RedisMemoryStore`）：**关键回归 `shortTermMemory_storesOnlyDialog_dropsIntermediate`**（think 拒收、只留 user+assistant）；超过 maxRounds 滑出；按时间序。
- `LongTermMemoryServiceTest`（writing-agent-core，单测，mock `WritingMemoryRepository`）：save_memory 写入（带 user_id）；按 user_id 加载；**user_id 隔离**（B 读不到 A）。
- `ESRetrieveToolTest`（writing-agent-core，单测，mock `ModelService`+`MaterialChunkRepository`）：`execute` 经 `ModelService.embed` 向量化 → `search(userId,vec,topK)`；user_id 来源 `UserContext.require()`（`UserContext.set` 注入）；返回 TopN 文本 `\n` 连接。
- `PromptManagerTest`（writing-agent-core，单测）：核心记忆排最前；三类 Prompt 骨架隔离；长期记忆注入；空长期记忆不注入空块。
- `WritingAgentTest`（writing-agent-core，单测，全 mock）：三模式都能经 `ModelService.chat` 产出；**关键回归 `longContext_extractsLongTermBeforeCompress`**（超长触发压缩且压缩前先 `verify(longTermMemoryService).save`）。
- 单测默认跑；integration 冒烟 `@Tag("integration")` CI 跳过；**实现完成的定义是 `mvn clean verify` 全绿**。

**Constraints**:
- 依赖方向：writing-agent-core → writing-model + writing-storage + writing-common（单向、无循环）；**不引入 spring-web/spring-webmvc 到 writing-agent-core**（流式用自有 `StreamOutput` 抽象）。
- user_id 唯一合法来源 = `UserContext`（H4 不变量①）；检索/记忆写入强制 user_id；禁止前端参数取。
- 记忆三层分明：短期 Redis / 长期 MySQL `writing_memory` / 核心 Prompt 注入（H4 不变量⑤ / 宪法八），不混层、不塞进 Agent 内部（宪法二）。
- AgentCore 不直连大模型，LLM/Embedding 统一经 `ModelService`（H4 不变量⑥ / 宪法三 / 决策九）。
- 不新增第三方依赖；不建新表（`writing_memory` 第 1 节已建）；不改前序节已定字面量（`ModelService`/`RedisMemoryStore`/`WritingMemoryRepository`/`MaterialChunkRepository.search`/`UserContext`/`RedisKeys` 原样消费）。
- 凭证/模型路径全 `${ENV_VAR}`（H4 不变量③）；Embedding 恒 1024 维（H4 不变量④，前序回归）。

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 检查 |
|------|------|
| I 单体 7 模块单向依赖 | ✅ 只动 writing-agent-core（新增）+ writing-start（配置），无新模块无循环 |
| II Agent 无状态，状态外置 (NON-NEGOTIABLE) | ✅ AgentFactory 按需生成、Agent 不持有会话状态；会话在 Redis、素材在 ES、业务在 MySQL |
| III AgentScope 边界 / 模型唯一出口 (NON-NEGOTIABLE) | ✅ 记忆→Redis/MySQL、检索→ES（自有存储）；AgentCore 经 `ModelService`，不直连模型；不引 AgentScope 默认存储 |
| IV 极简登录 | 不涉及 |
| V 查询强制 user_id (NON-NEGOTIABLE) | ✅ `LongTermMemoryService`/`ESRetrieveTool` 全部 `UserContext.require()`，前端参数取 user_id 拒绝 |
| VI JPA 规范 | ✅ 复用第 1 节 `WritingMemory`（无新表），逻辑删除 `@SQLDelete` 生效 |
| VII RAG 管线自实现 | ✅ ESRetrieveTool 消费第 4 节检索能力（`MaterialChunkRepository.search`），不重造 |
| VIII 记忆三层分层，存储明确 | ✅ 本节核心：短期 Redis / 长期 MySQL `writing_memory` / 核心 Prompt；压缩先抽长期记忆（关键回归） |
| IX 三类写作共享一套 Agent 底座 | ✅ AgentFactory + 记忆 + ESRetrieveTool + PromptManager + 流式封装，按模式生成，Prompt 隔离 |
| X 可演示成果 | ✅ harness 测试全绿（三模式产出 / 记忆三层 / 隔离 / 压缩顺序），本地真模型/真中间件人工项见验收报告 |

## Project Structure

### Documentation (this feature)

```text
specs/006-agent-core/
├── spec.md              # 本节规格（已产出）
├── checklists/
│   └── requirements.md  # spec 质量检查（16 项全过）
├── plan.md              # 本文件
└── tasks.md             # /speckit-tasks 输出（本 plan 不创建）
```

### Source Code (repository root)

本节交付物全部落在 writing-agent-core（技术方案 §5 落位表）：

```text
writing-agent-core/src/main/java/com/scriptagent/writing/agent/
├── MemoryBase.java                 # 【新增】短期记忆抽象（对齐 AgentScope MemoryBase 契约）
├── RedisMemory.java                # 【新增】短期记忆实现（包装 RedisMemoryStore，拒中间态/窗口滑出）
├── LongTermMemoryBase.java         # 【新增】长期记忆抽象（对齐 AgentScope LongTermMemoryBase 契约）
├── LongTermMemoryService.java      # 【新增】长期记忆实现（包装 WritingMemoryRepository，按 user_id 隔离）
├── AgentTool.java                  # 【新增】工具抽象（对齐 AgentScope 工具调用契约：name/execute）
├── ESRetrieveTool.java             # 【新增】RAG 检索工具（ModelService.embed + MaterialChunkRepository.search，UserContext.require）
├── SaveMemoryTool.java             # 【新增】save_memory 内置工具（回写长期记忆）
├── PromptManager.java              # 【新增】三类 Prompt 隔离 + 核心/长期/短期组装
├── StreamOutput.java               # 【新增】流式输出回调抽象（onToken/onComplete）
├── StreamingResponseHandler.java   # 【新增】流式封装 + Redis 会话缓存回写（短期记忆）
├── AutoContextMemory.java          # 【新增】上下文压缩（先抽长期记忆再截断）
├── WritingAgent.java               # 【新增】一次推理编排（三层记忆→LLM→流式→会话缓存）
└── AgentFactory.java               # 【新增】按模式生成无状态 Agent 配置
```

```text
writing-agent-core/src/test/java/com/scriptagent/writing/agent/
├── AgentFactoryTest.java           # 【新增】三模式配置 + 未知模式抛错
├── RedisMemoryTest.java            # 【新增】只存对话层/滑出/中间态拒收（关键回归）
├── LongTermMemoryServiceTest.java  # 【新增】save/load/隔离
├── ESRetrieveToolTest.java         # 【新增】检索带 user_id + 经 ModelService.embed
├── PromptManagerTest.java          # 【新增】核心最前/三类隔离/长期注入
└── WritingAgentTest.java           # 【新增】三模式产出 + 压缩先抽长期记忆（关键回归）
```

```text
writing-start/src/main/resources/
└── application.yaml                # 【扩展】新增 memory.short-term.max-rounds: 10（及 agent 上下文阈值，如需要）
```

**Structure Decision**: 按技术方案 §5 模块落位表——全部新增归 writing-agent-core（`com.scriptagent.writing.agent` 包，抽象接口 + 实现 + 编排同包）；测试与实现同模块。AgentScope 契约以自有对齐接口落地（决策①）；流式以 `StreamOutput` 抽象落地（决策②），不向 writing-agent-core 引 spring-web。Prompt 骨架内联于 `PromptManager`（Java 常量），不新增资源目录，避免资源类路径管理。

## Complexity Tracking

> 本节无 Constitution 违例需 justify。两项待确认决策（①AgentScope 对齐抽象、②StreamOutput 流式抽象）已写入 Assumptions/Technical Context，为 Step5 软停点软门禁项，待用户确认后实施。
