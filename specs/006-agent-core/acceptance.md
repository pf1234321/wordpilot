# 第 5 节 Agent 核心与记忆分层 — 验收报告

**Branch**: `lesson5-agent-core` | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Tasks**: [tasks.md](./tasks.md)
**完成日期**: 2026-10-04 | **执行方式**: `wordpilot-lesson-dev` 全程流程（H0→specify→clarify→plan→tasks 停点→implement→六项证据 DoD）

---

## 一、六项证据 DoD

### 证据 1 — `mvn clean verify` 全绿（P3C 组合门禁）

```
命令: BGE_MODEL_PATH=/Users/Administrator/bge-m3 mvn clean verify
Reactor Summary:
  wordpilot .................. SUCCESS
  writing-common ............. SUCCESS
  writing-model .............. SUCCESS
  writing-storage ............ SUCCESS
  writing-agent-core ......... SUCCESS   ← 本节模块
  writing-business ........... SUCCESS
  writing-api ................ SUCCESS
  writing-start .............. SUCCESS
BUILD SUCCESS | Total time: 35.679s
```
- SpotBugs / FindSecBugs：agent-core `BugInstance size=0, Error size=0`。
- Checkstyle：`You have 0 Checkstyle violations`。
- Spotless / PMD / dependency-check 全过（PMD 规则集兼容 warning 为项目既有、跨模块共有，非本节引入）。

### 证据 2 — 课件 harness 映射表测试类存在且非空，关键回归逐个对号

| harness 测试类 | 落地文件 | 测试数 | 结果 |
|---|---|---|---|
| `AgentFactoryTest` | `writing-agent-core/src/test/.../AgentFactoryTest.java` | 4 | ✅ 全绿 |
| `RedisMemoryTest` | `.../RedisMemoryTest.java` | 3 | ✅ 全绿 |
| `LongTermMemoryServiceTest` | `.../LongTermMemoryServiceTest.java` | 4 | ✅ 全绿 |
| `PromptManagerTest` | `.../PromptManagerTest.java` | 7 | ✅ 全绿 |
| `WritingAgentTest` | `.../WritingAgentTest.java` | 4 | ✅ 全绿 |
| （补充）`ESRetrieveToolTest` | `.../ESRetrieveToolTest.java` | 3 | ✅ 全绿 |
| **合计** | | **25** | **0 Fail / 0 Error** |

**关键回归对号**（`@DisplayName` 保留课件原文）：
- `shortTermMemory_storesOnlyDialog_dropsIntermediate`：append(think) 被丢弃、只落 user+assistant（RedisMemoryTest ✅）
- `longContext_extractsLongTermBeforeCompress`：超长触发压缩、压缩前先 `verify(longTermMemoryService).save`（WritingAgentTest ✅）

### 证据 3 — "本节交付物"逐项存在性核对

writing-agent-core `com.scriptagent.writing.agent` 全部 13 个交付类 + 6 个测试类已 ls 核对存在（AgentFactory / MemoryBase / RedisMemory / LongTermMemoryBase / LongTermMemoryService / AgentTool / ESRetrieveTool / SaveMemoryTool / PromptManager / StreamOutput / StreamingResponseHandler / AutoContextMemory / WritingAgent）。

### 证据 4 — 前序节全部测试回归绿

`mvn clean verify` 全模块（common/model/storage/business/api/start）SUCCESS，第 1~4 节既有测试全部通过，跨节契约（`ModelService`/`RedisMemoryStore`/`WritingMemoryRepository`/`MaterialChunkRepository.search`/`UserContext` 原样消费）回归绿。

### 证据 5 — H4 六条全局不变量自查

| # | 不变量 | 自查结果 |
|---|---|---|
| ① 所有业务查询/检索强制 user_id（来源 UserContext，前端参数取拒绝） | ✅ `ESRetrieveTool`/`SaveMemoryTool` 一律 `UserContext.require()`；`LongTermMemoryService.save` 强制 user_id |
| ② 逻辑删除 `@SQLDelete`/审计 `@CreatedDate` 生效 | ✅ 复用第 1 节 `WritingMemory`（无新表），`@SQLDelete` 由实体自带，未触碰 |
| ③ grep 无明文 key/密码/模型路径（全 `${ENV_VAR}`） | ✅ agent-core 源码 grep 无明文 key/模型路径（确认输出 "OK"） |
| ④ Embedding 恒 1024 维、ES dim=1024 + cosine | ✅ agent-core 无硬编码 1024；dim 由 `EsIndexConstants`/ES mapping 管辖（前序回归） |
| ⑤ Agent 无状态、状态外置；短期 Redis / 长期 MySQL / 核心 Prompt 三层分明 | ✅ `AgentFactory` 按需生成、`WritingAgent` 仅持 mode/tool 配置不持会话；RedisMemory（短期 Redis）/ LongTermMemoryService（长期 MySQL writing_memory）/ PromptManager（核心 Prompt）分明 |
| ⑥ AgentCore 不直连大模型，LLM/Embedding 统一经 writing-model 出口 | ✅ agent-core 仅依赖 `ModelService`，grep 无 `LlmClient`/`EmbeddingClient`/`RestClient`/`DashScope` 直连（唯一命中为 javadoc 说明不直连） |

### 证据 6 — 验收报告 + 剩余人工项

本报告即证据 6。以下人工项已由 harness 判卷确认无代码问题，需你真模型/真中间件人工验收（见下节）。

---

## 二、变更总结（给 reviewer 导读）

### 改动点

