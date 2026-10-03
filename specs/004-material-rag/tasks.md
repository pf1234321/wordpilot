# 第 4 节 素材 RAG 管线 — 任务清单（/speckit-tasks）

**Branch**: `lesson4-material-rag` | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

实现完成定义：`BGE_MODEL_PATH=/Users/Administrator/bge-m3 mvn clean verify` 全绿（P3C 组合门禁）。

## 前置（已就绪）

- 分支 `lesson4-material-rag` 已建（基于 `lesson3-auth`，干净基线）
- 课件 / TechnicalSolution §6 + 流程二/三 已读；前序 1/2/3 节交付物存在性已核对（见 spec.md 依赖节）
- 无新增第三方依赖（POI/PDFBox/ES/ONNX 均已在既有模块）

## 任务清单

| # | 模块 | 任务 | 验证 |
|---|---|---|---|
| T001 | writing-common | 扩展 `exception/ErrorCode.java`：新增 `UNSUPPORTED_FORMAT(1002,"不支持的文件格式")` / `PARSE_FAILED(1003,"文档解析失败")` / `MATERIAL_NOT_FOUND(1004,"素材不存在")`（课件主角一引用 `UNSUPPORTED_FORMAT`，必须逐字保真） | 编译通过 |
| T002 | writing-storage | 新建 `chunk/Chunk.java`：切片领域对象 `(materialId, chunkIndex, chunkText)` + getter（课件主角二构造签名 `new Chunk(materialId, index, text)`） | 编译通过 |
| T003 | writing-storage | 新建 `chunk/ChunkSplitter.java`（`@Component`）：`@Value("${rag.chunk.size:512}")`/`@Value("${rag.chunk.overlap:50}")`；`split(String text, long materialId)` 按课件算法（start 每次前进 `size-overlap`，`end=min(start+size,len)`，`end==len` 即停）；空文本返回空；`overlap >= size` 抛 `IllegalStateException`（防死循环） | 编译通过 |
| T004 | writing-storage | 新建 `parser/DocumentParser.java`（`@Component`）：`parse(String fileName, InputStream in)` —— 扩展名手写提取（不引 commons-io）→ docx 用 POI `XWPFDocument` 段落 `\n` 连接；pdf 用 PDFBox（含分页拼接）；txt/md UTF-8 直读；不支持格式抛 `BusinessException(UNSUPPORTED_FORMAT)`；坏文件抛 `BusinessException(PARSE_FAILED)`（不静默产出空文本） | 编译通过 |
| T005 | writing-storage | 扩展 `es/MaterialChunkRepository.java`：新增 `batchUpsert(List<Chunk>, float[][])`（内部 `UserContext.require()` 取 userId，doc id=`{materialId}_{chunkIndex}`，复用既有 bulk 写入路径）、`search(Long userId, float[], int)` → `List<String>`（复用 `searchByUser` 的 user_id filter + cosine，返回切片文本）、`findByMaterialId(Long)` → `List<MaterialChunk>`（termQuery material_id）；**既有 `saveChunks`/`searchByUser`/`deleteByMaterialId` 一行不动** | 编译通过 |
| T006 | writing-storage | 新建 `chunk/ChunkSplitterTest.java`（harness，单测，`ReflectionTestUtils` 设 `@Value` 字段）：①固定长度+重叠切得对（起点前进、重叠 50、末片收束）②空文档返回空列表 ③短文档单一切片 ④`overlap>=size` 抛配置错误 | 单测绿 |
| T007 | writing-storage | 新建 `parser/DocumentParserTest.java`（harness，单测，POI/PDFBox 构造真实字节流）：①docx 段落解析正确 ②pdf 两页分页拼接正确 ③txt/md 直读正确 ④不支持格式抛 `UNSUPPORTED_FORMAT` ⑤损坏 PDF 抛 `PARSE_FAILED` 不落半成品 | 单测绿 |
| T008 | writing-storage | 扩展 `es/MaterialChunkRepositoryTest.java`（harness，`@Tag("integration")` 本地 ES、CI 跳过）：①`batchUpsert` 写入后可被检索（含 user_id）②**关键回归 `search_carriesUserId_returnsOnlyOwnChunks`**（`search(userB, vec, 5)` 只含 userB 切片，userA 数据不泄漏）③**关键回归 `deleteMaterial_removesAllEsChunks`**（delete 后 `findByMaterialId` 为空） | integration 绿（本地 ES） |
| T009 | writing-business | 新建 `MaterialService.java`（`@Service`）：`upload(String originalFilename, InputStream in)` —— `UserContext.require()` → `DocumentParser.parse` → `writing_material` save（拿 materialId，chunk_count=0）→ `ChunkSplitter.split` → 逐片 `ModelService.embed`（不直连 EmbeddingClient）→ 非空时 `MaterialChunkRepository.batchUpsert` → 更新 chunk_count；任一步抛错 `@Transactional` 回滚无孤儿。`delete(Long materialId)` —— `findByIdAndUserId` 校验归属（不存在/越权 → `MATERIAL_NOT_FOUND`）→ 先 `deleteByMaterialId` 清 ES（失败中止）→ 再逻辑删除 | 编译通过 |
| T010 | writing-business | 新建 `MaterialServiceTest.java`（harness，`@ExtendWith(MockitoExtension.class)` 纯 mock）：①`upload_fullPipeline_savesMaterialAndUpsertsChunks`（ArgumentCaptor：ES 收到与切片数一致的 chunks/vectors，主记录 chunk_count 正确）②`upload_embeddingFailure_rollsBackNoPartialData`（embed 抛错 → 抛异常、`batchUpsert` 从未调用、MySQL 未保存）③`upload_unsupportedFormat_throwsAndNoSave` ④**关键回归 `deleteMaterial_removesAllEsChunks`**（ES 清理 + MySQL 逻辑删除）⑤`delete_otherUsersMaterial_throwsNotFound` | 单测绿 |
| T011 | writing-start | 扩展 `application.yaml`：新增 `rag.chunk.size: 512` / `rag.chunk.overlap: 50`（切片参数可配，不写死） | 配置加载正确 |
| T012 | 全部 | 硬门禁：`BGE_MODEL_PATH=/Users/Administrator/bge-m3 mvn clean verify` 全绿（P3C 组合，8 模块 SUCCESS，前序节回归绿） | 全量 verify SUCCESS |
| T013 | specs | 验收报告 `specs/004-material-rag/acceptance.md`（技能第 7 步六项证据 DoD + 剩余人工项） | 报告完成 |

