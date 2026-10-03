# Implementation Plan: 模板引擎（Template Engine）—— business/storage 范围

**Branch**: `lesson6-template-engine` | **Date**: 2026-10-03 | **Spec**: [spec.md](./spec.md)

## Summary

按决策七（存储分层：MySQL 业务 / Redis KV / ES 向量+模板库）实现模板引擎的 business/storage 两模块。`TemplateRepository`（writing-storage，ES 模板库，落地第 1 节占位接口）——`ensureIndex`/`save`/`findByIdAndUserId`/`findByUserId`/`delete`/`enable`/`disable`，全部强制 user_id；配套 `TemplateDoc`/`TemplateVariable` 领域对象与 `TemplateIndexInitializer`（启动建索引）。`TemplateService`（writing-business）——`render(TemplateDoc, Map<String,Object>)` 按课件主角代码原样：必填校验 → `${key}` 占位符替换 → 组装 `prompt + "\n\n" + body` 模板写作 Prompt（不调用 LLM，不落稿件）。

## Technical Context

**Language/Version**: Java 21 (Spring Boot 3.3.5)，P3C 门禁 targetJdk=20（代码避免 Java 21-only 语法形态，如 switch 箭头 default 写法）

**Primary Dependencies**:
- 前序交付物（已核对存在性）：`TemplateRepository` 占位接口（writing-storage，第 1 节，本计划**实现**它而非另建）；`RestHighLevelClient`/`EsClientConfig`/`MaterialChunkRepository`（ES 访问模式，第 1 节）；`EsIndexConstants`/`RedisKeys`（writing-common，第 1 节）；`UserContext`/`BusinessException`/`ErrorCode`（第 3 节）；`MaterialServiceTest`（Mockito 单测模式，第 4 节）；`MaterialChunkRepositoryTest`（`@Tag("integration")` 本地 ES 冒烟，第 4 节）
- ES7.17 `elasticsearch-rest-high-level-client`（writing-storage 已有）、Jackson（已有）
- **无新增第三方依赖**；模板库纯 ES，不建 JPA Entity

**模板索引 `writing_template`（本节新建，非向量索引）**: settings shards=1 replicas=0；mapping 字段 `user_id`(long)/`name`(text+keyword)/`description`(text)/`structure`(text)/`variables`(nested: key keyword, label text, type keyword, required boolean, placeholder text, options keyword)/`prompt`(text)/`status`(keyword)/`create_time`(long)/`update_time`(long)。`status` 取值 `enabled`/`disabled`（课件字面量）。

**渲染契约（FR3~5）**: `render` 纯函数（无状态，符合宪法二），输入 `TemplateDoc` + `Map<String,Object>` 参数，输出 Prompt 文本。必填缺失抛 `BusinessException(ErrorCode.MISSING_REQUIRED_PARAM, key)`；替换 `${key}`（key 与 variables 一一对应）；返回 `tpl.getPrompt() + "\n\n" + body`。**不在 render 内校验 status**（停用拦截属第 8 节写作编排，课件渲染代码原样保真）。

**用户隔离（FR6）**: 仓库所有读取/写删除/启停 MUST 携带 user_id；`findByIdAndUserId` 用 `bool(termQuery(_id,id) + termQuery(user_id,userId))` 检索（越权 → 空）；`delete`/`updateStatus` 用 DeleteByQuery/UpdateByQuery 内嵌 user_id 过滤（防 TOCTOU）；`findByUserId` 按 user_id 列出。

**回退与一致性**: 模板为单文档 ES 存储，无跨存储事务；save 失败抛 `IOException`→上层 `BusinessException(ES_OPERATION_FAILED)`（沿用素材约定）；delete/updateStatus 按归属不存在返回 false，由调用方按"不存在/越权统一不存在"处理。

## 模块落位

| 类 | 模块 | 说明 |
|----|------|------|
| `TemplateDoc` | writing-storage (es) | ES 模板文档模型：id/userId/name/description/structure/variables/prompt/status/createTime/updateTime + getter/setter |
| `TemplateVariable` | writing-storage (es) | 变量配置：key/label/type/required/placeholder/options + getter/setter |
| `TemplateRepository` | writing-storage (es) | **实现第 1 节占位接口**：`ensureIndex`/`save`/`findByIdAndUserId`/`findByUserId`/`delete`/`enable`/`disable` |
| `TemplateIndexInitializer` | writing-storage (es) | `ApplicationRunner` 启动确保 `writing_template` 索引存在（镜像 `MaterialChunkIndexInitializer`） |
| `TemplateService` | writing-business | `render(TemplateDoc, Map<String,Object>)` 必填校验 + 占位符替换 + Prompt 组装（课件主角代码原样） |
| `ErrorCode` + `MISSING_REQUIRED_PARAM` | writing-common (exception) | 新增枚举值 1006（课件点名必填校验） |
| `EsIndexConstants.INDEX_TEMPLATE` | writing-common (constants) | 新增 `INDEX_TEMPLATE = "writing_template"` |
| `TemplateRepositoryTest` | writing-storage (test) | `@Tag("integration")` 本地 ES：CRUD、user 隔离、启停生效 |
| `TemplateServiceTest` | writing-business (test) | 单测（Mockito 注入 repo 满足构造，render 纯函数）：必填缺失抛错、无残留、Prompt 组装 |

