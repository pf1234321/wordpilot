# ScriptAgent 智稿引擎 AI 编程指南

> 本文档定义 ScriptAgent 智稿引擎的 AI 编程实施思路。项目仓库已初始化 **Spec-Kit** 工作区（`.specify/`）并安装了 **speckit-*** 系列 Claude Code Skills（`.claude/skills/`），主体思路是把已有的《完整系统架构设计文档（SDD版）》喂给 Spec-Kit，按 SDD 的 5 个开发阶段拆成 5 个 user story 逐步实施；后续增量阶段切换到手动提示词配合 Claude Code。前置阅读《ScriptAgent 智稿引擎 行业调研》《ScriptAgent 智稿引擎 需求文档》《ScriptAgent 智稿引擎 技术方案》和《ScriptAgent 智稿引擎 - 完整系统架构设计文档（SDD版）》。本文档讲思路和拆解方法，不绑定具体时间安排，也不展开提示词细节。

> 本文档以最新 SDD 为准：系统是 SpringBoot3.x + Java21 单体应用，Maven 模块为 7 个（技术方案第 1.1 节决策八），三类核心写作能力（对话写作、素材仿写 RAG、模板写作）加四类支撑能力（登录鉴权、素材管理、模板管理、稿件历史）作为 5 个 user story 的骨架。

---

## 1. 实施总览

### 1.1 主体思路：Spec-Kit + 手动提示词的混合模式

ScriptAgent 的 AI 编程实施分两个阶段，两个阶段用不同的协作工具：

| 阶段 | 工具 | 适用场景 |
|------|------|---------|
| **主体开发阶段** | Spec-Kit | 从零开发 ScriptAgent 1.0 的五阶段能力，7 个 Maven 模块 |
| **增量开发阶段** | 手动提示词 + Claude Code | 扩展功能、修 bug、加新写作场景等小颗粒度增量 |

这两个阶段的边界很清晰：**Spec-Kit 适合大颗粒度 greenfield，手动提示词适合小颗粒度增量**。ScriptAgent 主体开发是前者，后续迭代是后者，工具选择跟工作性质匹配。

**项目现状**：仓库已初始化 `.specify/` 工作区（constitution、spec、plan、tasks 的目录与模板已就位）并安装了 `speckit-specify`、`speckit-plan`、`speckit-tasks`、`speckit-analyze`、`speckit-constitution`、`speckit-clarify`、`speckit-implement`、`speckit-converge`、`speckit-checklist`、`speckit-taskstoissues` 等 Claude Code Skills。下一步的准备工作是填好 constitution 并跑 specify/plan，而不是重新搭脚手架。

---

### 1.2 跟 SDD 的关系

实施指引不重写需求和技术方案，而是把已有的 SDD 喂给 Spec-Kit。具体对应关系：

| Spec-Kit 输入 | 来源文档 | 作用 |
|--------------|---------|------|
| `/speckit.specify` 输入 | SDD 中的项目介绍、核心业务流程、开发优先级 | Spec-Kit 把 SDD 转成 5 个 user story 的 spec |
| `/speckit.plan` 输入 | SDD 中的模块设计、技术栈、数据库设计 | Spec-Kit 把 SDD 转成模块化的实施 plan |
| `constitution.md` | SDD 中的核心设计原则 + 技术方案第 1.1 节关键技术决策 | 非协商原则 |
| acceptance criteria | SDD 中的五阶段可演示成果 + 需求文档第 12 章验收标准 | 直接复用 |

已有文档的投入不浪费，Spec-Kit 只是把它们转换成 AI agent 能直接消费的格式。

> **关键注意**：技术方案是 `/speckit.plan` 的输入，所以 plan 里的模块结构必须跟技术方案第 2 章的 7 个模块一致，喂文档时确保用的是最新版技术方案，否则生成的 plan 会按错误的模块数拆分。

---

### 1.3 拆解策略：按 user story 拆，不按时间拆

整个 ScriptAgent 主体开发按 **5 个 user story** 组织，每个对应 SDD 的一个开发阶段。

5 个 user story 不是平行的，它们之间有明确的依赖关系，依赖关系决定推进顺序：

