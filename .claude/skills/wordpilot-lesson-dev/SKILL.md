---
name: wordpilot-lesson-dev
description: >-
  按课件驱动开发 ScriptAgent 的一节模块：输入节号（1~8），自动完成备料 → speckit-specify →
  clarify → plan → tasks（停等确认）→ implement → 节级验收报告，全程施加硬/软两类门禁，
  保证产出严格贴合 docs/TechnicalSolution.md 的 ScriptAgent 设计。当用户说「开发第 N 节 /
  实现第 N 节课的模块 / 用 spec kit 做第 N 节」时使用。
argument-hint: "节号（1~8），如：1"
user-invocable: true
---

# ScriptAgent 逐节开发 Skill

## User Input

```text
$ARGUMENTS
```

节号来自上面的输入。设计依据见 `docs/TechnicalSolution.md`（技术方案）与 `.specify/memory/constitution.md`（宪章）、`CLAUDE.md`（开发指南）；每节课件的生成模板见 `docs/class/生成讲义提示词.md`。本 skill 是它们的可执行版本，条款以本文为准执行，不需要复读那些文档给用户。

## 总纪律（贯穿全程）

> **能机器判的绝不留给人，机器判不了的绝不自行发挥。**

- **硬门禁**：测试、`mvn clean verify`（前端课为 `vite build` + 冒烟）、存在性核对——不过不放行，不许绕。
- **软门禁**：遇到下列任一情况，**立即停下、向用户报告、等确认后继续**，不得自行决定：
  1. 需要创建"本节交付物"清单之外的任何对外概念（public 类型、配置键、数据表、REST 路径、Agent 模式名）；
  2. 需要修改任何已定字面量（类名、方法签名、配置键、表列名、端点路径、ES 索引名）；
  3. 课件与 `docs/TechnicalSolution.md` 冲突；
  4. 需要修改前序节交付的公共接口（当节课件明确列为"改造点"的除外）；
  5. 第三方 API 在本地依赖中核实不到；
  6. 需要新增 plan 未列明的第三方依赖。
- **反作弊**：不得删断言、`@Disabled`、放宽阈值让测试变绿。实现错修实现；认为测试错，停下报告。未全绿不得宣称完成。
- 全程**不自动 commit / push**，同步时机由用户决定。

---

## 第 0 步：输入校验与课型分流

解析节号 N，按下表分流；表外输入直接报错退出。

| N | 课型 | 处理 |
|---|---|---|
| 1,2,3,4,5,6,8 | 代码课（Java 后端） | 走完整流程（第 1~7 步） |
| 7 | 前端课 | 走前端分支：备料/specify/plan/tasks/implement 相同，但硬门禁与第 7 步验收用 `vite build` + 前端冒烟，**不用 mvn verify** |
| 其他 | — | 报错：仅支持 1~8 |

**节号即开发顺序**（讲义已按 SDD 五阶段重排）：1 存储层 → 2 模型服务 → 3 登录鉴权 → 4 素材 RAG → 5 Agent 核心 → 6 模板引擎 → 7 前端工作台 → 8 三类写作编排。跳节开发前必须确认前序节已完成（第 1 步依赖检查会拦）。

## 第 1 步：H0 开工纪律——必读与依赖检查

1. **读三样**（用 Read 完整读，不凭记忆）：
   - 当节课件：`docs/class/第{N}节*.md`（glob 匹配）；
   - `docs/TechnicalSolution.md` 对应章节（映射表见下）；
   - 前序各代码课课件的"本节交付物"小节。
2. **依赖存在性检查**：对前序每节交付物清单里的核心类，在代码库里 grep/Glob 确认存在。任何缺失 → 停下报告"先做第 X 节"，不得跳节自造。
3. **分支**：确认当前在该节的 feature 分支上（speckit-specify 的 before_specify hook 会建分支；若 hook 未配置，手动 `git checkout -b lesson{N}-<slug>`），不在 main/主干上直接开发。

**技术方案章节映射表：**

