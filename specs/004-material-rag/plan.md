# Implementation Plan: 素材 RAG 管线（Material RAG Pipeline）

**Branch**: `lesson4-material-rag` | **Date**: 2026-10-03 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/004-material-rag/spec.md`

## Summary

按决策五（RAG 管线自实现）实现素材 RAG 管线：`DocumentParser`（writing-storage，POI/PDFBox 解析 docx/pdf/txt/md，不支持格式抛 `UNSUPPORTED_FORMAT`）；`ChunkSplitter`（writing-storage，固定长度 + 重叠切片，`rag.chunk.size/overlap` yml 可配，输出 `Chunk(materialId, index, text)`）；`MaterialChunkRepository`（writing-storage，新增 `batchUpsert`/`search`/`findByMaterialId`，检索 filter 强制 user_id，既有 `saveChunks`/`searchByUser`/`deleteByMaterialId` 不动）；`MaterialService`（writing-business，`upload` 全链路编排——UserContext 取 userId → 解析 → 保存 MySQL 主记录 → 切片 → 经 `ModelService.embed` 向量化 → ES `batchUpsert` → 更新 chunk_count，任一步失败 `@Transactional` 回退无孤儿；`delete`——校验归属 → 先清 ES 切片 → 再 MySQL 逻辑删除）。Embedding 复用第 2 节 `ModelService.embed`（唯一出口，恒 1024 维归一化，不直连 `EmbeddingClient`）。

## Technical Context

**Language/Version**: Java 21 (Spring Boot 3.3.5)，P3C 门禁 targetJdk=20（代码避免 Java 21-only 语法形态）

**Primary Dependencies**:
- 前序交付物（依赖检查已核实）：`MaterialChunkRepository`/`MaterialChunk`/`WritingMaterial`/`WritingMaterialRepository.findByIdAndUserId`/`EsIndexConstants`（writing-storage，第 1 节）；`ModelService.embed`（writing-model，第 2 节）；`UserContext`/`BusinessException`/`ErrorCode`（writing-common，第 3 节）
- POI（`poi`/`poi-ooxml`，docx）、PDFBox（`pdfbox`，pdf）——writing-storage 已有
- ES `RestHighLevelClient`（writing-storage 已有）、ES7.17 索引 `writing_material_chunk`（第 1 节启动初始化，dim=1024 cosine）
- **无新增第三方依赖**（扩展名提取手写 lastIndexOf，不引入 commons-io；Service 不依赖 `MultipartFile`/spring-web）

**切片参数（FR6）**: `rag.chunk.size`（默认 512）/ `rag.chunk.overlap`（默认 50）写入 writing-start `application.yaml`；`ChunkSplitter` 用 `@Value("${rag.chunk.size:512}")` / `@Value("${rag.chunk.overlap:50}")` 兜底。

**回退与一致性（FR5）**:
- `upload`：解析 → `writing_material` save（拿 materialId，chunk_count=0）→ 切片 → 向量化 → `batchUpsert`（ES bulk）→ 更新 chunk_count；任何一步抛错 → `@Transactional` 回滚 MySQL 主记录、ES 未写入，无孤儿数据。
- `delete`：`findByIdAndUserId` 校验归属（不属于当前用户按不存在处理）→ 先 `deleteByMaterialId` 清 ES（失败抛错中止，MySQL 不动）→ 再 `materialRepository.delete`（`@SQLDelete` 逻辑删除）。删除后 `findByMaterialId` 为空（关键回归）。

**API / 契约（本节不建 REST）**:
- 本节交付 Service/组件能力：`MaterialService.upload(String originalFilename, InputStream in)`（userId 从 `UserContext.require()`，不从前端参数取；Controller 层在第 8 节将 `MultipartFile` 转为 `(fileName, InputStream)` 调用）与 `MaterialService.delete(Long materialId)`。
- `ErrorCode` 新增枚举值（writing-common，第 3 节枚举扩展，非新 public 类型）：`UNSUPPORTED_FORMAT(1002)`（课件点名）、`PARSE_FAILED(1003)`（坏文件/解析失败）、`MATERIAL_NOT_FOUND(1004)`（越权/不存在统一按不存在处理）。
- 既有 ES 仓库方法签名/索引 mapping 全部不动（第 1 节契约回归）；`MaterialChunkRepository` 仅新增 `batchUpsert(List<Chunk>, float[][])`（内部 `UserContext.require()` 取 userId，委托既有 bulk 写入逻辑）、`search(Long userId, float[], int)`（返回切片文本列表，内部复用 `searchByUser` 的 user_id filter + cosine 检索）、`findByMaterialId(Long)`（返回该素材全部切片）。

**Testing**（harness 先行，方法名英文 + `@DisplayName` 保留课件原文）:
- `ChunkSplitterTest`（writing-storage，单测，`ReflectionTestUtils` 设 `@Value` 字段）：固定长度+重叠切得对（start 前进 `size-overlap`、末片收束）；空文档返回空；短文档单一切片；`overlap >= size` 抛配置错误。
- `DocumentParserTest`（writing-storage，单测，POI/PDFBox 构造真实字节流）：docx/pdf/txt/md 四种解析正确；pdf 含分页拼接；不支持格式抛 `UNSUPPORTED_FORMAT`；损坏 PDF 抛 `PARSE_FAILED` 不静默。
- `MaterialChunkRepositoryTest`（writing-storage，扩展既有 `@Tag("integration")` 类，本地 ES 跑、CI 跳过）：`batchUpsert` 写入正确；**关键回归 `search_carriesUserId_returnsOnlyOwnChunks`**（userA/userB 数据隔离，`search(userB, vec, 5)` 只含 B）；**关键回归 `deleteMaterial_removesAllEsChunks`**（delete 后 `findByMaterialId` 为空）。
- `MaterialServiceTest`（writing-business，单测，`@ExtendWith(MockitoExtension.class)` 纯 mock）：`upload_fullPipeline_savesMaterialAndUpsertsChunks`（ArgumentCaptor 断言 ES 收到与切片数一致的 chunks/vectors、MySQL 主记录含 chunk_count）；`upload_embeddingFailure_rollsBackNoPartialData`（embed 抛错 → 抛业务异常、ES `batchUpsert` 从未调用、MySQL 未保存）；`deleteMaterial_removesAllEsChunks`（ES 清理 + MySQL 逻辑删除均发生）；`delete_otherUsersMaterial_throwsNotFound`（findByIdAndUserId 空 → 抛 `MATERIAL_NOT_FOUND`）。
- 单测默认跑；integration 冒烟 `@Tag("integration")` CI 跳过；**实现完成的定义是 `mvn clean verify` 全绿**。

**Constraints**:
- 依赖方向：writing-business → writing-storage + writing-model + writing-common；writing-storage → writing-common；无循环。
- user_id 唯一合法来源 = `UserContext`（H4 不变量①）；ES 检索/入库强制 user_id（batchUpsert 内部 `UserContext.require()`）；禁止前端参数取。
- 不新增第三方依赖；不建新表/不改 ES 索引 mapping；不改前序节已定字面量（仅 `ErrorCode` 加枚举值，课件点名 `UNSUPPORTED_FORMAT`）。
- 凭证/模型路径全 `${ENV_VAR}`（H4 不变量③）；Embedding 恒 1024 维 + ES dim=1024 cosine（H4 不变量④，前序回归）。

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 检查 |
|------|------|
| I 单体 7 模块单向依赖 | ✅ 只动 writing-storage / writing-model（消费）/ writing-business / writing-common（枚举值）/ writing-start（配置），无新模块无循环 |
| II Agent 无状态 | 本节无 Agent，不涉及（ES 检索/入库为纯数据服务，无状态） |
| III AgentScope 边界 / 模型唯一出口 | ✅ MaterialService 经 `ModelService.embed` 向量化，不直连 `EmbeddingClient`（宪法三 / 决策九） |
| IV 极简登录 | 不涉及 |
| V 查询强制 user_id | ✅ upload/delete/search/batchUpsert 全部 `UserContext.require()`，素材归属 `findByIdAndUserId` 校验 |
| VI JPA 规范 | ✅ 复用第 1 节 `WritingMaterial`（无新表），逻辑删除 `@SQLDelete` 生效 |
| VII RAG 管线自实现 | ✅ 本节核心：解析→切片→向量化→ES 入库→user_id 检索；任一步失败回退无孤儿；删除同步清 ES |
| VIII 记忆三层 | 不涉及 |
| IX 三类写作共享底座 | 不涉及（第 5 节 ESRetrieveTool 接入本检索能力） |
| X 可演示成果 | ✅ harness 测试全绿 + 本地 ES integration 冒烟（上传→检索→删除链路可演示） |

## Project Structure

### Documentation (this feature)

```text
specs/004-material-rag/
├── spec.md              # 本节规格（已产出）
├── plan.md              # 本文件
└── tasks.md             # /speckit-tasks 输出（本 plan 不创建）
```

### Source Code (repository root)

本节交付物分布在三个模块（技术方案 §6 落位表）：

```text
writing-storage/src/main/java/com/scriptagent/writing/storage/
├── chunk/                              # 【新增】切片领域对象与工具
│   ├── Chunk.java                      # Chunk(materialId, chunkIndex, chunkText)
│   └── ChunkSplitter.java              # @Component，固定长度+重叠，yml 可配
├── parser/                             # 【新增】文档解析
│   └── DocumentParser.java             # @Component，docx(POI)/pdf(PDFBox)/txt/md
└── es/
    └── MaterialChunkRepository.java    # 【扩展】新增 batchUpsert/search/findByMaterialId（既有方法不动）

