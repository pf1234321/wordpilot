# Feature Spec: 模板引擎（Template Engine）—— business/storage 范围

**Branch**: `lesson6-template-engine` | **Spec Version**: v1 | **Date**: 2026-10-03
**Scope**: 本 spec 只覆盖 **business/storage** 两个模块（用户点名范围）：`TemplateRepository`（writing-storage，ES 模板库）+ `TemplateService`（writing-business，渲染）。Controller（writing-api）与前端动态表单（Vue3 `VariableFormBuilder`）不在本节范围，留待第 7/8 节。

## 第6节需求：模板引擎——固定模板写作的结构资产载体

模板引擎是 ScriptAgent 能力三"固定模板写作"的载体。核心是 **structure + variables + prompt 三要素**：`structure` 是给 LLM 的格式骨架（静态文本 + `${变量名}` 占位符），`variables` 驱动前端动态表单（后端不写死表单结构，加模板不改前端代码），`prompt` 是可选写作规范。模板是**结构资产**存 ES 模板库（§7），素材是**内容资产**存 ES 素材索引，两者分离。

### 用户场景

- 用户新建一条"周报模板"：填 structure（含 `${title}`/`${week_work}`/`${next_plan}`/`${risk}` 占位符）+ variables（key/label/type/required/placeholder/options）+ prompt 规范；保存后他人不可见（按 user_id 隔离）。
- 用户选模板写作：读取模板的 `variables` 动态生成表单 → 填参提交 → 后端校验必填项 → 替换 `${变量名}` 占位符 → 组装模板写作 Prompt（规范说明 + 填充后正文）。
- 用户停用某模板后，它不可用于新写作；历史稿件不受影响；两个用户的模板互不可见。

### 功能需求

- **FR1（模板存储）**：一条模板是 ES `writing_template` 索引中的 JSON 文档，字段 `id/user_id/name/description/structure/variables/prompt/status/create_time/update_time`，与 `docs/TechnicalSolution.md §7.1` 逐字对应。
- **FR2（变量配置）**：`variables` 为变量配置数组，每项含 `key/label/type/required/placeholder/options`（type ∈ text/textarea/select/number），驱动前端动态表单。
- **FR3（渲染-必填校验）**：`TemplateService.render` 遍历 `variables`，`required=true` 的 key 若不在提交参数中 → 抛 `BusinessException(ErrorCode.MISSING_REQUIRED_PARAM, key)`（防生成残缺稿）。
- **FR4（渲染-占位符替换）**：按 `${key}` 与 variables 的 key 一一对应替换 `structure` 占位符；提交参数覆盖的占位符不得残留 `${...}`。
- **FR5（渲染-Prompt 组装）**：返回 `prompt + "\n\n" + 填充后正文`（核心记忆身份 + 模板正文 + 规范说明）。
- **FR6（用户隔离）**：模板查询/检索/删除/启停 MUST 强制 user_id（来源 `UserContext` / 显式 userId，禁止越权），他人模板一律按不存在处理。
- **FR7（启用停用）**：`status` ∈ `enabled`/`disabled`；`disable` 后该模板 `status=disabled`（不可用于新写作的拦截属第 8 节写作编排，本节只保证状态落地正确）。

### 明确不做（边界）

- **不做 Controller / REST**（writing-api 属第 8 节）；**不做前端动态表单**（`VariableFormBuilder` 属第 7 节）。
- **不做写作编排 / LLM 调用**：`TemplateService` 只产出渲染后的 Prompt 文本，不调用 `ModelService`，不落稿件（第 8 节）。
- **停用拦截不在此层**：`render` 不校验 status（按课件渲染代码原样，拦截在写作编排层做）；状态正确性由仓库 `enable/disable` 保证。
- **不建 MySQL 表**：模板库纯 ES，不落 JPA Entity；不引入 spring-data-elasticsearch（沿用既有 `RestHighLevelClient`）。
- **不改前序节已定字面量**：`MaterialChunkRepository`/`WritingMaterial` 等一行不动；仅 `ErrorCode` 新增枚举值、`EsIndexConstants` 新增 `INDEX_TEMPLATE` 常量（本节交付物点名）。

### 验收标准

- 自动化由 harness 测试承载（`mvn test` 全绿即过；实现完成定义 `mvn clean verify` 全绿，P3C 组合门禁）。
- **关键回归点（课件 harness 写出代码的守点）**：
  - `render_missingRequiredParam_throws`：缺必填参数 → `BusinessException`（不校验 → 生成残缺稿）。
  - `render_replacesAllPlaceholders_noResidue`：渲染后无 `${week_work}` 残留未替换占位符。
  - 模板按 user_id 隔离（他人模板不可见）；启用停用生效。

### 依赖与假设

- 前序交付物（已核对存在性）：`TemplateRepository` 占位接口（writing-storage，第 1 节）；`RestHighLevelClient`/`EsClientConfig`/`MaterialChunkRepository` 模式（第 1 节）；`EsIndexConstants`（writing-common，第 1 节）；`UserContext`/`BusinessException`/`ErrorCode`（第 3 节）；`MaterialService` + `MaterialServiceTest`（Mockito 单测模式，第 4 节）；`MaterialChunkRepositoryTest`（`@Tag("integration")` 本地 ES 冒烟模式，第 4 节）。
- ES7.17 + `elasticsearch-rest-high-level-client`（writing-storage 已有）；无新增第三方依赖。
- 模板索引 `writing_template` 为**本节新建**（ApplicationRunner 启动初始化，非向量索引，dim/cosine 不适用）。
- 第 5 节（Agent 核心）尚未开发，本节不依赖其交付物。