## Testing

harness 先行。单测默认跑、integration `@Tag("integration")` CI 跳过；**实现完成的定义是 `mvn clean verify` 全绿（P3C 组合门禁）**。

- `TemplateServiceTest`（writing-business，单测，`@ExtendWith(MockitoExtension.class)`，mock `TemplateRepository` 满足构造注入，render 不触 repo）：
  1. `render_replacesAllPlaceholders_noResidue`（**关键回归**：fullParams 渲染后 `assertFalse(rendered.contains("${"))`，无残留）
  2. `render_missingRequiredParam_throws`（**关键回归**：缺 week_work → `assertThrows(BusinessException)`，errorCode=`MISSING_REQUIRED_PARAM`）
  3. `render_optionalMissing_doesNotThrow`（required=false 缺省不抛）
  4. `render_assemblePrompt_containsStructureAndPrompt`（结果以 `prompt + "\n\n"` 开头，含填充后正文）
  5. `render_nonVariablePlaceholder_remainsLiteral`（未声明占位符无参数值时原样保留，不 NPE）
- `TemplateRepositoryTest`（writing-storage，`@Tag("integration")`，本地 ES 7.17，CI 跳过，模式镜像 `MaterialChunkRepositoryTest`）：
  1. `ensureIndex_createsWritingTemplate`（索引存在，mapping 含 status/variables 字段）
  2. `saveAndFind_roundTrip`（save → findByIdAndUserId 读回字段一致；无 id 自动生成 `tpl-` 前缀）
  3. **关键回归 `templates_isolatedByUser_otherUsersInvisible`**（userA 存模板，`findByUserId(userB)` 不含、`findByIdAndUserId(id,userB)` 空——按用户隔离，越权按不存在）
  4. **关键回归 `disableTemplate_statusFlipsEnabledToDisabled`**（enable→disabled 生效；再 enable 回 `enabled`）
  5. `delete_removesOnlyOwnTemplate`（delete 后 findByIdAndUserId 空，他人模板不受影响）

## Constraints

- 依赖方向：writing-business → writing-storage + writing-model + writing-common；writing-storage → writing-common；无循环。
- user_id 唯一来源 = `UserContext.require()`（服务层）/ 显式 userId（仓库层）；ES 检索/删除/启停强制 user_id，禁止越权。
- 不新增第三方依赖；不建 MySQL 表；不改前序节已定字面量（仅 `ErrorCode` 加枚举值、`EsIndexConstants` 加 `INDEX_TEMPLATE`，本节交付物点名）。
- 凭证/模型路径全 `${ENV_VAR}`（H4 不变量③）；模板非向量索引，dim/cosine 不适用（H4 不变量④仅约束素材向量索引）。
- 方法名英文 + `@DisplayName` 保留课件中文原文；注释只写"为什么"。

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 检查 |
|------|------|
| I 单体 7 模块单向依赖 | ✅ 只动 writing-storage / writing-business / writing-common（枚举值+常量）/ writing-start（yaml 配置键），无新模块无循环 |
| II Agent 无状态 | ✅ 本节无 Agent；`render` 为纯函数（无状态），模板库为 ES 数据服务（无状态） |
| III AgentScope 边界 / 模型唯一出口 | ✅ 不涉及 LLM/Embedding；render 只产出 Prompt 文本，不直连任何模型 |
| IV 极简登录 | 不涉及 |
| V 查询强制 user_id | ✅ findByIdAndUserId/findByUserId/delete/updateStatus 全强制 user_id，越权按不存在 |
| VI JPA 规范 | 不涉及（模板纯 ES，不建 JPA Entity） |
| VII RAG 管线自实现 | 不涉及（本节为模板结构资产，非素材内容资产；模板库 ES 索引独立于素材切片索引） |
| VIII 记忆三层 | 不涉及 |
| IX 三类写作共享底座 | ✅ 模板渲染 Prompt 为第 8 节模板写作 Agent 的输入之一（本节交付渲染能力） |
| X 可演示成果 | ✅ harness 测试全绿 + 本地 ES integration 冒烟（存→查→隔离→启停→删链路可演示） |
