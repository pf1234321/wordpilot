# Feature Specification: 素材 RAG 管线（Material RAG Pipeline）

**Feature Branch**: `lesson4-material-rag`
**Created**: 2026-10-03
**Status**: Draft
**Input**: User description: 第4节需求：素材 RAG 管线——把用户上传的文档解析、切片、向量化入 ES，写作时按 user_id 语义检索 TopN 供仿写参考。落在决策五（RAG 管线自实现）与 §9.3 模型选型上（Embedding = BGE bge-m3 本地模型 + JVM 进程内 ONNX Runtime 推理，第 2 节已交付）。

## 用户场景与测试

### User Story 1 — 素材上传入库：解析→切片→向量化→ES 入库，失败整体回退（P1）

用户上传 docx/pdf/txt/md 文档作为仿写素材：`DocumentParser` 解析纯文本（POI/PDFBox/txt 直读），`ChunkSplitter` 按固定长度 + 重叠切片（长度/重叠 yml 可配），经 `ModelService.embed`（writing-model 唯一出口，BGE bge-m3 本地 ONNX，恒 1024 维归一化）向量化，`MaterialChunkRepository.batchUpsert` 批量写入 ES `writing_material_chunk`（携带 user_id/material_id），同时 MySQL `writing_material` 落主记录（含 chunk_count）。任一步失败整体报错回退——不产生"MySQL 有主记录但 ES 无切片 / ES 有切片但主记录缺失"的孤儿数据；不支持的格式（非 docx/pdf/txt/md）抛明确错误且不落任何数据。

**Why this priority**：这是"素材文档仿写"能力的地基（TechnicalSolution 流程二），也是宪法原则七（RAG 管线自实现）的落地。回退语义是数据一致性的底线——半成品数据会让后续检索串数据。

**Independent Test**：`MaterialService.upload(file)` 在 mock 全部下游（parser/splitter/embedding/repo）下全链路走通（ES 收到全部切片向量、MySQL 主记录含 chunk_count）；任一环节抛错时断言 MySQL 与 ES 均无写入。

**Acceptance Scenarios**：

1. **Given** 一个合法 docx/txt 输入流，**When** `MaterialService.upload(file)`，**Then** 解析出文本 → 切片 → 向量化 → `MaterialChunkRepository.batchUpsert` 收到与切片数一致的向量 → MySQL 写入素材主记录（含正确的 chunk_count）。
2. **Given** Embedding 环节抛错（或解析抛错），**When** `upload(file)`，**Then** 抛业务异常，MySQL 主记录回滚、ES 无写入（无孤儿数据）。
3. **Given** 不支持的文件格式（如 `.exe` / `.doc`），**When** `DocumentParser.parse(...)`，**Then** 抛 `BusinessException(ErrorCode.UNSUPPORTED_FORMAT)` 且不落任何数据。
4. **Given** 损坏的 PDF（非 PDF 内容伪后缀），**When** `parse(...)`，**Then** 抛明确异常（不静默产出空文本）。

---

### User Story 2 — 按 user_id 语义检索：只召回当前用户的素材切片（P1）

写作仿写时（第 5 节 Agent 的 ESRetrieveTool 接入前，本节先交付仓库与编排能力），`MaterialChunkRepository.search(userId, queryVector, topK)` 按向量余弦相似度返回 TopN 切片文本；检索**强制携带 user_id**（filter 条件），任何人只能召回自己的素材片段，禁止跨用户召回。

**Why this priority**：这是"用户数据完全隔离"（宪法原则五 / H4 不变量①）在检索侧的直接落地；漏 user_id 即越权漏洞，是本节的坑之一，必须用测试锁死。

**Independent Test**：`search(userB, vec, 5)` 只返回 userB 的切片（userA 的数据即使相似度更高也不出现）。

**Acceptance Scenarios**：

1. **Given** userA 与 userB 各写入一批切片，**When** `search(userB, vec, 5)`，**Then** 只返回 userB 的切片，不含 userA 任何片段。
2. **Given** 查询向量与某素材切片高度相似，**When** `search(userId, vec, topK)`，**Then** 返回 TopN 中相似度最高的切片文本列表。

---