| User Story | SDD 阶段 | 能力主线 | 依赖 | 可并行 |
|-----------|---------|---------|------|--------|
| **US-1** | 第一阶段 | 基础底座：Maven 骨架、公共模块、JPA 基础、极简登录、Token 拦截器、Redis/ES 配置 | 无 | — |
| **US-2** | 第二阶段 | 素材 RAG 底座：文件解析、切片、ES 向量入库、检索、素材上传列表 | US-1 | — |
| **US-3** | 第三阶段 | Agent 核心：AgentScope 集成、RedisMemory、ESRetrieveTool、三类写作 Agent | US-2 | — |
| **US-4** | 第四阶段 | 前端业务页面：登录页、三大 Tab 工作台、模板管理、稿件历史 | US-3 | 与 US-3 后半程可并行 |
| **US-5** | 第五阶段 | 联调、优化、收尾：流式输出、异常处理、数据隔离校验、BUG 修复 | US-1 ~ US-4 全部 | — |

**推进顺序**：US-1 → US-2 → US-3 → （US-4）→ US-5

- US-1 是基础，没有骨架和登录，一切能力都无从挂载
- US-2 依赖 US-1，素材入库链路需要 JPA Repository 和 ES 配置就绪
- US-3 依赖 US-2，仿写 Agent 需要 ESRetrieveTool（ES 检索）就绪
- US-4 依赖 US-3 的写作接口，前端工作台调的是后端已完成的能力
- US-5 依赖前 4 个，联调验证全链路

> 具体推进的时间投入由项目方根据团队情况决定。本文档按依赖顺序拆，不规定时长。SDD 定义的 5 个阶段本身就是按此顺序组织的。

这套 user story 拆法跟 Spec-Kit 的机制天然契合。Spec-Kit 的 `/speckit.tasks` 命令本身就是按 user story 组织任务的，每个 user story 成为一个独立的实施 phase，任务之间按依赖排序、可并行的标记出来。

---

## 2. Spec-Kit 跟 ScriptAgent 的匹配度评估

写实施计划之前先回答一个根本问题：Spec-Kit 真的适合 ScriptAgent 项目吗？

### 2.1 Spec-Kit 适合什么场景

Spec-Kit 是 GitHub 开源的 spec-driven development 工具链，是增长最快的开发者工具之一，支持多种 AI coding agent。它把 AI 辅助编码结构化成可重复的 specify → plan → tasks → implement 流程，核心理念是让 spec 成为代码行为的契约和单一事实来源，把 AI agent 从"代码生成器"变成"按规格干活的协作者"，对治 vibe coding。

**社区共识适合的场景：**
- medium 到 large greenfield 项目（从零开发，工程量中到大，模块跨多个文件夹）
- 需求清晰（上游有明确的需求文档或产品决策）
- AI agent 协作（用 Claude Code、Copilot、Cursor 等做主体开发）
- 方法论场景（强制 spec-driven 流程，团队能学到工程方法论）

**不适合的场景：**
- 小 feature、快速原型、单文件改动（流程开销大于收益）
- 大型 brownfield 项目改造（legacy 代码上下文太复杂，超出 LLM context limit）
- 探索性研究项目（需求未定就跑 spec 会反复返工）

### 2.2 ScriptAgent 的匹配度判断

对照 Spec-Kit 适合的场景逐条评估 ScriptAgent：

| 评估维度 | ScriptAgent 情况 | 匹配度 |
|---------|-----------------|--------|
| greenfield | 从零开发的全新项目，不是改造现有代码 | ✅ 完全匹配 |
| 规模 | 7 个 Maven 模块、三类写作能力清晰，典型 medium 规模 | ✅ 完全匹配 |
| 需求清晰 | 已有完整的 SDD + 需求文档 + 技术方案，SDD 本身就定义了 5 个开发阶段 | ✅ 完全匹配 |
| AI agent 协作 | 仓库已装 speckit-* 系列 Claude Code Skills，本来就用 Claude Code 做主体开发 | ✅ 完全匹配 |
| 方法论场景 | Spec-Kit 的强制流程让产出对齐 SDD，对开发者掌握工程方法论很有价值 | ✅ 匹配 |

**结论**：Spec-Kit 是 ScriptAgent 主体开发的最佳工具选择。社区在 brownfield 项目上对 Spec-Kit 有争议，但 ScriptAgent 是纯 greenfield，这些争议不适用。

### 2.3 Spec-Kit 的局限和应对

| 局限 | 描述 | ScriptAgent 的应对 |
|------|------|-------------------|
| 流程对小增量过重 | Spec-Kit 完整流程对小改动开销过大 | ScriptAgent 主体开发是大颗粒度，增量阶段切换到手动提示词 |
| spec 不会自动跟实现同步 | AI agent 在 implement 阶段可能偏离 spec | 每个 user story 实施完成后跑 `/speckit.analyze` 做一致性检查 |
| context limit 在大型 brownfield 上失效 | 十万级文件的 legacy 项目 LLM 看不全 | ScriptAgent 是纯 greenfield，整个项目在 LLM context window 内，不适用 |
| Spec-Kit 本身在快速迭代 | 命令名、artifacts 格式、集成方式都还在变 | 本文档不锁定具体版本细节，具体命令以实施时官方文档为准 |

