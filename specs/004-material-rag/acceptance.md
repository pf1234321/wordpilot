# 第 4 节验收报告：素材 RAG 管线（Material RAG Pipeline）—— 六项证据 DoD

* 分支：`lesson4-material-rag`（基于 `lesson3-auth`，第 3 节已提交，基线干净）
* 验收时间：2026-10-03
* 执行方式：`wordpilot-lesson-dev` 全程流程（H0→specify→clarify→plan→tasks 停点→implement→六项证据 DoD）

## 第 1 项证据：`mvn clean verify` 全绿（含 P3C/SpotBugs/FindSecBugs/PMD）

根目录执行 `BGE_MODEL_PATH=/Users/Administrator/bge-m3 mvn clean verify`（含真实 BGE bge-m3 ONNX 推理），**BUILD SUCCESS**，8 个模块全部 SUCCESS，总时长 ~42s：

```
[INFO] writing-common ..................................... SUCCESS [  5.944 s]
[INFO] writing-model ...................................... SUCCESS [ 11.518 s]   ← 前序节（含真实 BGE 推理）
[INFO] writing-storage .................................... SUCCESS [  9.342 s]   ← 本节 DocumentParser/ChunkSplitter/ES
[INFO] writing-business ................................... SUCCESS [  4.363 s]   ← 本节 MaterialService
[INFO] writing-api ........................................ SUCCESS [  4.102 s]
[INFO] writing-start ...................................... SUCCESS [  4.321 s]
[INFO] BUILD SUCCESS
```

静态门禁（P3C PMD 组合 / checkstyle / SpotBugs / FindSecBugs / dependency-check / spotless）全模块全绿，无妥协。

## 第 2 项证据：课件 harness 映射表每个测试类存在且非空，关键回归逐个对号

本节单测（storage + business）与 integration（本地 ES 7.17 放开）：

| 测试类 | 落地路径 | 覆盖的验收点（课件） | 结果 |
|---|---|---|---|
| `DocumentParserTest` | writing-storage/.../parser/DocumentParserTest.java | docx/pdf/txt/md 四种解析正确；坏文件/不支持格式报错且不落库 | ✅ 6/6 |
| `ChunkSplitterTest` | writing-storage/.../chunk/ChunkSplitterTest.java | 固定长度+重叠切得对；空文档/短文档边界；非法 overlap 防御 | ✅ 4/4 |
| `MaterialChunkRepositoryTest` | writing-storage/.../es/MaterialChunkRepositoryTest.java（扩展既有 integration 类） | ES 写入/检索正确；检索强制 user_id；删除按 material_id 清干净 | ✅ 6/6（含 3 新用例） |
| `MaterialServiceTest` | writing-business/.../MaterialServiceTest.java | 上传全链路成功；向量化失败回退不产生半成品；越权删除 | ✅ 6/6 |

**关键回归对号（断言逐条保真，方法名英文 + @DisplayName 保留课件原文）**：
- `deleteMaterial_removesAllEsChunks`：**双保险**——仓库层（`MaterialChunkRepositoryTest`，真实 ES：`batchUpsert` 3 切片 → `findByMaterialId` hasSize(3) → `deleteByMaterialId` → `findByMaterialId` isEmpty）+ 编排层（`MaterialServiceTest`：`deleteByMaterialId` + `repository.delete` 均被调用）。
- `search_carriesUserId_returnsOnlyOwnChunks`（`MaterialChunkRepositoryTest`，真实 ES）：`search(userB, vec, 5)` 只命中 userB 的切片，userA 数据不泄漏（`allMatch(t -> t.startsWith("userB"))`）。

## 第 3 项证据：本节交付物逐项存在性核对

| 交付物（课件 §三代码 + 配置） | 路径 | 存在 |
|---|---|---|
| `DocumentParser`（writing-storage） | writing-storage/.../parser/DocumentParser.java | ✅ |
| `ChunkSplitter`（writing-storage） | writing-storage/.../chunk/ChunkSplitter.java | ✅ |
| `Chunk`（切片对象） | writing-storage/.../chunk/Chunk.java | ✅ |
| `MaterialChunkRepository` 新增 `batchUpsert`/`search`/`findByMaterialId` | writing-storage/.../es/MaterialChunkRepository.java | ✅ |
| `MaterialService`（writing-business） | writing-business/.../business/MaterialService.java | ✅ |
| `EmbeddingClient`（writing-model） | 第 2 节已交付，本节仅经 `ModelService.embed` 消费（唯一出口），无新增代码 | ✅ |
| `rag.chunk.size: 512` / `rag.chunk.overlap: 50` 配置 | writing-start/application.yaml | ✅ |
| `ErrorCode.UNSUPPORTED_FORMAT`（课件主角一引用）+ `PARSE_FAILED`/`MATERIAL_NOT_FOUND`/`ES_OPERATION_FAILED` | writing-common/.../exception/ErrorCode.java | ✅ |
| harness 四测试类 | 见第 2 项证据 | ✅ |
| 规格产物 | specs/004-material-rag/{spec,plan,tasks,acceptance}.md | ✅ |

## 第 4 项证据：前序节全部测试回归绿（跨节契约证据）

`mvn clean verify` 全量 reactor 通过：writing-model（含真实 BGE bge-m3 ONNX 推理 11.5s，EmbeddingClient 恒 1024 维/归一化）、writing-common、writing-storage、writing-api、writing-start 全部 SUCCESS。第 1/2/3 节交付物（Entity/Repository/ModelService/UserContext/LoginService 等）编译与测试通过；跨节契约点回归验证：`ModelService.embed`（第 2 节）被 `MaterialService` 消费、`WritingMaterial`/`WritingMaterialRepository.findByIdAndUserId`（第 1 节）被本节 upload/delete 复用、`UserContext.require`（第 3 节）为 userId 唯一来源。