| 节 | 讲义 | TechnicalSolution 章节 |
|---|---|---|
| 1 | 存储层 | §8（§8.1~8.4，JPA/Redis/ES/切片） |
| 2 | 模型服务 | §9（§9.1/9.2/9.3，LLM Qwen + BGE 本地 ONNX） |
| 3 | 登录与鉴权 | §3（§3.1/3.2/3.3）+ 流程一 |
| 4 | 素材 RAG 管线 | §6 + 流程二/三 |
| 5 | Agent 核心与记忆分层 | §5（§5.1/5.2/5.3） |
| 6 | 模板引擎 | §7（§7.1/7.2/7.3） |
| 7 | 前端工作台 | §10 |
| 8 | 三类写作编排 | §4（§4.1~4.4）+ 流程四/五 |

## 第 2 步：组装并执行 /speckit-specify

用 Skill 工具调用 `speckit-specify`，参数按此骨架从**当节课件的一、二部分**提炼（只写 WHAT/WHY，不带类名和技术栈）：

```text
第{N}节需求：<模块名>——<一句话定位>
背景与价值。<为什么需要，从课件"是什么"部分提炼>
用户场景。<2~3 个具体场景>
功能需求。FR1~FRn <从课件"想清楚"部分提炼，每条可测试>
明确不做（边界）。<课件"二、动手前先想清楚"里的坑与不做项逐项照搬>
验收标准。可自动化部分由课件"验收 harness"测试套件承载（mvn test 全绿即通过），
  关键回归点：<列出课件 harness 里写出代码的那几个测试的守点>；
  人工项见课件"五、做完怎么验"。
依赖与假设。<指向前序节交付物；外部依赖如 AgentScope/BOM/中间件版本>
```

## 第 3 步：/speckit-clarify

调用 `speckit-clarify`。有问题答问题（答案只从课件和技术方案找，找不到 → 软门禁停下问用户）；无问题继续。

## 第 4 步：组装并执行 /speckit-plan

参数 = 固定技术栈句 + 本节模块落位 + 测试策略句 + 语法禁区：

**固定技术栈句**：`JDK 21 + Spring Boot 3.x + Spring Data JPA（Hibernate）+ AgentScope2.0 + Redis4 + ES7 + MySQL8 + POI/PDFBox。凭证（DashScope key、BGE 模型路径、数据库密码）走环境变量占位，不落明文。MySQL 生产不依赖 hibernate.ddl-auto=update（手动 SQL）；Embedding 用本地 BGE bge-m3 + JVM 进程内 ONNX Runtime 推理（dim=1024，ES 索引 dim 一致 + cosine）；所有业务查询/检索强制 user_id（来源 UserContext，禁止从前端参数取）。`

**模块落位表**（照抄本节行，细节以 TechnicalSolution §8 为准）：

| 节 | 落位 |
|---|---|
| 1 | `*Entity`/`*Repository`（sys_user/writing_material/writing_article/writing_memory）→writing-storage；RedisTemplate 操作封装→writing-storage；ES 索引/检索（MaterialChunkRepository/TemplateRepository）→writing-storage；切片工具→writing-storage；JPA 审计监听器→writing-common |
| 2 | `LlmClient`/`EmbeddingClient`/`ModelService`/`ModelProperties`→writing-model |
| 3 | `LoginService`→writing-business；`TokenInterceptor`→writing-api；`UserContext`→writing-common |
| 4 | `DocumentParser`/`ChunkSplitter`/`MaterialChunkRepository`→writing-storage；`EmbeddingClient`→writing-model；`MaterialService`→writing-business |
| 5 | `AgentFactory`/`RedisMemory`/`LongTermMemoryService`/`ESRetrieveTool`/`PromptManager`/`StreamingResponseHandler`→writing-agent-core |
| 6 | `TemplateService`→writing-business；`TemplateRepository`→writing-storage；前端动态表单→前端 |
| 7 | 前端目录（Vue3+Vite+Element Plus+Pinia+Axios+SSE）：登录页、三大 Tab 工作台、素材/模板/稿件管理 |
| 8 | `WritingService`（路由）→writing-api/business；`ArticleService`→writing-business；`SseStreamingService`→writing-api |