---

## 3. 准备阶段

准备阶段是正式实施前的脚手架工作，产出三份 Spec-Kit artifacts（constitution、spec、plan），让后续每个 user story 的实施都有清晰的依据。

### 3.1 脚手架现状与 Claude Code 配置

项目仓库已经初始化了 Spec-Kit 工作区：

- `.specify/` 目录已存在（`memory/constitution.md` 模板、templates 目录、workflows 配置、integrations 配置）
- `.claude/skills/` 已安装 `speckit-specify`、`speckit-plan`、`speckit-tasks`、`speckit-analyze`、`speckit-constitution`、`speckit-clarify`、`speckit-implement`、`speckit-converge`、`speckit-checklist`、`speckit-taskstoissues` 等 Skills

当前 `constitution.md` 还是未填写的模板，需要按 3.2 节补齐。具体命令与集成方式（slash 命令还是 skills 模式）以实施时官方文档为准，本文档不锁定细节。

---

### 3.2 `/speckit.constitution`：写 ScriptAgent 项目宪章

`constitution.md` 是项目的 **non-negotiable principles**，所有后续 spec、plan、tasks、implement 都要遵守。ScriptAgent 的 constitution 从 SDD 的核心设计 + 技术方案第 1.1 节关键技术决策提炼：

| # | 原则 | 说明 |
|---|------|------|
| 1 | SpringBoot3.x + Java21 单体应用 | Maven 多模块（7 个），单向依赖、无循环依赖，一条命令打包运行 |
| 2 | Agent 无状态，状态外置 | 会话放 Redis、素材检索放 ES、业务数据放 MySQL；Agent 不持有任何会话状态 |
| 3 | AgentScope 使用边界 | 用 Agent 管理、工具调用、记忆管理抽象；短期会话记忆对接 Redis（RedisMemory）、长期记忆对接 MySQL、检索工具对接 ES（ESRetrieveTool），不引入 AgentScope 默认存储 |
| 4 | 极简登录，不用 SpringSecurity | BCrypt 加密 + UUID Token 存 Redis + 全局拦截器 + UserContext 用户隔离 |
| 5 | **查询强制携带 user_id（最容易被写错的一条）** | 所有业务查询，JPQL/JpaSpecification 必须追加 user_id 条件；Service 层统一控制，禁止不带 user_id 查询；用户数据完全隔离 |
| 6 | JPA 落地规范 | Entity/Repository 统一放 writing-storage；`@CreatedDate` 审计放 writing-common；`@SQLDelete` + `@Where` 逻辑删除；分页用 Pageable；开发 ddl-auto=update、生产手动 SQL |
| 7 | RAG 管线自实现 | POI + PDFBox 解析 → 固定长度 + 重叠切片 → Embedding 向量化 → ES7 批量入库 → 用户维度检索 |
| 8 | **记忆三层分层，存储明确** | 短期记忆（会话历史）落 Redis（RedisMemory）；长期记忆（用户偏好）落 MySQL `writing_memory`（按 user_id 隔离、`save_memory` 回写）；核心记忆（Agent 身份与任务定义）由 Prompt 管理固定注入 system prompt；超长用 AutoContextMemory 压缩（压缩前先抽取长期记忆）。禁止把三层混成一层或塞进 Agent 内部 |
| 9 | 三类写作能力共享一套 Agent 底座 | Agent 工厂按模式生成，Prompt 隔离，SSE 流式输出统一封装 |
| 10 | 每个 user story 完成后有可演示成果 | 优先级是跑通而非完美，按 SDD 五阶段逐段交付 |

> `constitution.md` 写一次定下来，整个主体开发期间不改。如果中途发现某条原则不对，停下来重新讨论，**不允许 AI agent 自己修改 constitution**。

---

### 3.3 `/speckit.specify`：把 SDD 转成 5 个 user story

`/speckit.specify` 命令的输入是 SDD，输出是 5 个 user story 的 spec，每个 user story 对应 SDD 的一个开发阶段。

5 个 user story 按依赖关系排推进顺序，而不是按重要性。这里要特别说明：US-5 联调收尾排在最后实施，是因为它依赖前四个阶段都就绪，**不是因为它不重要**。恰恰相反，联调阶段的"数据隔离校验"是用户隔离这个核心设计的验收环节，重要性很高。本文档不用 P1/P2/P3 这种优先级标记，避免被误读成"靠后的可以不做"，只讲依赖顺序。

