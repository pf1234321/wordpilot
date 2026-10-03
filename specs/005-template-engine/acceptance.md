# 第 6 节 模板引擎（Template Engine）— 节级验收报告

**Branch**: `lesson6-template-engine` | **Date**: 2026-10-03 | **Spec/Plan/Tasks**: [spec.md](./spec.md) [plan.md](./plan.md) [tasks.md](./tasks.md)

**范围**: business/storage 两模块（用户点名）；Controller (writing-api) / 前端动态表单 (Vue3) 属第 7/8 节，不在本节。



***

## 六项证据 DoD 核对

### ① `mvn clean verify` 全绿（含 P3C 组合门禁）

命令：`BGE_MODEL_PATH=/Users/Administrator/bge-m3 mvn clean verify`



```
Reactor Summary:
wordpilot .......... SUCCESS
writing-common .... SUCCESS   [ 5.256 s]
writing-model ..... SUCCESS   [ 8.601 s]
writing-storage ... SUCCESS   [ 7.950 s]
writing-agent-core  SUCCESS   [ 1.481 s]
writing-business .. SUCCESS   [ 3.924 s]
writing-api ....... SUCCESS   [ 3.852 s]
writing-start ..... SUCCESS   [ 4.263 s]
BUILD SUCCESS  Total time: 35.591 s
```

8 模块 SUCCESS；Spotless / Checkstyle (0 violations) / PMD / SpotBugs (0 bugs) / FindSecBugs /dependency-check 全过。P3C 门禁通过。

### ② 课件 harness 映射表测试类存在且非空，关键回归逐个对号



| harness 测试类                                                 | 落地        | 关键回归 / 覆盖点                                                                                                                                                              | 结果           |
| ----------------------------------------------------------- | --------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------ |
| `TemplateServiceTest`（writing-business，单测）                  | ✅ 5 tests | `render_missingRequiredParam_throws`（缺必填 → `MISSING_REQUIRED_PARAM`，防残缺稿）★；`render_replacesAllPlaceholders_noResidue`（无 `${}` 残留）★；可选缺省不抛；Prompt 组装含正文 + 规范；未声明占位符保留字面量 | 5/5 绿        |
| `TemplateRepositoryTest`（writing-storage，integration，CI 跳过） | ✅ 6 tests | `templates_isolatedByUser_otherUsersInvisible`（user 隔离，越权按不存在）★；`disableTemplate_statusFlipsEnabledToDisabled`（启停生效）★；存取往返；无 id 自动生成；删除只删本人；索引存在                        | 6/6 绿（本地 ES） |

★ = 课件 harness 写出的关键回归，断言逻辑逐条保真落地，方法名译英文、原文进 `@DisplayName`。

### ③ "本节交付物" 逐项存在性核对



| 交付物                                                   | 模块               | 存在                                   |
| ----------------------------------------------------- | ---------------- | ------------------------------------ |
| `TemplateRepository`（实现第 1 节占位接口：CRUD + 启停 + user 隔离） | writing-storage  | ✅ `es/TemplateRepository.java`       |
| `TemplateDoc`（ES 模板文档模型）                              | writing-storage  | ✅ `es/TemplateDoc.java`              |
| `TemplateVariable`（变量配置）                              | writing-storage  | ✅ `es/TemplateVariable.java`         |
| `TemplateIndexInitializer`（启动建索引）                     | writing-storage  | ✅ `es/TemplateIndexInitializer.java` |
| `TemplateService`（渲染 + 变量填充 + Prompt 组装）              | writing-business | ✅ `business/TemplateService.java`    |
| `TemplateServiceTest` / `TemplateRepositoryTest`      | 对应模块             | ✅ 见上                                 |
| `ErrorCode.MISSING_REQUIRED_PARAM(1006)`              | writing-common   | ✅                                    |
| `EsIndexConstants.INDEX_TEMPLATE`                     | writing-common   | ✅                                    |
| `application.yaml` 模板索引配置键                            | writing-start    | ✅                                    |