### User Story 3 — 删除素材：MySQL 逻辑删除 + ES 按 material_id 清干净（P1）

用户删除素材：`MaterialService.delete(materialId)` 校验素材属于当前用户（findByIdAndUserId），先按 `material_id` 清理 ES 全部切片（`deleteByMaterialId`，配合 `findByMaterialId` 可验证），再 MySQL 逻辑删除（`@SQLDelete` 打标 `deleted=1`）。任一失败抛错中止。删除后 ES 无该素材任何残留，后续检索不会召回已删素材。

**Why this priority**：不清理 ES 会残留孤儿向量、搜索串数据（课件明确列为坑）；逻辑删除保住历史主记录。

**Independent Test**：`materialService.delete(userA, materialId)` 后 `materialChunkRepository.findByMaterialId(materialId)` 为空。

**Acceptance Scenarios**：

1. **Given** 某素材已有切片入库，**When** `materialService.delete(userA, materialId)`，**Then** `findByMaterialId(materialId)` 为空、MySQL 主记录逻辑删除（`deleted=1`）。
2. **Given** 用户 B 尝试删除用户 A 的素材，**When** `materialService.delete(userB, materialA)`，**Then** 抛异常（素材不存在或无权），不产生任何删除动作。

---

### User Story 4 — 切片参数可配置：召回质量可调（P2）

切片长度 `rag.chunk.size`（默认 512）与重叠 `rag.chunk.overlap`（默认 50）从 yml 读取（`@Value` 带默认值兜底），不写死在代码里；相邻切片通过重叠保证语义不断裂。

**Why this priority**：课件明确"切片参数写死 → 召回质量没法调。长度/重叠必须配 yml 可改"。

**Independent Test**：`ChunkSplitterTest` 用不同 size/overlap 组合断言切片边界正确；yaml 存在 `rag.chunk.size/overlap` 配置项。

**Acceptance Scenarios**：

1. **Given** 文本长度 L，chunkSize=512、overlap=50，**When** `split(text, materialId)`，**Then** 切片起点每次前进 462，相邻切片重叠 50 字，末片以文本结尾收束（`end == text.length()` 即停）。
2. **Given** 空文本 / 短于 chunkSize 的文本，**When** `split(...)`，**Then** 空文本返回空列表、短文本返回单一切片。
3. **Given** 配置文件中 `rag.chunk.size/overlap`，**When** 修改配置重启，**Then** 切片行为随之改变（不修改代码）。

---

### Edge Cases

- **不支持格式**（`.doc`、`.exe`、`.png` 等非 docx/pdf/txt/md）：`DocumentParser` 抛 `UNSUPPORTED_FORMAT`，不产生半成品。
- **损坏文件**：伪后缀 PDF / 截断 docx → 解析抛明确异常（包装 IOException 为业务可读错误），不静默返回空文本。
- **向量化失败**：任一切片 embed 抛错 → 整体回退，不落半截数据（MySQL 回滚 + ES 未写）。
- **重叠参数非法**（`overlap >= chunkSize`）：`ChunkSplitter` 抛配置错误（步长为负会死循环）。
- **越权删除 / 检索**：素材归属校验（findByIdAndUserId）+ ES filter 强制 user_id；不属于当前用户的素材按"不存在"处理。
- **空文本素材**：切片数为 0，ES 不写空 bulk，MySQL 主记录 chunk_count=0（可正常保存或按业务拒绝，本节取"正常保存、chunk_count=0"）。

---

## 功能需求（FR，从课件"想清楚"提炼，每条可测试）