每个 user story 的 acceptance criteria 直接复用 SDD 五阶段的可演示成果 + 需求文档第 12 章验收标准：

| User Story | 对应阶段 | 验收要点 |
|-----------|---------|---------|
| US-1 | 第一阶段 | 项目可启动，登录跑通，Token 鉴权生效，数据库自动建表 |
| US-2 | 第二阶段 | 上传文档后素材列表可见，ES 中可检索到切片 |
| US-3 | 第三阶段 | 三种写作模式都能调用 LLM 产出文稿，SSE 流式输出 |
| US-4 | 第四阶段 | 完整前端工作台可用，三 Tab 全流程操作 |
| US-5 | 第五阶段 | 端到端全链路可用，越权访问被拦截，系统稳定 |

`/speckit.specify` 执行后生成 `spec.md`，AI agent 据此理解 ScriptAgent 整体要做什么。跑完后建议跑一次 `/speckit.clarify`，AI agent 会问几个澄清问题（比如切片长度默认值、Token 过期时间、稿件自动保存策略等），这一步可选但推荐。

---

### 3.4 `/speckit.plan`：把技术方案转成实施 plan

`/speckit.plan` 命令的输入是技术方案 + 上一步的 `spec.md` + `constitution.md`，输出是实施 plan。Plan 包含：

- 技术栈选型（SpringBoot3.x + Java21 + Spring Data JPA + AgentScope2.0 + Redis4 + ES7 + MySQL8 + Vue3）
- 7 个 Maven 模块的职责（对照技术方案第 2 章）
- 关键技术决策的展开（Agent 无状态状态外置、AgentScope 使用边界、极简登录、user_id 强制、RAG 管线、SSE 流式输出）
- 数据流和模块间协作（写作路由 → AgentCore → 模型服务 → 存储层）

**Plan 生成后人工 review 是必要环节**。AI agent 可能根据自己对技术方案的理解做了不该做的取舍，重点检查：

- [ ] 有没有把查询改成不带 user_id（必须强制携带，见 constitution 原则五）
- [ ] 有没有引入 SpringSecurity（必须极简登录，见 constitution 原则四）
- [ ] 有没有把 Entity/Repository 散到业务模块（必须统一放 writing-storage，见 constitution 原则六）
- [ ] 有没有把会话状态塞进 Agent 内部（必须 Agent 无状态，状态外置，见 constitution 原则二）
- [ ] 有没有绕过 AgentScope 使用边界（记忆必须对接 Redis、检索必须对接 ES，见 constitution 原则三）

Review 通过后 `plan.md` 锁定。

---

### 3.5 准备阶段交付物清单

准备阶段结束时，ScriptAgent 项目仓库里应该有：

```
.specify/
└── memory/
    └── constitution.md       # 非协商原则集（需补齐）
spec.md                       # 5 个 user story
plan.md                       # 技术栈 + 7 个模块 + 技术决策
docs/
├── IndustryResearch.md       # 行业调研（来源参考）
├── DemandAnalysis.md         # 需求文档（来源参考）
├── TechnicalSolution.md      # 技术方案（来源参考）
├── AiProgrammingGuide.md     # 本文档
└── ScriptAgent智稿引擎 - 完整系统架构设计文档（SDD版）.docx  # 源头文档
```

准备阶段完成后，5 个 user story 的实施依据全部就绪，可以按依赖关系顺序推进。

---

## 4. 基于 Spec-Kit 的实施拆解

准备阶段把整体 spec 和 plan 都准备好了，下面按 5 个 user story 拆解具体实施。每个 user story 的拆解结构一致：核心目标、涉及的 Maven 模块、Spec-Kit 任务拆分思路、关键 task 颗粒度、验收要点。模块名以技术方案第 2 章的 7 模块为准。

---

### 4.1 US-1：基础底座（SDD 第一阶段）

**核心目标**：把项目骨架和地基立起来——Maven 多模块、公共能力、JPA 基础、极简登录、Redis/ES 配置。这是所有后续能力的挂载点。

**涉及的 Maven 模块**：
- `writing-start`（启动聚合）
- `writing-api`（登录接口、Token 拦截器）
- `writing-business`（登录业务）
- `writing-storage`（Entity/Repository、Redis/ES 配置）
- `writing-common`（工具类、加密、用户上下文、JPA 审计监听器）

**Spec-Kit 任务拆分思路**：`/speckit.tasks` 针对 US-1 拆任务，按依赖关系排序，标记可并行任务。预期产出的 task 大类：