writing-storage/src/test/java/com/scriptagent/writing/storage/
├── chunk/
│   └── ChunkSplitterTest.java          # 【新增】单测
├── parser/
│   └── DocumentParserTest.java         # 【新增】单测
└── es/
    └── MaterialChunkRepositoryTest.java # 【扩展】integration：新增 3 个验收点（含 2 个关键回归）

writing-business/src/main/java/com/scriptagent/writing/business/
└── MaterialService.java                # 【新增】upload/delete 全链路编排（@Transactional）

writing-business/src/test/java/com/scriptagent/writing/business/
└── MaterialServiceTest.java            # 【新增】单测（mock 全链路 + 回退 + 越权删除）

writing-common/src/main/java/com/scriptagent/writing/common/exception/
└── ErrorCode.java                      # 【扩展】新增 UNSUPPORTED_FORMAT/PARSE_FAILED/MATERIAL_NOT_FOUND

writing-start/src/main/resources/
└── application.yaml                    # 【扩展】新增 rag.chunk.size/overlap
```

**Structure Decision**: 按技术方案 §6 模块落位表——解析/切片/ES 存取归 writing-storage（`parser`/`chunk` 新子包，`es` 扩展），向量化消费 writing-model 门面（不新增代码），编排归 writing-business；测试与实现同包同模块。Service 不依赖 Servlet API（`MultipartFile` 留在第 8 节 API 层），签名用 `(String fileName, InputStream in)`，userId 一律 `UserContext.require()`。

## Complexity Tracking

> 本节无 Constitution 违例需 justify。
