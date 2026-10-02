# ScriptAgent（智稿引擎）— Claude Code 项目指南

ScriptAgent 是基于 AgentScope2.0 的 **通用 AI 写作中台**，面向内部用户提供三类核心写作能力：一句话多轮对话写作、素材文档仿写（RAG）、固定模板写作。部署在企业内网，素材/模板/稿件在系统内沉淀复用，用户数据完全隔离，轻量易部署，适配 SDD 迭代开发。

> 详细背景：`docs/DemandAnalysis.md`（需求）、`docs/TechnicalSolution.md`（技术方案）、`docs/IndustryResearch.md`（业界调研）、`docs/AiProgrammingGuide.md`（AI 编程指南）、`.specify/memory/constitution.md`（项目宪章）

---

## 技术栈

| 组件 | 选型 |
|------|------|
| 语言 / 运行时 | Java 21（必须，Spring Boot 3.x） |
| 框架 | Spring Boot 3.x（单体应用） |
| ORM | Spring Data JPA（Hibernate） |
| Agent 底座 | AgentScope2.0（Agent 管理 / 工具调用 / 记忆管理抽象） |
| 缓存 / 会话 | Redis4（Token / 会话 / SSE 缓存 / 限流） |
| 检索引擎 | ES7（素材向量库 / 模板库） |
| 数据库 | MySQL8（用户 / 素材 / 稿件 / 长期记忆） |
| 文档解析 | POI（docx）+ PDFBox（pdf）+ txt/md 直读 |
| 安全 | BCrypt 密码加密 + UUID Token + 拦截器（手写极简登录） |
| 前端 | Vue3 + Vite + Element Plus + Pinia + Axios + SSE |
| 构建 | Maven 多模块 |

**模型选型（技术方案 §9.3）**：LLM = 阿里云百炼通义千问 Qwen（DashScope API）；Embedding = BGE bge-m3（本地模型文件 + JVM 进程内 ONNX Runtime 推理，维度 **1024**）。两条调用链独立、可切换，敏感 key 走环境变量。

---

## 模块结构（7 个）

```
wordpilot/
├── writing-start        # 启动聚合模块：主类、依赖聚合
├── writing-api          # 接口层：登录、素材、三类写作、模板、稿件；Token 拦截器/鉴权
├── writing-business     # 业务编排：写作路由、素材调度、模板渲染、稿件历史
├── writing-agent-core   # Agent 核心（重点）：Agent 工厂、三层记忆、ESRetrieveTool、
│                        #   Prompt 管理、SSE 流式封装（依赖 writing-model）
├── writing-storage      # 存储层：JPA Entity/Repository、Redis 操作、ES 操作、文档解析、切片工具
├── writing-model        # 模型服务：LLM 对话调用（阿里 Qwen）、Embedding 向量化（BGE）、模型适配
└── writing-common       # 公共基础：工具类、加密、UserContext、异常、常量、DTO、枚举、JPA 审计监听器
```

**依赖方向（单向、无循环）**：`writing-start → writing-api → writing-business → writing-agent-core / writing-storage / writing-model / writing-common`。新增能力只改对应模块，不破坏依赖链。

---

## 不可违背的原则（Constitution）

以下 10 条来自 `.specify/memory/constitution.md`，所有代码必须遵守。标注 ⚠️ 的是最容易写错、审查重点。

### 原则一：SpringBoot3.x + Java21 单体应用

Maven 7 模块**单向依赖、无循环依赖**，一条命令打包运行（`mvn clean package` → `java -jar`）。

### 原则二：Agent 无状态，状态外置 ⚠️

写作 Agent MUST 不持有任何会话状态。会话上下文在 Redis、素材检索在 ES、业务数据在 MySQL。**不得把状态塞进 Agent 内部**。

### 原则三：AgentScope 使用边界；LLM 统一经模型服务 ⚠️

AgentScope 只用于 Agent 管理、工具调用、记忆管理抽象。**短期会话记忆对接 Redis（RedisMemory）、长期记忆对接 MySQL（writing_memory）、检索工具对接 ES（ESRetrieveTool）**，不得引入 AgentScope 默认存储。**AgentCore 依赖模型服务（writing-model），LLM/Embedding 统一经模型服务发出，AgentCore 不直接连接任何大模型**。