| Task 类别 | 主要内容 |
|----------|---------|
| 工程骨架类 | Maven 7 模块骨架、单向依赖、Spring Boot 启动配置 |
| 公共模块类 | 工具类、加密工具（BCrypt）、用户上下文（UserContext）、异常、常量、DTO、枚举、JPA 审计监听器（@CreatedDate 自动填充 create_time） |
| JPA 基础类 | sys_user Entity/Repository、基础 CRUD、分页查询 |
| 登录类 | 登录接口、Token 签发（UUID + Redis）、全局 Token 拦截器 |
| 中间件配置类 | Redis 配置、ES7 配置 |

**关键 task 颗粒度**：几个需要拆细的复杂 task：
- **登录链路**（接口 → 业务 → Repository → Redis → 拦截器，建议拆 2~3 个子 task）
- **JPA 审计配置**（审计监听器放 writing-common、Entity 用 @CreatedDate，拆 1~2 个子 task）

> **再次强调 constitution 原则五**：从 US-1 的第一条查询开始，JPQL/JpaSpecification 就必须携带 user_id，这个习惯要贯穿所有后续 user story。AI agent 写第一个 Repository 查询时就要点明。

US-1 实施完成后跑 `/speckit.analyze` 检查 spec 跟代码一致性。

**验收要点**：项目可启动；admin 用户可登录；Token 签发与拦截生效；未登录请求被拦截；MySQL 自动建表成功。

---

### 4.2 US-2：素材 RAG 底座（SDD 第二阶段）

**核心目标**：把素材从"上传文件"到"可检索切片"的 RAG 地基立起来——解析、切片、向量化、ES 入库、检索。

**涉及的 Maven 模块**：
- `writing-api`（素材上传、素材列表接口）
- `writing-business`（素材业务编排、入库调度）
- `writing-storage`（文档解析、切片工具、ES 操作、writing_material Entity/Repository）
- `writing-model`（Embedding 向量生成，落地用 BGE bge-m3，维度 1024，见技术方案 9.3）

**Spec-Kit 任务拆分思路**：预期产出的 task 大类：

| Task 类别 | 主要内容 |
|----------|---------|
| 文档解析类 | POI 解析 docx、PDFBox 解析 pdf、txt/md 直接读取，格式与大小校验 |
| 切片工具类 | 固定长度 + 重叠切片，切片参数可配置 |
| 向量化类 | Embedding 模型封装（writing-model） |
| ES 操作类 | writing_material_chunk 索引创建、批量入库、检索、按 material_id 清理 |
| 素材业务类 | 上传调度、素材列表分页、素材删除（同步清 ES） |

**关键 task 颗粒度**：
- **文档解析**（三种格式 + 错误处理，建议按格式拆子 task 或统一封装后细测）
- **ES 操作**（索引 mapping 设计 + 批量写入 + 检索，拆 2~3 个子 task）

> **注意 constitution 原则七**：RAG 管线每一步都要"失败即报错、不产生半成品数据"——解析失败不回写 ES，入库失败给明确提示。

US-2 实施完成后跑 `/speckit.analyze`。

**验收要点**：上传 PDF/Word/TXT/MD 后素材列表可见；ES 中能按用户检索到切片；删除素材后 ES 切片同步清理。

---

### 4.3 US-3：Agent 核心能力（SDD 第三阶段）

**核心目标**：把 AI 能力底座立起来——AgentScope 集成、Redis 会话记忆、ES 检索工具、三类写作 Agent。这是系统的核心重点模块。

**涉及的 Maven 模块**：
- `writing-agent-core`（Agent 工厂、RedisMemory、ESRetrieveTool、Prompt 管理、流式输出封装）—— **依赖 writing-model，不直接连接大模型**
- `writing-api`（三类写作接口）
- `writing-business`（写作路由编排）
- `writing-storage`（Redis 会话操作）
- `writing-model`（LLM 对话调用 + Embedding 向量生成，是 AgentCore 的底层依赖、LLM 唯一出口；落地选型：LLM 用阿里云百炼通义千问 Qwen，Embedding 用 BGE bge-m3 本地加载（JVM 内 ONNX Runtime 推理），见技术方案 9.3）

**Spec-Kit 任务拆分思路**：预期产出的 task 大类：