## 软停点：任务清单 ↔ 课件"本节交付物"自动比对

| 课件交付物（§三代码 + §四 harness） | 落地 | 说明 |
|---|---|---|
| `DocumentParser`（writing-storage） | ✅ T004 | docx/pdf/txt/md 解析 + 明确报错 |
| `ChunkSplitter`（writing-storage） | ✅ T003 | 固定长度+重叠，yml 可配 |
| `Chunk`（切片对象，`new Chunk(materialId, index, text)`） | ✅ T002 | 课件主角二返回类型 |
| `EmbeddingClient`（writing-model） | ✅ 前序已交付（第 2 节） | 本节仅经 `ModelService.embed` 消费，不新增代码（宪法三唯一出口） |
| `MaterialChunkRepository`（writing-storage） | ✅ T005 | 新增 `batchUpsert`/`search`/`findByMaterialId`；既有方法不动 |
| `MaterialService`（writing-business） | ✅ T009 | upload/delete 全链路编排 |
| `rag.chunk.size/overlap` 配置（yml 可改） | ✅ T011 | 默认 512/50，`@Value` 兜底 |
| harness：`DocumentParserTest` | ✅ T007 | 四种格式 / 坏文件 / 不支持格式报错且不落库 |
| harness：`ChunkSplitterTest` | ✅ T006 | 长度+重叠 / 空文档 / 短文档边界 |
| harness：`MaterialChunkRepositoryTest` | ✅ T008 | ES 写入/检索 / 强制 user_id / 按 material_id 清干净 |
| harness：`MaterialServiceTest` | ✅ T010 | 上传全链路 / 向量化失败回退无半成品 |
| 关键回归：`deleteMaterial_removesAllEsChunks` | ✅ T008 + T010 | 仓库层（真实 ES）+ 编排层（mock）双保险 |
| 关键回归：`search_carriesUserId_returnsOnlyOwnChunks` | ✅ T008 | `search(userB, vec, 5)` 只应召回 userB |
| （补充）`ErrorCode` 新增枚举值 | ✅ T001 | 课件主角一代码点名 `ErrorCode.UNSUPPORTED_FORMAT`，必须存在 |
| （补充）`MaterialService` 签名 | ✅ T009 | 课件示例 `upload(userId, MultipartFile)` 调整为 `upload(fileName, InputStream)`：userId 从 `UserContext.require()`（H4 不变量①，禁止参数取）；business 不依赖 spring-web/Servlet API |

**比对结论**：任务清单与课件交付物**一致**（不缺、无文档外新增对外概念、无已定字面量改动——`ErrorCode` 仅加枚举值、ES 仓库仅新增方法、前序节文件除 `ErrorCode`/`application.yaml`/ES 仓库外零触碰）。测试任务（T006/T007/T008/T010）先行/伴随实现（T003/T004/T005/T009）。**课件 harness 关键回归的断言逻辑逐条保真，方法名译英文 + `@DisplayName` 保留课件原文。**
