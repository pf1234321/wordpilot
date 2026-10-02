<!--
Sync Impact Report
- Version change: (none) → 1.0.0  (initial ratification)
- Bump rationale: First formal adoption of the ScriptAgent constitution. MAJOR baseline.
- Principles defined (10):
    I.   SpringBoot3.x + Java21 单体应用，Maven 7 模块单向依赖
    II.  Agent 无状态，状态外置 (NON-NEGOTIABLE)
    III. AgentScope 使用边界：记忆对接 Redis/MySQL、检索对接 ES (NON-NEGOTIABLE)
    IV.  极简登录，不用 SpringSecurity
    V.   查询强制携带 user_id，用户数据完全隔离 (NON-NEGOTIABLE)
    VI.  JPA 落地规范
    VII. RAG 管线自实现
    VIII.记忆三层分层，存储明确
    IX.  三类写作能力共享一套 Agent 底座
    X.   每个 user story 完成后有可演示成果
- Source docs: docs/IndustryResearch.md, docs/DemandAnalysis.md,
  docs/TechnicalSolution.md, docs/AiProgrammingGuide.md
- Templates checked:
    ✅ .specify/templates/plan-template.md
    ✅ .specify/templates/spec-template.md
    ✅ .specify/templates/tasks-template.md
- Follow-up TODOs: none
-->

# ScriptAgent Constitution

ScriptAgent（智稿引擎）是基于 AgentScope2.0 的通用 AI 写作中台——面向内部用户提供三类核心写作能力
（一句话多轮对话写作、素材文档仿写 RAG、固定模板写作），素材/模板/稿件在系统内沉淀复用，用户数据完全隔离。
本宪法定义不可违背的工程与架构铁律，凌驾于一切个人偏好与临时便利之上；所有代码、Spec、Plan 与实现
必须遵守。原则冲突时以本文件为准。本宪法依据《行业调研》《需求文档》《技术方案》《AI 编程指南》四份
文档与 SDD 提炼，实施时若与四份文档冲突，以本宪法为准。

## Core Principles

### I. SpringBoot3.x + Java21 单体应用

系统 MUST 是 SpringBoot3.x + Java21 单体应用，Maven 多模块（**7 个**：writing-start / writing-api /
writing-business / writing-agent-core / writing-storage / writing-model / writing-common），
**单向依赖、无循环依赖**，一条命令打包运行（`mvn clean package` → `java -jar`）。

**Rationale**: 轻量易部署是核心定位；模块单向依赖适配 SDD 迭代开发，每阶段可独立交付与验证。

### II. Agent 无状态，状态外置 (NON-NEGOTIABLE)

写作 Agent MUST 不持有任何会话状态，状态全部外置：会话上下文在 Redis、素材检索在 ES、业务数据在
MySQL。Agent 由工厂按写作模式按需生成，任意实例可处理任意用户请求。MUST NOT 把状态塞进 Agent 内部。

**Rationale**: 状态外置是水平扩展的前提，也是"Agent 无状态"核心设计的落地。

### III. AgentScope 使用边界：记忆对接 Redis/MySQL、检索对接 ES (NON-NEGOTIABLE)

AgentScope2.0 只用于 Agent 管理、工具调用、记忆管理抽象。**短期会话记忆 MUST 对接 Redis（RedisMemory）、
长期记忆 MUST 对接 MySQL（writing_memory）、检索工具 MUST 对接 ES（ESRetrieveTool）**。MUST NOT 引入
AgentScope 默认存储。**AgentCore 依赖模型服务（writing-model），LLM/Embedding 调用统一经模型服务发出，
AgentCore 不直接连接任何大模型**。

**Rationale**: 复用 AgentScope 能力但不交存储控制权；模型服务是 LLM 唯一出口，是模型可切换的基础。

### IV. 极简登录，不用 SpringSecurity

登录鉴权 MUST 为手写极简方案：BCrypt 加密 + UUID Token 存 Redis + 全局拦截器 + UserContext 用户隔离。
MUST NOT 引入 SpringSecurity。

**Rationale**: 单表用户、够用且安全、轻量易维护；扩展阶段再平滑升级 SSO。

### V. 查询强制携带 user_id，用户数据完全隔离 (NON-NEGOTIABLE)

**所有业务查询 MUST 强制携带 user_id**（JPQL / JpaSpecification 追加 user_id 条件，Service 层统一控制），
禁止不带 user_id 查询。素材、会话、稿件、模板全部绑定用户，任何人只能访问自己的数据。这是最容易被
写错的一条，US-5 需专项验收。

**Rationale**: 用户数据完全隔离是核心设计；漏写 user_id 即越权漏洞。

### VI. JPA 落地规范

Entity / Repository MUST 统一放 writing-storage 模块；审计注解 `@CreatedDate` 自动填充 create_time
（审计配置放 writing-common）；逻辑删除用 `@SQLDelete` + `@Where`；分页用 JPA Pageable；开发环境
ddl-auto=update 自动建表、生产环境关闭自动建表、手动执行 SQL 脚本。

**Rationale**: 集中式数据访问 + 约定规范，CRUD/分页/审计零手写 SQL，数据隔离硬约束。

### VII. RAG 管线自实现