- **FR1 解析（DocumentParser → writing-storage）**：按扩展名分发，docx 用 POI `XWPFDocument` 读段落（`\n` 连接）、pdf 用 PDFBox 抽文本（含分页拼接）、txt/md 直接 UTF-8 读取；不支持格式抛 `ErrorCode.UNSUPPORTED_FORMAT`；坏文件抛明确异常，不产生半成品。
- **FR2 切片（ChunkSplitter → writing-storage）**：`@Value("${rag.chunk.size:512}")` / `@Value("${rag.chunk.overlap:50}")`；起点每次前进 `chunkSize - overlap`，`end = min(start + chunkSize, len)`，`end == len` 即停；返回 `Chunk(materialId, chunkIndex, chunkText)` 列表；空文本返回空；`overlap >= chunkSize` 抛配置错误。
- **FR3 向量化（writing-model，前序已交付，本节仅消费）**：经 `ModelService.embed(text)`（唯一出口，恒 1024 维归一化），MaterialService 不得直接持有 `EmbeddingClient`。
- **FR4 ES 入库/检索（MaterialChunkRepository → writing-storage）**：新增 `batchUpsert(List<Chunk>, float[][])`（ES bulk 写入，doc id=`{materialId}_{chunkIndex}`，携带 user_id/material_id）、`search(Long userId, float[] queryVector, int topK)`（filter 强制 user_id + cosine 相似度，返回切片文本列表）、`findByMaterialId(Long)`（按 material_id 查全部切片，供删除验证）。既有 `saveChunks/searchByUser/deleteByMaterialId` 保持不动（第 1 节契约回归）。
- **FR5 编排（MaterialService → writing-business）**：`upload(MultipartFile file)` 全链路——`UserContext.require()` 取 userId（禁止前端参数）→ 解析 → 保存 MySQL 主记录（拿 materialId）→ 切片 → 向量化 → `batchUpsert` → 更新 chunk_count；任一步失败抛错，`@Transactional` 回滚无孤儿。`delete(Long materialId)`——校验归属 → 先清 ES（`deleteByMaterialId`）→ 再 MySQL 逻辑删除。
- **FR6 配置（writing-start application.yaml）**：新增 `rag.chunk.size: 512` / `rag.chunk.overlap: 50`（不写死，可调优召回）。

## 明确不做（边界，逐项照搬课件"想清楚"）

- **不做 REST 接口 / Controller**（`POST /api/material/upload` 属 API 层，第 8 节写作编排 / 前端联调阶段落地；本节只交付 storage 与 business 两个模块的 Service/组件能力）。
- **不引入现成 RAG 框架**（决策五自实现：POI/PDFBox + 自研切片 + ES + ONNX 均为成熟组件，组合逻辑自控）。
- **不做 LLM 对话 / 仿写 Prompt 组装**（第 2 节已交付 LLM，仿写链路第 5 节 Agent + ESRetrieveTool）。
- **不做模板检索**（`TemplateRepository` 第 6 节实现）。
- **不新增任何第三方依赖**（POI/PDFBox/ES/ONNX/commons 均在既有依赖或前序已就绪；扩展名提取手写，不引入 commons-io）。
- **切片参数不得写死**（必须 yml 可配）。
- **检索 / 删除不得漏 user_id / material_id**（越权与孤儿数据的红线）。
- **不修改前序节已定字面量**：第 1 节 `MaterialChunkRepository` 既有方法（saveChunks/searchByUser/deleteByMaterialId）、`MaterialChunk`、ES 索引名与 mapping 不变；仅新增方法。

## 依赖与假设

- **前序交付物（依赖检查已核实存在）**：
  - 第 1 节：`MaterialChunkRepository`（ensureIndex/saveChunks/searchByUser/deleteByMaterialId）、`MaterialChunk`、`WritingMaterial`/`WritingMaterialRepository`（findByIdAndUserId）、`EsIndexConstants`（EMBEDDING_DIM=1024、SIMILARITY=cosine）、POI/PDFBox/ES 依赖；
  - 第 2 节：`ModelService.embed`（唯一出口，恒 1024 维归一化）、`EmbeddingClient`；
  - 第 3 节：`UserContext`（require/get）、`ErrorCode`（本节新增 `UNSUPPORTED_FORMAT` 枚举值）、`BusinessException`。
- **外部依赖**：ES7.17（writing_material_chunk 索引由第 1 节启动初始化）、Redis4、MySQL8、POI、PDFBox、ONNX Runtime（第 2 节已配）。
- **假设**：upload 的 `MultipartFile` 由 Controller 层（第 8 节）转交，本节方法签名面向 service 调用（文件以 `InputStream` + 文件名传入，避免 Service 依赖 Servlet API——签名以 `upload(String originalFilename, InputStream in)` 落地，Controller 转 MultipartFile 在 API 层完成）。