## 第 5 项证据：H4 六条全局不变量逐条自查

| # | 不变量 | 自查结果 |
|---|---|---|
| ① | 所有业务查询/检索强制 user_id（来源 UserContext，前端参数取一律拒绝） | ✅ `MaterialService.upload/delete` 均 `UserContext.require()`；`batchUpsert` 内部 `UserContext.require()`；ES `search` filter 强制 user_id；无从前端参数取 |
| ② | 逻辑删除 `@SQLDelete`+`@Where`、审计 `@CreatedDate` 生效 | ✅ `WritingMaterial` 复用第 1 节 `@SQLDelete`，`MaterialService.delete` 经 `repository.delete` 打标 deleted=1；前序 AuditTest 回归绿 |
| ③ | grep 无明文 key/密码/模型路径（全 `${ENV_VAR}`） | ✅ 本节无新凭证；`application.yaml` 全 `${DB_PASSWORD}`/`${DASHSCOPE_API_KEY}`/`${BGE_MODEL_PATH}`，Redis/ES 密码为空占位；grep 无 `sk-`/`/Users/Administrator` 硬编码 |
| ④ | Embedding 恒 1024 维、ES 索引 dim=1024 + cosine | ✅ 经 `ModelService.embed`（第 2 节恒 1024 归一化）；ES mapping 引用 `EsIndexConstants`（dim=1024/cosine）；前序 IndexDimensionTest 回归绿 |
| ⑤ | Agent 无状态、状态外置 | 本节无 Agent（ES 入库/检索为无状态数据服务），不适用，未引入反例 |
| ⑥ | AgentCore 不直连大模型，LLM/Embedding 统一经 writing-model 出口 | ✅ `MaterialService` 仅依赖 `ModelService`，grep 无 `EmbeddingClient` 直连（仅注释提及） |

## 第 6 项证据：剩余人工项清单（harness 已判卷，以下等你人工过）

1. **真实 docx 上传**：本地起 writing-start（MySQL/Redis/ES 就绪、登录拿 Token），传一份真实 docx → 素材列表可见、ES 能按该用户搜到切片（`POST /api/material/upload` 接口在本节未建，待第 8 节 Controller 或直接调 `MaterialService.upload` 验证）。
2. **双用户检索隔离**：换另一用户登录检索，取不到前一用户的任何片段。
3. **删除无残留**：删除素材后，ES 该 material_id 无残留（`findByMaterialId` 为空，仓库层 integration 已自动化验证，人工端到端复核一遍）。
4. **损坏 PDF**：传一份损坏 PDF → 明确报 `PARSE_FAILED`，不落半截数据（单测已覆盖，人工端到端复核）。

## 补充证据：真实素材端到端功能测试（MaterialServiceFunctionalIT）

新增 `writing-start/src/test/java/com/scriptagent/writing/MaterialServiceFunctionalIT.java`（`@SpringBootTest` + `@Tag("integration")`，本地 MySQL/ES/BGE 中间件），用 `docs/测试素材/` 下 **4 个真实文档**跑完整 RAG 链路，实测 **1/1 通过**：

```
upload 素材3_AI写作七条方法.txt           → materialId=6  | 原文长度=568 | 切片数=2
upload 素材4_写作引擎技术笔记.md          → materialId=7  | 原文长度=1912 | 切片数=5
upload 素材1_新能源汽车智能诊断系统_方案.docx → materialId=8  | 原文长度=967 | 切片数=2
upload 素材2_新能源汽车故障诊断_白皮书.pdf  → materialId=9  | 原文长度=647 | 切片数=2
search 查询='AI 写作引擎从 0 到 1：原理与工程实践' 召回 3 片：
   召回片段: # AI 写作引擎从 0 到 1：原理与工程实践   ← Top1 命中素材4（最相关）
   召回片段: AI 写作的七条实战方法（…）              ← 同为"AI 写作"主题（素材3）
delete 全部素材 4 份，ES 无残留、MySQL 逻辑删除均通过
```

**验证结论**：四种格式真实文档均完成「upload → MySQL 主记录 + ES 切片 → 同模型向量化检索召回 → delete 清理无残留」全链路；检索 Top1 正确命中查询主题对应素材（相关性成立）。**此前列在人工项的"真实 docx 上传→ES 可检索"与"删除无残留"两项，已由本测试自动化覆盖**；剩余人工项仅为"双用户隔离"与"损坏 PDF 明确报错"的端到端复核。

配套改动：`writing-start/pom.xml` 增加与 writing-storage 一致的 `<surefire.excludedGroups>integration</surefire.excludedGroups>` 属性化配置，使该依赖真实中间件的功能测试默认被 CI 排除（`mvn clean verify` 不依赖中间件，8 模块全绿）。

## 结论

六项证据 DoD 全部满足：全量 `mvn clean verify` 全绿（含真实 BGE 推理 + 全部静态门禁）、harness 四测试类存在且关键回归（`deleteMaterial_removesAllEsChunks` 双保险、`search_carriesUserId_returnsOnlyOwnChunks`）逐个对号、本节交付物逐项存在、前序节回归绿、H4 不变量①③④⑥相关项通过、剩余人工项清单已列明。**第 4 节素材 RAG 管线验收通过。**

> 变更总结（给 reviewer 的导读）直接输出在对话中，含改动点、重点 review 清单、如何验证三段。