| Task 类别 | 主要内容 |
|----------|---------|
| AgentScope 集成类 | AgentScope2.0 依赖引入、与 Spring Boot 的集成配置 |
| Agent 工厂类 | 按写作模式（对话/仿写/模板）生成 Agent |
| 记忆类 | **三层记忆**：RedisMemory（短期会话，Redis 读写 + TTL，**只存 user 输入 + assistant 最终回复、丢弃推理/tool 中间态，默认最近 10 轮可配置**）；LongTermMemory（长期偏好，MySQL `writing_memory` 读写 + 注入）；核心记忆（Agent 身份与任务定义，Prompt 管理固定注入）；超长上下文压缩（AutoContextMemory，压缩前先抽取长期记忆） |
| 工具类 | ESRetrieveTool：按用户 + 语义检索素材切片 |
| Prompt 管理类 | 三类写作场景 Prompt 隔离与组装；按"核心记忆 → 长期记忆 → 短期记忆"顺序注入 |
| 流式输出类 | SSE 流式封装、Redis 会话缓存 |
| 写作接口类 | Tab1 对话、Tab2 仿写、Tab3 模板三个接口 + 稿件保存 |

**关键 task 颗粒度**：US-3 是 Spec-Kit 拆分的重点。几个需要拆细的复杂 task：
- **AgentScope 集成**（框架接入方式需要摸索，先跑通一个最小闭环：一个 Agent + 一个工具，再扩展）
- **记忆三层**（短期 RedisMemory、长期 MySQL 读写 + `save_memory` 工具、核心记忆 Prompt 注入，建议拆 3~4 个子 task，每层独立可测）
- **三类写作 Agent**（共享底座、配置不同，拆 3 个子 task，每个模式独立可测）
- **流式输出**（SSE + Redis 缓存 + 前端联调，拆 2~3 个子 task）

> **再次强调 constitution 原则二、三、八**：Agent 必须无状态（会话在 Redis、素材在 ES）；记忆对接 Redis/MySQL、检索对接 ES，不引入 AgentScope 默认存储；记忆必须三层分明（短期 Redis / 长期 MySQL / 核心 Prompt），不能把长期记忆塞进短期记忆、也不能把记忆塞进 Agent 内部。AI agent 实现 AgentScope 集成时容易顺手用框架自带存储、或把三层记忆简化成一层，task 里要明确。

US-3 实施完成后跑 `/speckit.analyze`。

**验收要点**：三种写作模式都能调用 LLM 产出文稿；SSE 流式输出正常；多轮对话上下文在 Redis 中可续（短期记忆）；`save_memory` 能写入、对话开始能注入长期记忆（MySQL `writing_memory`，仅当前用户）；仿写能检索到当前用户的素材切片。

---

### 4.4 US-4：前端业务页面（SDD 第四阶段）

**核心目标**：把后端能力变成可操作的前端工作台——登录页、三大写作 Tab、模板管理、稿件历史。

**涉及的模块**：
- 前端工程（Vue3 + Vite + Element Plus + Pinia + Axios + SSE）
- 登录模块、核心工作台（三大 Tab）、素材管理、模板管理、稿件历史

**Spec-Kit 任务拆分思路**：预期产出的 task 大类：

| Task 类别 | 主要内容 |
|----------|---------|
| 工程搭建类 | Vite 工程、Element Plus、Pinia、Axios 拦截器（Token 携带、401 跳转）、路由守卫 |
| 登录页面类 | 登录表单、Token 存储 |
| 工作台类 | Tab1 对话式输入 + SSE 流式渲染；Tab2 素材选择 + 仿写输入；Tab3 模板选择 + 动态表单 |
| 素材管理类 | 上传、列表、预览、删除 |
| 模板管理类 | 新增、编辑、启用停用、变量配置 |
| 稿件历史类 | 列表、查看、重新编辑、导出 Word、删除 |

**关键 task 颗粒度**：
- **SSE 流式渲染**（EventSource / fetch stream 接收增量内容实时渲染，是前端体验核心，拆 2 个子 task）
- **三大 Tab**（互相独立，可并行实现，每个 Tab 一组任务）
- **动态表单**（根据模板变量配置动态生成，与后端模板变量配置联调）

US-4 实施完成后跑 `/speckit.analyze`。

**验收要点**：完整前端工作台可用；三大 Tab 全流程操作跑通；素材/模板/稿件管理页面可用；SSE 流式渲染流畅。

---

### 4.5 US-5：联调、优化、收尾（SDD 第五阶段）

**核心目标**：把整个系统端到端联调打通，重点验证数据隔离、流式输出、异常处理，收尾 BUG。

**涉及的模块**：
- 全部 7 个后端模块 + 前端全部页面

**Spec-Kit 任务拆分思路**：预期产出的 task 大类：

| Task 类别 | 主要内容 |
|----------|---------|
| 数据隔离校验类 | 全链路验证 user_id 强制携带；A 用户无法访问 B 用户素材/稿件/会话 |
| 流式输出优化类 | SSE 断线重连、流式缓存、大稿性能 |
| 异常处理类 | 全局异常处理、LLM 调用失败提示、入库失败回退 |
| 权限校验类 | 未登录拦截、越权访问拦截、文件上传校验 |
| 工程化收尾类 | 日志、配置（开发/生产 ddl-auto 区分）、打包验证、BUG 修复 |