素材仿写（RAG）MUST 自实现：POI + PDFBox 解析（pdf/docx/txt/md）→ 固定长度 + 重叠切片 → Embedding
向量化 → ES7 批量入库（writing_material_chunk，携带用户维度）→ 用户维度语义检索。任一步失败 MUST
报错并回退，不产生半成品数据；删除素材 MUST 同步清理 ES 切片。

**Rationale**: 管线各环节成熟组件，组合简单可控；切片参数可配置调优召回质量。

### VIII. 记忆三层分层，存储明确

记忆 MUST 分三层且存储明确，禁止混成一层或塞进 Agent 内部：
- **短期记忆（会话）**：MUST 存 Redis，只存 user 输入 + assistant 最终回复，丢弃推理/tool 中间态，
  默认保留最近 10 轮（可配置），超长触发压缩；
- **长期记忆（用户偏好）**：MUST 存 MySQL `writing_memory`（按 user_id 隔离），Agent 用 `save_memory`
  回写稳定偏好，对话开始注入；
- **核心记忆（身份/任务）**：MUST 由 Prompt 管理固定注入 system prompt，不被压缩清除。
超长用 AutoContextMemory 压缩，压缩前先抽取长期记忆。

**Rationale**: 三层记忆各司其职，是"核心记忆定身份、长期记忆记偏好、短期记忆装会话"的落地。

### IX. 三类写作能力共享一套 Agent 底座

一句话多轮对话、素材仿写、模板写作 MUST 共享同一套 Agent 底座（Agent 工厂 + 记忆 + ESRetrieveTool +
Prompt 管理 + SSE 流式输出），由 Agent 工厂按模式生成，Prompt 隔离，SSE 流式封装统一。

**Rationale**: 一套底座支撑三种模式，能力复用、不重复建设。

### X. 每个 user story 完成后有可演示成果

按 SDD 五阶段拆 5 个 user story 推进，优先级是"跑通而非完美"，每个 user story 完成 MUST 有可演示成果
（登录跑通 → 素材可检索 → 三种模式可生成 → 前端可用 → 全链路稳定）。

**Rationale**: 适配 SDD 迭代开发，每阶段可交付、可验证、可回退。

## 技术栈与架构约束

- **语言 / 运行时**：Java 21，框架 Spring Boot 3.x，ORM Spring Data JPA（Hibernate），Agent 底座 AgentScope2.0。
- **存储**：Redis4（Token / 会话 / SSE 缓存 / 限流）、ES7（素材向量库 / 模板库）、MySQL8（业务数据：
  sys_user / writing_material / writing_article / writing_memory）。
- **文档解析**：POI（docx）+ PDFBox（pdf）+ txt/md 直读。
- **安全**：BCrypt 密码加密；Token 存 Redis 可过期；查询强制 user_id。
- **前端**：Vue3 + Vite + Element Plus + Pinia + Axios（拦截器统一 Token）+ SSE 流式渲染。
- **架构**：四层（前端展示 → 后端业务 → 中间件存储 → AI 模型），单向依赖、完全解耦。
- **模型选型**：LLM = 阿里云百炼通义千问 Qwen（DashScope API）；Embedding = BGE bge-m3（本地模型 + JVM 进程内 ONNX Runtime 推理，
  维度 1024，ES 索引 dim 必须一致，检索用 cosine）。模型服务层可切换，两条调用链独立。
- **部署**：单可执行 fat JAR；装在企业内网，数据不出内网，用户数据隔离；不锁云。

## 开发流程与质量门禁

- 采用 Spec-Driven Development：constitution → specify → (clarify) → plan → tasks → (analyze) →
  implement。一次只推进一个 user story。
- 主体开发用 Spec-Kit（项目已初始化 .specify 与 speckit-* Skills）；增量阶段切换到手动提示词 + Claude Code。
- 按 SDD 五阶段拆 5 个 user story（US-1 基础底座 → US-2 素材 RAG 底座 → US-3 Agent 核心 → US-4 前端页面
  → US-5 联调收尾），按依赖顺序推进。
- 质量门禁（每阶段 MUST 全绿方可交付）：编译通过、能启动、关键链路可演示；**重点审查 user_id 强制携带**
  与 **记忆三层是否分明**。
- 敏感配置一律 `${ENV_VAR}` 占位，缺失即清晰报错，不静默失败。

## Governance

- 本宪法凌驾于其它一切实践之上；与个人偏好或临时便利冲突时，以本宪法为准。
- 修订流程：任何原则的新增 / 删除 / 重定义 MUST 通过 PR 提出，说明动机与影响，并同步更新受影响模板
  （plan / spec / tasks）与 docs/ 文档。
- 版本策略（语义化）：MAJOR = 不兼容的治理 / 原则删除或重定义；MINOR = 新增原则或实质性扩充；
  PATCH = 澄清、措辞、笔误等非语义调整。
- 合规审查：所有 PR 与代码评审 MUST 验证是否符合本宪法；违背原则的复杂度 MUST 显式论证，否则优先选择
  更简单、更符合原则的方案。

**Version**: 1.0.0 | **Ratified**: 2026-10-02 | **Last Amended**: 2026-10-02