| 模块 | 文件 | 类型 | 动机 |
|---|---|---|---|
| writing-agent-core | `pom.xml` | 修改 | 补 `spring-boot-starter-test`（test scope），对齐 writing-business 测试依赖 |
| writing-agent-core | `agent/{MemoryBase,RedisMemory,LongTermMemoryBase,LongTermMemoryService,AgentTool,ESRetrieveTool,SaveMemoryTool,PromptManager,StreamOutput,StreamingResponseHandler,AutoContextMemory,WritingAgent,AgentFactory}.java` | 新增 ×13 | 第5节交付物：Agent 底座 + 三层记忆 + 检索工具 + Prompt 管理 + 流式封装 + 编排（decision①②确认落地） |
| writing-agent-core | `agent/{AgentFactoryTest,RedisMemoryTest,LongTermMemoryServiceTest,ESRetrieveToolTest,PromptManagerTest,WritingAgentTest}.java` | 新增 ×6 | 课件 harness + ESRetrieveTool 补充测试（25 用例） |
| writing-start | `application.yaml` | 修改 | 新增 `agent.context.max-rounds: 10`（压缩阈值）；`memory.short-term.max-rounds` 前序已存在 |
| specs | `006-agent-core/{spec,plan,tasks,acceptance}.md` + `checklists/requirements.md` | 新增 ×5 | 本节 spec 文档 |

**前序节文件触碰**：仅 `writing-start/src/main/resources/application.yaml`（加配置）；`ModelService`/`RedisMemoryStore`/`WritingMemoryRepository`/`MaterialChunkRepository`/`UserContext`/`RedisKeys` 等公共接口**零修改**（原样消费）。

### 重点 review 清单（按风险排序）

1. **AgentScope 契约落地方式（决策①）**：定义自有 `MemoryBase`/`LongTermMemoryBase`/`AgentTool` 接口对齐 AgentScope 契约，实现包装项目自有 Redis/MySQL/ES（`RedisMemoryStore`/`WritingMemoryRepository`/`MaterialChunkRepository.search`），不引 jar、不引 AgentScope 默认存储（宪法三）。→ `agent/MemoryBase.java`、`agent/AgentTool.java`、`agent/AgentFactory.java`
2. **流式输出抽象（决策②）**：以 `StreamOutput`（onToken/onComplete/onError）回调替代课件 `SseEmitter`，agent-core 不引 spring-web；SSE 端点归第 8 节 API 层。→ `agent/StreamOutput.java`、`agent/WritingAgent.java#run`
3. **关键回归保真**：`shortTermMemory_storesOnlyDialog_dropsIntermediate`（中间态"丢弃"而非抛错，与课件断言一致）与 `longContext_extractsLongTermBeforeCompress`（压缩前先落长期记忆）原样落地、`@DisplayName` 留课件原文。→ `agent/RedisMemoryTest.java`、`agent/WritingAgentTest.java`
4. **压缩启发式（简单截断 + 先抽长期记忆）**：`AutoContextMemory.compress` 先 save 最早 user 指令为长期记忆再截断（核心阶段不做 LLM 摘要，TS §5.2 扩展项）。→ `agent/AutoContextMemory.java`
5. **三层记忆分明 / user_id 强制**：短期 Redis、长期 MySQL、核心 Prompt；检索/回写全部 `UserContext.require()`。→ `agent/RedisMemory.java`、`agent/LongTermMemoryService.java`、`agent/ESRetrieveTool.java`
6. **多实例无状态**：`AgentFactory.create` 每次按需生成 `RedisMemory(userId,sessionId)`，`WritingAgent` 不持跨会话状态。→ `agent/AgentFactory.java`

### 如何验证（可直接复制执行）

```bash
# 全量门禁（P3C 组合，8 模块 SUCCESS）
BGE_MODEL_PATH=/Users/Administrator/bge-m3 mvn clean verify
# 只跑本节测试
mvn -pl writing-agent-core test
# 关键回归单测
mvn -pl writing-agent-core test -Dtest='RedisMemoryTest,WritingAgentTest'
# 依赖方向 grep（agent-core 不直连模型）
grep -rn 'LlmClient\|EmbeddingClient\|RestClient\|DashScope' writing-agent-core/src/main || echo '无直连'
```
预期：全量 verify BUILD SUCCESS；本节 25 用例全绿；依赖方向 grep 无命中。

### 待你人工确认的剩余人工项（harness 已判卷，这几项需真模型/真中间件）

1. **三种写作模式都能调 LLM 产出文稿，SSE 流式**（需 DashScope key 启动 + 第 8 节 API 层 SSE 端点桥接；本节已交付编排与 `StreamOutput` 回调，链路待 API 层打通后联调）。
2. **让模型"以后都写 800 字"，下个会话它还记得（长期记忆生效）**（需 MySQL 运行 + 对话走 `save_memory` 回写后再起新会话注入）。
3. **一个会话聊很久，观察短期记忆只留最近 10 轮**（需 Redis 运行，`memory.short-term.max-rounds=10`，超过窗口滑出）。
4. **换模型只改模型服务层，Agent 层代码不动**（agent-core 仅依赖 `ModelService`，可改 `application.yaml` 的 `model.llm` 验证 Agent 层无感）。
5. **越权隔离**：A 用户无法检索/读到 B 的素材与长期记忆（ES filter user_id + `UserContext.require()`，第 8 节联调阶段专项验收）。

---

**结论**：harness 已判卷（25 用例全绿、关键回归保真、`mvn clean verify` 全绿、H4 六条不变量自查通过）。剩余人工项如上，等你真模型/真中间件人工过。commit/push 由你决定（本节不自动提交）。