**关键 task 颗粒度**：
- **数据隔离专项**（这是用户隔离设计的验收环节，拆 2~3 个子 task 覆盖素材/稿件/会话三个维度）

> **注意 constitution 原则五的验收**：US-5 要专门验证"查询强制携带 user_id"在所有 Repository 查询上都生效，防止越权。这是整个系统"用户数据完全隔离"承诺的落地检验，不能省。

US-5 完成后跑最后一次 `/speckit.analyze`，整个主体开发完成。

**验收要点**：端到端全链路可用；越权访问被拦截；异常有可理解提示；系统稳定运行；打包部署一条命令可跑。

---

### 4.6 实施过程中的协作模式

5 个 user story 的实施过程中有几个跨 user story 的协作要点：

**`/speckit.analyze` 每个 user story 结束后必跑**

检查 constitution + spec + plan + tasks + 代码是否一致，发现漂移立刻修正，这是 Spec-Kit 防漂移的核心命令，**不能省**。

**AI agent 跑偏 constitution 时主动纠正**

看到 Claude Code 生成的代码不符合 constitution，主动让 AI agent 重读 constitution 改正。ScriptAgent 最容易被写错的几个点：

| 问题 | 正确做法 |
|------|---------|
| 查询漏写 user_id | 必须强制携带，见 constitution 原则五 |
| 引入了 SpringSecurity | 极简登录，BCrypt + Token + 拦截器 |
| Entity/Repository 散到业务模块 | 统一放 writing-storage |
| 会话状态塞进 Agent 内部 | Agent 无状态，会话在 Redis |
| 用了 AgentScope 默认存储 | 记忆对接 Redis/MySQL、检索对接 ES |
| **三层记忆被简化成一层** | 短期 Redis / 长期 MySQL `writing_memory` / 核心 Prompt 注入，必须分明，见 constitution 原则八 |
| 切片参数写死 | 长度/重叠可配置，便于调优 |
| 删除素材没清 ES | 同步清理切片，不留孤儿数据 |

**跨 task 上下文丢失时回到 spec**

Spec-Kit 把代码拆成多个 task 后，AI agent 实施每个 task 时可能不知道前面任务做了什么，定期让它读 `spec.md` + `plan.md` + 最近的代码。

**git commit 标记每个 user story 完成**

方便随时回退到稳定状态。

---

## 5. 项目交付物

主体开发完成后 ScriptAgent 1.0 是一个可演示的完整 AI 写作中台：三类写作能力 + 四类支撑能力全部跑通。除了核心代码本身，还有几个交付物：

### Spec-Kit Artifacts 保留

`.specify/` 目录下的 constitution、spec、plan 在主体开发结束后仍然保留在仓库里，作为后续迭代的长期参考。

### 项目文档

docs/ 目录下的行业调研、需求文档、技术方案、AI 编程指南四份文档与 SDD 一起保留，作为项目档案与后续迭代依据。

### 部署说明

单体应用打包运行说明、开发/生产环境配置说明（ddl-auto 区分、中间件版本要求）。

---

## 6. 增量阶段：手动提示词模式

### 6.1 为什么从 Spec-Kit 切换到手动提示词

主体开发完成后 ScriptAgent 进入增量阶段。这个阶段的工作性质跟主体开发完全不同：

| 维度 | 主体开发 | 增量开发 |
|------|---------|---------|
| 任务颗粒度 | 大（7 个模块同时建） | 小（加一个写作场景、补一个 Bug） |
| 涉及文件数 | 多（跨模块） | 少（通常 1~3 个） |
| 跨模块协作 | 有 | 通常无 |
| 上下文 | 从零设计 | 已有代码 |

这种工作性质下 Spec-Kit 流程过重，跑一次完整的 constitution + specify + plan + tasks + implement 流程，开销大于单次任务的工作量本身。手动提示词配合 Claude Code 更适合：直接打开 Claude Code 描述要做的事，Claude Code 在已有代码上下文里直接修改，改完跑测试没问题就提交，不需要正式的 spec 和 plan artifacts。

### 6.2 增量开发的工作流

增量阶段的典型工作流：

```
1. 团队认领一个需求（新写作场景、扩展功能、修 bug）
2. 打开 Claude Code（或继续用现有开发工具）
3. 描述要做的改动（可引用需求文档/技术方案的对应章节）
4. Claude 在已有代码基础上修改、加测试、跑通
5. Review + 提交
```