### 原则四：极简登录，不用 SpringSecurity

手写登录：BCrypt 加密 + UUID Token 存 Redis + 全局 Token 拦截器 + UserContext 用户隔离。**不得引入 SpringSecurity**。

### 原则五：查询强制携带 user_id ⚠️（最容易被写错）

所有业务查询 MUST 强制携带 user_id（JPQL / JpaSpecification 追加 user_id 条件，Service 层统一控制），禁止不带 user_id 查询。素材、会话、稿件、模板全部绑定用户，任何人只能访问自己的数据。**US-5 需专项验收越权**。

### 原则六：JPA 落地规范

Entity / Repository 统一放 writing-storage；`@CreatedDate` 审计（配置在 writing-common）；逻辑删除用 `@SQLDelete` + `@Where`；分页用 Pageable；开发 ddl-auto=update 自动建表、生产关闭自动建表手动执行 SQL。

### 原则七：RAG 管线自实现

解析（POI/PDFBox）→ 固定长度 + 重叠切片 → Embedding 向量化 → ES7 批量入库 → 用户维度检索。任一步失败报错回退、不产生半成品；删除素材 MUST 同步清理 ES 切片。

### 原则八：记忆三层分层 ⚠️

- **短期记忆（会话）**：Redis，只存 user 输入 + assistant 最终回复，丢弃推理/tool 中间态，默认最近 10 轮（可配置），超长压缩；
- **长期记忆（偏好）**：MySQL `writing_memory`，Agent 用 `save_memory` 回写，对话开始注入；
- **核心记忆（身份/任务）**：Prompt 管理固定注入，不被压缩清除。
超长用 AutoContextMemory 压缩，**压缩前先抽取长期记忆**。禁止把三层混成一层。

### 原则九：三类写作能力共享一套 Agent 底座

对话 / 仿写 / 模板共用 Agent 工厂 + 记忆 + ESRetrieveTool + Prompt 管理 + SSE 流式封装，按模式生成，Prompt 隔离。

### 原则十：每个 user story 完成后有可演示成果

按 SDD 五阶段拆 5 个 user story，跑通优先于完美，每阶段有可交付成果。

---

## 架构与关键机制

### 四层架构

```
前端展示层（Vue3 工作台：登录 / 三大写作 Tab / 素材管理 / 模板管理 / 稿件历史）
  → 后端业务服务层（writing-api 拦截器鉴权 → writing-business 编排 → writing-agent-core / writing-model）
  → 中间件存储层（MySQL / Redis / ES7）
  → AI 模型层（LLM 阿里 Qwen / Embedding BGE bge-m3）
```

单向依赖、完全解耦。模型服务是 LLM/Embedding 唯一出口。

### 三类写作模式（writing-business 路由）

| 模式 | 入口 | 依赖 |
|------|------|------|
| 一句话多轮对话 | Tab1 一句话输入 | AgentCore 对话 Agent + 记忆（短期/长期/核心）+ 模型服务（LLM），无 ES |
| 素材仿写（RAG） | Tab2 素材 + 需求 | AgentCore 仿写 Agent + 记忆 + ESRetrieveTool + 模型服务（LLM） |
| 固定模板写作 | Tab3 模板 + 参数 | AgentCore 模板 Agent + 记忆 + ES 模板库 + 模型服务（LLM） |

### 核心流程

**登录**：账号密码 → JPA 查 sys_user → BCrypt 比对 → 签发 UUID Token 存 Redis（token:{token}→UserId）→ 拦截器校验写入 UserContext。

**素材入库**：上传（pdf/docx/txt/md）→ 校验 → POI/PDFBox 解析纯文本 → 切片（固定长度+重叠）→ Embedding 向量化 → ES 批量写入（writing_material_chunk，带用户维度）→ JPA 写 writing_material。

**素材仿写**：选素材+需求 → 按 user_id 在 ES 检索相似切片（ESRetrieveTool）→ 组装仿写 Prompt（参考素材注入）→ LLM 生成 → SSE 流式返回 + Redis 会话 → 落稿件历史。

