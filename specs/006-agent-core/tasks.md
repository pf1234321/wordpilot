# 第 5 节 Agent 核心与记忆分层 — 任务清单（/speckit-tasks）

**Branch**: `lesson5-agent-core` | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

实现完成定义：`BGE_MODEL_PATH=/Users/Administrator/bge-m3 mvn clean verify` 全绿（P3C 组合门禁）。

## 前置（已就绪）

- 分支 `lesson5-agent-core` 已建（基于 `lesson4-material-rag` tip，干净基线，不含第 6 节模板引擎改动）
- 课件 / TechnicalSolution §5 + 记忆分层已读；前序 1/2/3 节交付物存在性已核对（`RedisMemoryStore`/`WritingMemoryRepository`/`MaterialChunkRepository.search`/`ModelService`/`UserContext`，见 spec.md 依赖节）
- 不新增第三方依赖（AgentScope 占位依赖维持注释，以自有对齐抽象实现契约）

## 任务清单

| # | 模块 | 任务 | 验证 |
|---|---|---|---|
| T001 | writing-agent-core | 新建 `agent/MemoryBase.java`：短期记忆抽象接口，对齐 AgentScope MemoryBase 契约——`void append(String role, String content)` / `List<SessionMessage> loadRecent(int maxRounds)`（课件主角二接口签名保真） | 编译通过 |
| T002 | writing-agent-core | 新建 `agent/LongTermMemoryBase.java`：长期记忆抽象接口，对齐 AgentScope LongTermMemoryBase 契约——`void save(Long userId, String memoryType, String content, String source)` / `List<String> load(Long userId)` | 编译通过 |
| T003 | writing-agent-core | 新建 `agent/AgentTool.java`：工具调用抽象接口，对齐 AgentScope 工具契约——`String name()` / `String execute(String query)` | 编译通过 |
| T004 | writing-agent-core | 新建 `agent/RedisMemoryTest.java`（harness，单测，mock `RedisMemoryStore`，直接构造 `RedisMemory(userId, sessionId, store, maxRounds)`）：①**关键回归 `shortTermMemory_storesOnlyDialog_dropsIntermediate`**（`@DisplayName` 保留课件原文"shortTermMemory_storesOnlyDialog_dropsIntermediate：只存 user+assistant、丢弃 think/tool 中间态"——append("think",...) 抛 `IllegalArgumentException` 拒收、只落 user+assistant）②`loadRecent` 超过 maxRounds 最早轮次滑出 ③按时间序返回 | 单测绿 |
| T005 | writing-agent-core | 新建 `agent/RedisMemory.java`（实现 `MemoryBase`，非 Spring bean、按会话构造）：构造 `RedisMemory(Long userId, String sessionId, RedisMemoryStore store, int maxRounds)`；`append(role, content)`——仅 `user`/`assistant` 映射 `SessionRole` 调 `store.append`，`think`/`tool` 等中间态抛 `IllegalArgumentException` 拒收；`loadRecent(maxRounds)` → `store.loadRecent(userId, sessionId, maxRounds)`（窗口滑出） | 编译通过 |
| T006 | writing-agent-core | 新建 `agent/LongTermMemoryServiceTest.java`（harness，单测，mock `WritingMemoryRepository`）：①`save` 写入（ArgumentCaptor 断言 user_id/memory_type/content/source 落对）②`load(userId)` 按 user_id 加载并返回 content 列表 ③**user_id 隔离**（`load(B)` 与 A 无关——repo 收到的是 B 的 userId）④量大按最新截断 | 单测绿 |
| T007 | writing-agent-core | 新建 `agent/LongTermMemoryService.java`（实现 `LongTermMemoryBase`，`@Service`，注入 `WritingMemoryRepository`）：`save(userId, memoryType, content, source)`——强制 user_id 写入（`WritingMemory` 实体，source 记 `save_memory`/`auto-compress`）；`load(userId)`——`findByUserId(userId)` 按 user_id 加载、量大按最新截断、返回 content 列表 | 编译通过 |
| T008 | writing-agent-core | 新建 `agent/SaveMemoryTool.java`（实现 `AgentTool`，`@Component`，注入 `LongTermMemoryService`）：`name()="save_memory"`；`execute(content)`——`UserContext.require()` 取 user_id → `LongTermMemoryService.save(userId, "other", content, "save_memory")` 回写稳定偏好（Agent 不自作主张猜、系统不自动抽取） | 编译通过 |
| T009 | writing-agent-core | 新建 `agent/ESRetrieveToolTest.java`（harness，单测，mock `ModelService`+`MaterialChunkRepository`，`UserContext.set` 注入）：①`execute(query)` 先 `ModelService.embed` 再 `repo.search(userId, vec, topK)`（ArgumentCaptor 断言 query→embed→search 链）②user_id 来源 `UserContext.require()`（不从前端参数取）③返回 TopN 文本 `\n` 连接 ④无素材时返回空串不抛错 | 单测绿 |
| T010 | writing-agent-core | 新建 `agent/ESRetrieveTool.java`（实现 `AgentTool`，`@Component`，注入 `ModelService`+`MaterialChunkRepository`）：`name()="es_retrieve"`；`execute(query)`——`Long userId = UserContext.require()`（H4 不变量①）→ `float[] vec = modelService.embed(query)`（唯一出口，不直连 EmbeddingClient）→ `repo.search(userId, vec, topK)` → `String.join("\n", hits)` | 编译通过 |
| T011 | writing-agent-core | 新建 `agent/PromptManagerTest.java`（harness，单测）：①核心记忆排最前（`buildSystemPrompt` 开头是 core 骨架）②三类 Prompt 骨架隔离（dialog/rag/template 的 `corePrompt` 互不相同）③长期记忆注入（非空时含长期记忆块）④空长期记忆不注入空块 | 单测绿 |
| T012 | writing-agent-core | 新建 `agent/PromptManager.java`（`@Component`）：`corePrompt(String mode)`——dialog/rag/template 三类身份+任务定义骨架（Java 常量内联）；`buildSystemPrompt(String mode, List<String> longTerm)`——核心记忆固定最前，其后按需接长期记忆块（空则不接）；`assembleMessages(String mode, List<String> longTerm, List<SessionMessage> history, String userInput, List<String> referenceMaterials)`——system(core+长期+仿写参考素材) + history(user/assistant 消息) + 当前 userInput，返回 `List<Message>` | 编译通过 |
| T013 | writing-agent-core | 新建 `agent/StreamOutput.java`（流式输出回调抽象）：`void onToken(String)` / `void onComplete()` / `void onError(Throwable)`（agent-core 不依赖 spring-web，SSE 端点第 8 节 API 层桥接） | 编译通过 |
| T014 | writing-agent-core | 新建 `agent/StreamingResponseHandler.java`（`@Component`，注入 `RedisMemoryStore`+`RedisKeys`）：`emit(Long userId, String sessionId, String userMessage, String assistantReply, StreamOutput output)`——把 assistantReply 经 `output.onToken` 逐段推送 + `output.onComplete`；把 user 输入 + assistant 最终回复追加到 Redis 会话缓存（短期记忆，`store.append` user/assistant）与 `sse:cache:{userId}:{sessionId}` | 编译通过 |
| T015 | writing-agent-core | 新建 `agent/AutoContextMemory.java`（`@Component`，注入 `LongTermMemoryService`）：`int maxRounds`（`@Value("${agent.context.max-rounds:10}")`）；`boolean shouldCompress(int rounds)`（超阈值）；`List<SessionMessage> compress(Long userId, List<SessionMessage> history)`——**先**把最早 user 指令抽取为长期记忆（`LongTermMemoryService.save(userId, "habit", directive, "auto-compress")`，压缩前先抽长期记忆、不因压缩丢失），**再**截断保留最近 `maxRounds` 轮 | 编译通过 |
| T016 | writing-agent-core | 新建 `agent/WritingAgentTest.java`（harness，单测，全 mock `PromptManager`/`LongTermMemoryService`/`RedisMemory`/`ModelService`/`AutoContextMemory`/`StreamingResponseHandler`）：①三模式（dialog/rag/template）都经 `ModelService.chat` 产出（rag 会先 `esRetrieveTool.execute` 取参考素材）②**关键回归 `longContext_extractsLongTermBeforeCompress`**（`@DisplayName` 保留课件原文——short-term 超长触发压缩，`verify(longTermMemoryService).save(...)` 先于压缩发生；用低 `maxRounds` 的 `AutoContextMemory` 注入确定性触发）③`run` 后会话缓存回写（user+assistant 已持久化） | 单测绿 |
| T017 | writing-agent-core | 新建 `agent/WritingAgent.java`（一次推理编排，按会话构造、无状态）：`run(Long userId, String sessionId, String userMessage, StreamOutput output)`——`longTermMemory.load(userId)` → `redisMemory.loadRecent(maxRounds)` → 超长则 `autoContextMemory.compress(userId, history)`（先抽长期记忆）→ rag 模式 `esRetrieveTool.execute(userMessage)` 取参考素材 → `promptManager.assembleMessages(...)` → `modelService.chat(messages)` → `streamingResponseHandler.emit(userId, sessionId, userMessage, result, output)` → 返回 result | 编译通过 |
| T018 | writing-agent-core | 新建 `agent/AgentFactoryTest.java`（harness，单测）：①dialog 生成无检索工具的 WritingAgent ②rag 生成附 ESRetrieveTool 的 WritingAgent ③template 生成模板 Prompt 的 WritingAgent ④未知模式抛 `IllegalArgumentException("未知写作模式: " + mode)` | 单测绿 |
| T019 | writing-agent-core | 新建 `agent/AgentFactory.java`（`@Component`，注入 `PromptManager`/`LongTermMemoryService`/`RedisMemoryStore`/`ModelService`/`AutoContextMemory`/`StreamingResponseHandler`/`ESRetrieveTool`）：`WritingAgent create(String mode, Long userId, String sessionId)`——`switch(mode)` dialog→无工具 / rag→附 ESRetrieveTool / template→模板 Prompt（`default` 抛 `IllegalArgumentException("未知写作模式: " + mode)`）；每次按需生成新 `RedisMemory(userId, sessionId, store, maxRounds)`，不持有会话状态 | 编译通过 |
| T020 | writing-start | 扩展 `application.yaml`：新增 `memory.short-term.max-rounds: 10`（短期窗口可配，课件点名）；如需要 `agent.context.max-rounds`（压缩阈值）一并落 yml | 配置加载正确 |
| T021 | 全部 | 硬门禁：`BGE_MODEL_PATH=/Users/Administrator/bge-m3 mvn clean verify` 全绿（P3C 组合，8 模块 SUCCESS，前序节回归绿） | 全量 verify SUCCESS |
| T022 | specs | 验收报告 `specs/006-agent-core/acceptance.md`（技能第 7 步六项证据 DoD + 剩余人工项） | 报告完成 |