### ④ 前序节全部测试回归绿（跨节契约证据）

`mvn clean verify` 全模块测试通过：writing-common /writing-model/writing-storage（`ChunkSplitterTest` 4、`DocumentParserTest` 6、`ArticleRepositoryTest`、`AuditTest`、`WritingMemoryRepositoryTest` 等第 1\~4 节回归）/writing-agent-core/writing-business（`LoginServiceTest` 3、`MaterialServiceTest` 6、`TemplateServiceTest` 5）/writing-api/writing-start 全部 SUCCESS。

### ⑤ H4 六条全局不变量逐条自查



| 不变量                                     | 检查结果                                                                                                                           |
| --------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| ① 查询 / 检索强制 user\_id（UserContext，拒前端参数） | ✅ `findByIdAndUserId`/`findByUserId`/`delete`/`enable`/`disable` 全部内嵌 user\_id（`_id+user_id` 双条件，防 TOCTOV / 越权）；service 不取前端参数 |
| ② 逻辑删除 `@SQLDelete`/ 审计 `@CreatedDate`  | 不涉及（本节模板纯 ES，无 JPA Entity；前序审计回归绿）                                                                                             |
| ③ 无明文 key / 密码 / 模型路径（全 `${ENV_VAR}`）   | ✅ grep 本节改动文件：无 `sk-`/`AKIA`/`DASHSCOPE_API_KEY=`/`DB_PASSWORD=`/`BGE_MODEL_PATH=` 明文                                          |
| ④ Embedding 恒 1024 + ES dim=1024 cosine | ✅ 本节模板为非向量索引（无 dense\_vector，§7.1），不破坏素材索引契约（dim/cosine 前序回归绿）                                                                 |
| ⑤ Agent 无状态、状态外置                        | ✅ 本节无 Agent；`TemplateService.render` 纯函数无状态，模板库为 ES 数据服务                                                                       |
| ⑥ AgentCore 不直连大模型                      | ✅ 本节不涉 LLM/Embedding，render 只产出 Prompt 文本                                                                                      |

### ⑥ 剩余人工项清单（harness 已判卷，这几项等你人工过）

以下依赖真实中间件 / 前端 / 编排，harness 不覆盖，需人工验收：



1. **模板入库链路冒烟**：本地 ES 起 `writing_template` 索引（已建，见集成测试），人工经 `TemplateRepository` 存 / 查 / 隔离 / 启停 / 删一遍（可复用集成测试用例场景）。

2. **前端新建模板 → 动态表单**：第 7 节前端课实现 `VariableFormBuilder` 后，选模板看 `variables` 是否正确驱动表单（text/textarea/select/number）。

3. **填参提交 → 渲染 → LLM 成稿**：第 8 节写作编排接 `TemplateService.render` 产出的 Prompt → `ModelService` 生成文稿 → 落 `writing_article`。

4. **停用模板不可用于新写作**：停用拦截逻辑属第 8 节写作编排（本节只保证 `status` 状态落地正确）；历史稿件不受影响。

5. **两个用户模板互不可见（人工 UI 视角）**：集成测试已证数据层隔离，UI 层待第 7/8 节验证。

6. **第 5 节（Agent 核心）尚未开发**：本节不依赖其交付物；接第 7/8 节前需先补第 5 节。



***

## 验证方式与缺口



* **已自动验证**：`mvn clean verify` 全绿（静态门禁 + 全量单测）；`TemplateRepositoryTest` 集成冒烟 6/6（本地 ES 7.17，`-Dgroups=integration -Dsurefire.excludedGroups=`）。

* **覆盖范围**：render 必填校验 / 占位符替换 / Prompt 组装；ES 模板 CRUD、user 隔离、启停、删除归属。

* **仍存缺口**：停用拦截与 LLM 成稿属第 8 节；前端动态表单属第 7 节 —— 均为课件明确边界，非本节遗漏。