**一句话写作**：输入 → AgentCore 对话 Agent → 注入核心/长期记忆 + 读 Redis 短期记忆 → LLM 生成 → SSE 流式 + Redis 会话 → 落稿件。

**模板写作**：选模板 → 读 ES 模板配置 → 动态表单填参 → 替换占位符 → 组装 Prompt → LLM 生成 → 落稿件。

---

## 核心数据模型

### MySQL 四表

**sys_user（用户）**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键，自增 |
| username | VARCHAR | 账号，唯一 |
| password | VARCHAR | BCrypt 加密 |
| nickname | VARCHAR | 昵称（可空） |
| create_time / update_time | DATETIME | 审计自动填充 |
| deleted | TINYINT | 逻辑删除 |

**writing_material（素材主表）**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| user_id | BIGINT | 归属用户，强制携带 |
| file_name / file_type / file_size | - | 原始文件信息（pdf/docx/txt/md） |
| file_path | VARCHAR | 存储路径 |
| content_text | LONGTEXT | 解析后纯文本（预览用） |
| chunk_count | INT | 切片数量 |
| create_time / update_time | DATETIME | 审计 |
| deleted | TINYINT | 逻辑删除 |

**writing_article（稿件历史）**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| user_id | BIGINT | 归属用户，强制携带 |
| article_title / article_content | - | 标题 / 正文 |
| write_type | VARCHAR | dialog / rag / template |
| material_id / template_id | BIGINT | 关联素材/模板（可空） |
| create_time / update_time / deleted | - | 审计 / 逻辑删除 |

**writing_memory（长期记忆）**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| user_id | BIGINT | 归属用户，强制携带 |
| memory_type | VARCHAR | style / term / habit / other |
| content | TEXT | 一条用户偏好/事实 |
| source | VARCHAR | save_memory 工具 / 会话压缩抽取 |
| create_time / update_time / deleted | - | 审计 / 逻辑删除 |

### ES 索引：writing_material_chunk

`id`、`material_id`、`user_id`、`chunk_index`、`chunk_text`、`embedding`（**dense_vector，dim=1024**，检索用 **cosine**）。

### Redis key

| Key | Value |
|-----|-------|
| `token:{token}` | UserId（TTL 过期） |
| `session:{userId}:{sessionId}` | 对话层消息数组（短期记忆，最近 10 轮） |
| `sse:cache:{userId}:{sessionId}` | SSE 流式缓存 |

---

## 接口层（writing-api）

接口层暴露的核心端点类别（具体路径以实现为准）：

| 类别 | 端点 |
|------|------|
| 登录 | 登录（签发 Token）；鉴权由全局拦截器统一处理 |
| 素材 | 上传、列表（分页、仅当前用户）、预览、删除（同步清 ES） |
| 写作 | 一句话多轮（/api/writing/dialog）、素材仿写（/api/writing/rag）、模板写作（/api/writing/template），均 SSE 流式 |
| 模板 | 新增、编辑、启用停用、变量配置 |
| 稿件 | 列表、查看、重新编辑、导出 Word、删除 |

---

## 配置加载规则

敏感配置（阿里 DashScope API key、Embedding 模型路径等）通过环境变量注入，**不得**明文写入代码或 yml：

```yaml
model:
  llm:
    provider: dashscope
    api_key: ${DASHSCOPE_API_KEY}     # 环境变量
    model: qwen-max
  embedding:
    provider: bge
    model-path: ${BGE_MODEL_PATH}      # 本地 bge-m3 模型目录（含 onnx/model.onnx）
    model: bge-m3
    dim: 1024
    load-on-start: true                # 启动时加载一次，单例
```

`memory.short-term.max-rounds` 默认 10，可配置。ddl-auto 开发 update / 生产 none（手动 SQL）。

---

## 前端工作台

单页面 Tab 工作台（Vue3 + Vite + Element Plus + Pinia + Axios + SSE）：

- 登录页：账号密码，Token 存本地，路由守卫拦截未登录
- 核心工作台三大 Tab：一句话多轮写作（对话式输入 + SSE 流式渲染）、素材仿写（上传/选素材 + 自动检索 + 仿写）、模板写作（选模板 + 动态表单）
- 素材管理 / 模板管理 / 稿件历史

Axios 拦截器统一携带 Token，401 跳转登录。