这个流程不强制走 Spec-Kit，按团队习惯做就行。对要求严格的大 feature（涉及新增 Maven 模块、改 constitution、跨多个能力）可以选择走 Spec-Kit，但不强制。

### 6.3 跟主体阶段 Spec-Kit Artifacts 的对接

主体阶段产出的 `constitution.md` 和 `spec.md` 在增量阶段仍作为参考文档保留在仓库里：

- **`constitution.md` 仍然是非协商原则**：增量代码必须遵守（SpringBoot3.x + Java21、Agent 无状态、极简登录、user_id 强制、JPA 落地规范、RAG 管线等）
- **`spec.md` 是核心能力的契约**：增量改动某个核心能力时要保证不破坏 spec 里的 acceptance criteria
- **`plan.md` 在主体阶段后基本不再更新**：技术方案文档作为项目参考保留

**新需求的处理方式：**
- 小 feature：直接手动提示词 + 提交
- 大 feature（涉及新增 Maven 模块、改 constitution、跨多个核心能力）：由项目方决定是否单独跑一次 Spec-Kit specify → plan → tasks 流程

---

## 7. 风险和注意事项

### 7.1 Spec-Kit 当前局限

Spec-Kit 还在快速迭代，工具本身变化频繁，使用时几个注意点：

| 注意点 | 说明 |
|--------|------|
| **版本锁定** | 实施前锁定项目已装的 speckit-* Skills / Specify 工具版本，整个主体开发期间不升级，命令名、artifacts 格式、集成方式可能在版本之间变化 |
| **官方文档随时查** | 本文档讲思路 + 节奏，具体命令和安装方式以实施时官方文档为准 |
| **community extension 谨慎用** | 主体开发期间只用官方核心命令，不引入扩展增加不确定性 |

### 7.2 实施过程中的常见挑战

| 挑战 | 对策 |
|------|------|
| **AI agent 跑偏 constitution** | 每次跑完 implement 后人工检查，发现偏离立刻让 AI agent 重读 constitution 修正 |
| **user_id 漏写导致越权** | US-1 起就强制，US-5 专项验收；review 时重点查 JPQL/JpaSpecification |
| **AgentScope 集成踩坑** | US-3 先跑通最小闭环（一个 Agent + 一个工具）再扩展；集成方式不确定时先做技术验证 |
| **跨 user story 的上下文断裂** | 每个 user story 开始前让 AI agent 重读 `spec.md` + `plan.md` + 最近代码 |
| **`/speckit.analyze` 被跳过** | 把 analyze 作为每个 user story 结束的硬性环节，不能省 |
| **SSE 联调问题** | US-3 先与后端流式接口联调通，US-4 再做前端渲染优化；断线重连放 US-5 |
| **Java 工程基础是前提** | 实施前确保团队成员对 Spring Boot + Maven + JPA + Redis/ES 有基本掌握 |

---

## 8. 总结

ScriptAgent 的 AI 编程实施分两个阶段：

### 主体开发阶段（Spec-Kit）

已有的 SDD + 需求文档 + 技术方案喂给 Spec-Kit，转成 constitution + spec + plan + tasks 等 artifacts。项目脚手架（`.specify/` + speckit-* Skills）已就位，准备阶段补好 constitution 即可开工。按 5 个 user story 的依赖关系顺序实施：

```
US-1（基础底座）→ US-2（素材RAG底座）→ US-3（Agent核心）→ US-4（前端页面）→ US-5（联调收尾）
```

每个 user story 完成后有可演示成果，对应 SDD 五阶段的可交付成果与需求文档第 12 章验收标准。

### 增量阶段（手动提示词 + Claude Code）

小颗粒度增量不适合 Spec-Kit 完整流程，团队用 Claude Code 直接在已有代码上做改动，主体阶段产出的 constitution + spec 作为长期参考保留。

---

**Spec-Kit 跟 ScriptAgent 的契合度很高**：纯 greenfield、medium 规模（7 个模块）、需求清晰（SDD 已定义五阶段）、AI agent 协作（speckit-* Skills 已安装）、方法论场景，每条都对得上。

**核心策略是已有文档喂给 Spec-Kit，不重写**。ScriptAgent 已经投入了完整的 SDD + 行业调研 + 需求文档 + 技术方案，这些是 Spec-Kit 的最佳输入。**关键是喂的是最新版文档**：模块是 7 个不是别的数量，constitution 要包含"Agent 无状态状态外置"、"查询强制携带 user_id"、"极简登录不用 SpringSecurity"这些核心决策，否则 Spec-Kit 生成的 plan 会按错误的结构走偏。