**测试策略句**（从课件"验收 harness"抄）：`测试策略按课件"验收 harness"执行：<测试类清单>（覆盖 <关键回归点>），单测默认跑、集成冒烟打 @Tag("integration") CI 跳过；实现完成的定义是 mvn clean verify 全绿。前端课：实现完成的定义是 vite build 通过 + 冒烟全绿。`

**语法禁区句**：`避开 P3C/ASM 解析不了的 Java 18+ 语法形态（如增强 switch 的 default -> 写法），静态检查是构建门禁。`

## 第 5 步：/speckit-tasks + 固定软停点

1. 调用 `speckit-tasks`。
2. **自动比对**：任务清单 ↔ 课件"本节交付物"（代码/测试/配置/表逐项），并确认测试任务先于或伴随对应实现任务（harness 先行）。
3. 输出比对结果（齐 / 缺什么 / 多什么），**停下等用户确认**后才进入下一步。这是流程中唯一的固定停点，不许跳过。

## 第 6 步：/speckit-analyze（建议跑）+ /speckit-implement

implement 期间逐任务执行，附加门禁：

- **写前**（H3）：涉及第三方 API 的任务，先在本地依赖核实方法存在；核实不到 → 软门禁。
- **写中**（H1/H5）：只创建交付物点名的对外概念；已定字面量逐字保真；异常不吞（catch 必落审计/日志或上抛）；不建文档外抽象层；注释只写"为什么"。**测试方法名必须是英文**（驼峰或 snake_case，如 `deleteMaterial_removesAllEsChunks`），不得用中文方法名；课件 harness 里若给出中文方法名，翻译成语义等价的英文名落地，并用 `@DisplayName` 保留课件原文以便对号。
- **写后**（任务级 DoD）：实现与测试一起落地，跑该模块测试，红了当场修，不攒到最后。
- 课件"验收 harness"里**写出代码的关键回归测试必须原样落地**（断言逻辑逐条保真；方法名按上条规则译成英文，课件原文进 `@DisplayName`）。

## 第 7 步：节级收尾——六项证据 DoD

全部满足才可宣布本节完成，逐项把证据写进验收报告：

1. `mvn clean verify` 全绿（含 P3C/SpotBugs/FindSecBugs/PMD），贴关键输出；（前端课改为 `vite build` 通过 + 冒烟全绿）
2. 课件 harness 映射表的每个测试类存在且非空，关键回归测试逐个对号；
3. "本节交付物"逐项 ls/grep 存在性核对；
4. **前序节全部测试回归绿**（跨节契约证据）；
5. **H4 六条全局不变量逐条自查**：①所有业务查询/检索强制 user_id（来源 UserContext，前端参数取 user_id 一律拒绝）②逻辑删除 `@SQLDelete`+`@Where`、审计 `@CreatedDate` 生效 ③grep 无明文 key/密码/模型路径（全 `${ENV_VAR}`）④Embedding 恒 1024 维、ES 索引 dim=1024 + cosine ⑤Agent 无状态、状态外置，短期记忆只存对话层、长期记忆 MySQL、核心记忆 Prompt ⑥AgentCore 不直连大模型，LLM/Embedding 统一经 writing-model 出口；
6. 验收报告收尾：以上证据 + 课件"做完怎么验"的**剩余人工项清单**（真模型/真中间件/SSE 冒烟等），明确告知用户"harness 已判卷，这几项等你人工过"。
7. **变更总结（给 reviewer 的导读，直接输出在对话里，不另开文件）**——以 `git status --short` / `git diff --stat` 实测为准，三段固定结构：
   - **改动点**：按模块分组列新增 / 移动（改名）/ 修改 / 删除的文件，每处一句话说明动机；前序节文件被本节触碰的（哪怕只改 import）单独标出；
   - **重点 review 清单**：按风险排序 3~6 条——架构决策（依赖方向、契约变化）优先，其次课件骨架同构性（逐行对照点）、跨节契约兼容性、静态门禁妥协点；每条给出文件行级定位；
   - **如何验证**：可直接复制执行的命令块（全量门禁、只跑本节测试、关键回归单测、依赖方向 grep 等），每条命令注明预期结果；最后重复剩余人工项。

报告完停止——commit/push 由用户决定。