## 软停点：任务清单 ↔ 课件"本节交付物"自动比对

| 课件交付物（§三代码 + §四 harness） | 落地 | 说明 |
|---|---|---|
| `AgentFactory`（按 dialog/rag/template 生成 Agent 配置，未知模式抛错） | ✅ T018 + T019 | switch 三模式 + default 抛 `IllegalArgumentException` |
| `RedisMemory`（短期记忆，对接 MemoryBase） | ✅ T004 + T005 | 只存 user/assistant、拒中间态、窗口滑出、TTL 由 `RedisMemoryStore` |
| `LongTermMemoryService`（长期记忆，对接 LongTermMemoryBase） | ✅ T006 + T007 | save_memory 回写、按 user_id 加载、user_id 隔离 |
| `ESRetrieveTool`（检索工具，封装 ES） | ✅ T009 + T010 | 经 `ModelService.embed` 向量化 → `MaterialChunkRepository.search`，user_id 来源 `UserContext` |
| `PromptManager`（三类 Prompt 隔离，核心记忆固定注入） | ✅ T011 + T012 | 核心最前 / 三类隔离 / 长期注入 / 空块不注入 |
| `StreamingResponseHandler`（SSE 流式封装 + Redis 会话缓存） | ✅ T013 + T014 | 流式回调 + user/assistant 回写短期记忆 |
| `WritingAgent`（一次推理编排） | ✅ T016 + T017 | 三层记忆→LLM→流式→会话缓存 |
| `AutoContextMemory`（超长压缩，先抽长期记忆） | ✅ T015 | 压缩前先 `longTermMemoryService.save`（关键回归） |
| harness：`AgentFactoryTest` | ✅ T018 | 三模式正确配置 + 未知模式抛错 |
| harness：`RedisMemoryTest` | ✅ T004 | **关键回归 `shortTermMemory_storesOnlyDialog_dropsIntermediate`** 原样落地（断言逻辑保真，`@DisplayName` 留课件原文） |
| harness：`LongTermMemoryServiceTest` | ✅ T006 | save_memory 写入 / 按 user_id 加载 / 隔离 |
| harness：`PromptManagerTest` | ✅ T011 | 核心记忆排最前 / 三类 Prompt 隔离 / 长期记忆注入 |
| harness：`WritingAgentTest` | ✅ T016 | 三模式都能产出 + **关键回归 `longContext_extractsLongTermBeforeCompress`**（压缩前先落长期记忆） |
| （补充）`ESRetrieveToolTest` | ✅ T009 | 检索带 user_id + 经模型服务 embed（ESRetrieveTool 为本节点名交付物，补测试锁死） |
| （补充）`AgentTool`/`MemoryBase`/`LongTermMemoryBase` 抽象 | ✅ T001 + T002 + T003 | AgentScope 契约的自有对齐抽象（决策①，见 plan） |
| （补充）`SaveMemoryTool` | ✅ T008 | `save_memory` 内置工具回写长期记忆（课件点名机制） |
| （补充）`StreamOutput` 抽象 | ✅ T013 | 流式输出回调，等价替代 `SseEmitter`（决策②，agent-core 不引 spring-web） |
| （补充）`StreamingResponseHandler`/`AutoContextMemory` 落点 | ✅ T014 + T015 | 模块落位表点名 → writing-agent-core |
| （补充）`application.yaml` 配置 | ✅ T020 | `memory.short-term.max-rounds: 10` |

**比对结论**：任务清单与课件交付物**一致**（不缺、无文档外新增对外概念——新增均为 writing-agent-core 内部类型或前序已交付组件消费；无已定字面量改动——`ModelService`/`RedisMemoryStore`/`WritingMemoryRepository`/`MaterialChunkRepository.search`/`UserContext`/`RedisKeys` 原样消费，前序节文件除 `application.yaml` 外零触碰）。测试任务（T004/T006/T009/T011/T016/T018）**先行/伴随**对应实现（T005/T007/T010/T012/T017/T019）。**课件 harness 关键回归断言逻辑逐条保真，方法名译英文 + `@DisplayName` 保留课件原文。**

> ⚠️ **两项软门禁项，需用户确认后才实施**：
> ① **AgentScope 契约落地**：AgentScope jar 未发布 Maven Central（依赖注释占位），本节以自有对齐抽象（`MemoryBase`/`LongTermMemoryBase`/`AgentTool`）实现其契约、存储走项目 Redis/MySQL/ES——与全项目前序节既定处理一致。
> ② **流式输出抽象**：writing-agent-core 不引 spring-web，以 `StreamOutput` 回调等价替代课件 `SseEmitter`；SSE 端点归第 8 节 API 层。
