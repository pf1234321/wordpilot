# 第 6 节 模板引擎（Template Engine）— 任务清单（/speckit-tasks）

**Branch**: `lesson6-template-engine` | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)
**范围**: business/storage 两模块（用户点名）；Controller(writing-api)/前端动态表单(Vue3) 不在本节。
**实现完成定义**: `mvn clean verify` 全绿（P3C 组合门禁）。

## 前置（已就绪）

- 分支待建：`lesson6-template-engine`（基于当前 `lesson4-material-rag`，干净基线）
- 课件《第6节：模板引擎》/ TS §7（§7.1 存储 / §7.2 渲染 / §7.3 关键设计点）已读
- 前序交付物存在性已核对：`TemplateRepository`（占位接口，第 1 节）`RestHighLevelClient`/`EsClientConfig`/`MaterialChunkRepository`/`EsIndexConstants`（第 1 节）、`UserContext`/`BusinessException`/`ErrorCode`（第 3 节）、`MaterialService`/`MaterialServiceTest`/`MaterialChunkRepositoryTest`（第 4 节模式）
- 无新增第三方依赖（ES 客户端/Jackson 均已在既有模块）

## 任务清单

| # | 模块 | 任务 | 验证 |
|---|---|---|---|
| T001 | writing-common | 扩展 `exception/ErrorCode.java`：新增 `MISSING_REQUIRED_PARAM(1006,"缺少必填参数")`（必填校验报错，课件点名） | 编译通过 |
| T002 | writing-common | 扩展 `constants/EsIndexConstants.java`：新增 `INDEX_TEMPLATE = "writing_template"`（模板库索引常量，禁止硬编码字符串） | 编译通过 |
| T003 | writing-storage | 新建 `es/TemplateVariable.java`：变量配置 POJO `(key,label,type,required,placeholder,List<String> options)` + getter/setter（课件字段逐字） | 编译通过 |
| T004 | writing-storage | 新建 `es/TemplateDoc.java`：ES 模板文档 POJO `(id,userId,name,description,structure,List<TemplateVariable> variables,prompt,status,createTime,updateTime)` + getter/setter；`id` 为空由仓库生成 `tpl-` 前缀；`status` 默认 `enabled` | 编译通过 |
| T005 | writing-storage | **实现** `es/TemplateRepository.java`（落地第 1 节占位接口）：`ensureIndex()`（建 `writing_template`，nested variables + status keyword mapping）；`save(TemplateDoc)`（IndexRequest，无 id 自动生成，写 create/update_time）；`findByIdAndUserId(String,Long)`→`Optional<TemplateDoc>`（`bool(_id+user_id)` 检索，越权按不存在）；`findByUserId(Long)`→`List<TemplateDoc>`（user_id 列出）；`delete(String,Long)`（DeleteByQuery 内嵌 `_id+user_id`，他人模板不删）；`enable(String,Long)`/`disable(String,Long)`（UpdateByQuery 内嵌 user_id 改 status，防 TOCTOU；不存在返回 false）；ES 失败抛 `IOException` | 编译通过 |
| T006 | writing-storage | 新建 `es/TemplateIndexInitializer.java`（`@Component`，`ApplicationRunner`）：启动确保 `writing_template` 索引存在，失败记日志不阻塞（镜像 `MaterialChunkIndexInitializer`） | 编译通过 |
| T007 | writing-storage | 新建 `es/TemplateRepositoryTest.java`（harness，`@Tag("integration")` 本地 ES、CI 跳过）：①`ensureIndex_createsWritingTemplate` 索引建好含 status/variables ②`saveAndFind_roundTrip` 存读回一致、无 id 自动生成 ③**关键回归 `templates_isolatedByUser_otherUsersInvisible`**（userB 查不到 userA 模板，`findByIdAndUserId(id,userB)` 空）④**关键回归 `disableTemplate_statusFlipsEnabledToDisabled`**（enable→disable→enable 状态翻转）⑤`delete_removesOnlyOwnTemplate`（删除后空、他人模板不受影响） | integration 绿（本地 ES） |
| T008 | writing-business | 新建 `TemplateService.java`（`@Service`，`@RequiredArgsConstructor` 注入 `TemplateRepository`）：`render(TemplateDoc, Map<String,Object>)`——①遍历 variables，`required=true` 且参数缺 key → `throw new BusinessException(ErrorCode.MISSING_REQUIRED_PARAM, v.getKey())` ②按 `${key}` 替换 structure 占位符 ③返回 `tpl.getPrompt() + "\n\n" + body`（课件主角代码原样，**不校验 status、不调 LLM、不落稿件**） | 编译通过 |
| T009 | writing-business | 新建 `TemplateServiceTest.java`（harness，`@ExtendWith(MockitoExtension.class)` mock repo 满足构造）：①**关键回归 `render_replacesAllPlaceholders_noResidue`**（fullParams 渲染后 `assertFalse(rendered.contains("${"))`，无残留）②**关键回归 `render_missingRequiredParam_throws`**（缺 week_work → `assertThrows(BusinessException)`，errorCode=`MISSING_REQUIRED_PARAM`）③`render_optionalMissing_doesNotThrow` ④`render_assemblePrompt_containsStructureAndPrompt`（以 `prompt+"\n\n"` 开头含填充正文）⑤`render_nonVariablePlaceholder_remainsLiteral`（未声明占位符无参原样保留，不 NPE） | 单测绿 |
| T010 | writing-start | 扩展 `application.yaml`：`elasticsearch.index.template: writing_template`（模板索引配置键，与素材索引并列，便于运维核对） | 配置加载正确 |
| T011 | 全部 | 硬门禁：`BGE_MODEL_PATH=/Users/Administrator/bge-m3 mvn clean verify` 全绿（P3C 组合，8 模块 SUCCESS，前序节回归绿） | 全量 verify SUCCESS |
| T012 | specs | 验收报告 `specs/005-template-engine/acceptance.md`（技能第 7 步六项证据 DoD + 剩余人工项） | 报告完成 |

## 软停点：任务清单 ↔ 课件"本节交付物"自动比对

| 课件交付物（§三模块分工表 + §四 harness） | 落地 | 说明 |
|---|---|---|
| `TemplateRepository`（writing-storage） | ✅ T005 | **实现**第 1 节占位接口：CRUD + 启停 + user 隔离（本请求范围核心） |
| `TemplateService`（writing-business） | ✅ T008 | 渲染 + 变量填充 + Prompt 组装（本请求范围核心，课件主角代码原样） |
| `TemplateController`（writing-api） | ⏭ 不在本节 | writing-api 属第 8 节（本请求点名 business/storage，不含） |
| `VariableFormBuilder`（前端动态表单） | ⏭ 不在本节 | Vue3 属第 7 节前端课 |
| `TemplateServiceTest` | ✅ T009 | 必填缺失报错 + 无残留占位符（两个关键回归原样落地） |
| `TemplateRepositoryTest` | ✅ T007 | ES 存取 + user_id 隔离 + 启用停用生效 |
| `TemplateControllerTest`（停用不可用于新写作/历史不受影响） | ⏭ 不在本节 | 依赖 Controller，第 8 节补；本节 T007 保证 status 状态落地正确 |

**比对结论**：本请求范围内（business/storage）交付物**齐全**；Controller/前端/ControllerTest 明确排除（非本节点名范围，且依赖第 7/8 节），不缺不漏不超。