---

## 五大核心能力与验收 Demo

| 能力 | 核心组件 | 验收 Demo |
|------|---------|----------|
| **登录鉴权** | BCrypt + Token + 拦截器 + UserContext | 登录跑通，未登录被拦截 |
| **素材 RAG** | 解析 / 切片 / Embedding / ES 入库检索 | 上传文档后素材可见、ES 可检索到切片 |
| **Agent 核心** | Agent 工厂 + 三层记忆 + ESRetrieveTool + Prompt | 三种模式都能调用 LLM 产出，SSE 流式 |
| **前端工作台** | Vue3 三大 Tab + 素材/模板/稿件管理 | 完整工作台全流程操作 |
| **数据隔离** | user_id 强制 + Token 鉴权 | A 无法访问 B 的素材/稿件/会话 |

---

## 实施节奏（SDD 五阶段 = 5 个 user story）

| 阶段 | 能力主线 | 可演示成果 |
|------|---------|-----------|
| **第一阶段** | 基础底座：Maven 骨架、公共模块、JPA 基础、极简登录、拦截器、Redis/ES 配置 | 项目可启动，登录跑通，鉴权生效，自动建表 |
| **第二阶段** | 素材 RAG 底座：解析、切片、ES 向量入库、检索、素材上传列表 | 上传文档后素材列表可见，ES 可检索切片 |
| **第三阶段** | Agent 核心：AgentScope 集成、三层记忆、ESRetrieveTool、三类写作 Agent | 三种模式都能调用 LLM 产出，SSE 流式 |
| **第四阶段** | 前端业务页面：登录页、三大 Tab 工作台、模板管理、稿件历史 | 完整前端工作台可用 |
| **第五阶段** | 联调、优化、收尾：流式、异常、数据隔离校验、BUG | 端到端全链路稳定，越权被拦截 |

---

## 常见陷阱

| 陷阱 | 症状 | 修复 |
|------|------|------|
| 查询漏写 user_id | 越权访问他人素材/稿件 | 所有 JPQL/JpaSpecification 强制带 user_id，Service 层统一控制（原则五） |
| 引入 SpringSecurity | 复杂度激增，违背极简登录 | 用 BCrypt + Token + 拦截器 + UserContext（原则四） |
| 用 AgentScope 默认存储 | 存储分层混乱、无法扩展 | 记忆对接 Redis/MySQL、检索对接 ES（原则三） |
| 记忆三层混成一层 | 长期偏好随会话丢失 | 短期 Redis / 长期 MySQL / 核心 Prompt 分明（原则八） |
| Agent 内部塞状态 | 无法水平扩展 | Agent 无状态，状态外置（原则二） |
| AgentCore 直连 LLM | 换模型要改 Agent 层 | 统一经模型服务（writing-model）出口（原则三） |
| ES 索引维度与 bge-m3 不一致 | 向量入库/检索报错 | embedding 字段 dim 必须 = 1024，检索用 cosine |
| 切片参数写死 | 召回质量无法调优 | 长度/重叠配 yml，可配置 |
| 删除素材没清 ES | 孤儿向量数据 | 按 material_id 同步清理切片 |
| 长期记忆没走 save_memory | 跨轮要求随窗口丢失 | "以后都写 800 字"这类走长期记忆，不靠短期窗口 |

---

## 设计原则

- **轻量易部署**：单体应用、7 模块单向依赖、一条命令打包运行
- **一套 Agent 底座支撑三种写作模式**：对话 / 仿写 / 模板共享工厂 + 记忆 + 工具 + Prompt + SSE
- **用户数据完全隔离**：素材/会话/稿件/模板全绑 user_id，查询强制携带
- **存储分层**：MySQL 业务数据 / Redis 会话与 Token / ES 向量与模板检索，各司其职
- **模型服务是 LLM 唯一出口**：AgentCore 依赖模型服务，换模型只改模型服务层
- **记忆三层**：核心记忆定身份、长期记忆记偏好、短期记忆装会话，压缩保证不撑爆上下文
- **分阶段克制**：先按 SDD 五阶段跑通三类写作 + 支撑能力，扩展功能（SSO/开放 API/权限分级/混合检索）后续迭代