***

## 变更总结（给 reviewer 的导读）

**改动点**（`git status --short` / `git diff --stat` 实测）：



* **writing-storage（新增 4 + 修改 1）**：


  * `es/TemplateVariable.java`（新）变量配置 POJO（key/label/type/required/placeholder/options）。

  * `es/TemplateDoc.java`（新）ES 模板文档模型，`status` 默认 `enabled`。

  * `es/TemplateRepository.java`（改，占位接口→具体实现，+293 行）CRUD + user 隔离 + 启停；ES 失败抛 `IOException`。

  * `es/TemplateIndexInitializer.java`（新）启动建 `writing_template` 索引。

  * `es/TemplateRepositoryTest.java`（新，integration）6 用例。

* **writing-business（新增 2）**：


  * `TemplateService.java`（新）`render` 纯函数：必填校验 → 占位符替换 → Prompt 组装。

  * `TemplateServiceTest.java`（新，单测）5 用例，含课件两个关键回归。

* **writing-common（修改 2）**：`ErrorCode` 增 `MISSING_REQUIRED_PARAM(1006)`；`EsIndexConstants` 增 `INDEX_TEMPLATE`。

* **writing-start（修改 1）**：`application.yaml` 增 `elasticsearch.index.template` 配置键。

* **specs/005-template-engine/**（新）spec/plan/tasks/acceptance 四份。

* 前序节文件被本节触碰的：`TemplateRepository.java`（第 1 节占位接口，本节按设计实现，非破坏性）。

**重点 review 清单**（按风险排序）：



1. **TemplateService 移除 repository 字段**（`TemplateService.java`）—— 课件展示该字段，但 render 为纯函数，SpotBugs `URF_UNREAD_FIELD` 拦下；改为无状态纯服务，CRUD 由第 8 节 Controller 直接走仓库。**偏离课件字段但语义等价，建议 reviewer 确认**。

2. **TemplateRepository 越权语义**（`findByIdAndUserId`/`delete`/`updateStatus` 均 `_id+user_id` 双条件，`Optional`/`boolean` 返回）—— 越权一律按 "不存在" 处理，防探测；`UpdateByQuery`/`DeleteByQuery` 内嵌过滤防 TOCTOV。

3. `BulkByScrollResponse`**&#x20;import 修正**（`org.elasticsearch.index.reindex`，非 `action.bulk`）——ES 7.17 实测包名。

4. **模板索引 mapping**：`variables` 为 nested、`status` 为 keyword（非向量索引，dim/cosine 不适用），与素材索引职责分离。

5. **跨节契约**：`ErrorCode`/`EsIndexConstants` 仅追加（1006 / `INDEX_TEMPLATE`），不改既有枚举值；素材层零触碰。

**如何验证**（可直接复制执行）：



```
# 全量门禁（含 P3C 组合；integration 默认 CI 跳过）
BGE_MODEL_PATH=/Users/Administrator/bge-m3 mvn clean verify        # 预期：8 模块 SUCCESS
# 只跑本节单测
BGE_MODEL_PATH=/Users/Administrator/bge-m3 mvn -pl writing-business test -Dtest=TemplateServiceTest  # 预期：5/5
# 本地 ES 集成冒烟（先确保 127.0.0.1:9200 在跑）
BGE_MODEL_PATH=/Users/Administrator/bge-m3 mvn -pl writing-storage -am test -Dgroups=integration -Dsurefire.excludedGroups= -Dtest=TemplateRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false  # 预期：6/6
# 依赖方向（无循环）：writing-business → common/model/storage/agent-core，writing-storage → common
grep -A2 '<artifactId>writing-' writing-business/pom.xml   # 预期：只见 common/model/storage/agent-core
grep -A2 '<artifactId>writing-' writing-storage/pom.xml    # 预期：只见 common
```

**剩余人工项**：见上文 ⑥（前端动态表单、停用拦截、LLM 成稿、第 5 节补做等）。