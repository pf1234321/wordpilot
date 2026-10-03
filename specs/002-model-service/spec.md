# Feature Specification: 模型服务（writing-model）

**Feature Branch**: `lesson2-model-service`
**Created**: 2026-10-03
**Status**: Draft
**Input**: User description: 第2节需求：模型服务（writing-model）—— LLM 对话调用（阿里 Qwen / DashScope）与 Embedding 向量化（本地 BGE bge-m3 / ONNX）统一收口，是系统唯一接触大模型的地方。

## 用户场景与测试

### User Story 1 — AgentCore 与业务层只依赖 ModelService，不直连任何模型（P1）

对话写作 Agent、素材 RAG 管线、模板写作编排要生成文本 / 向量化素材，统一调用 `ModelService`（门面）的 `chat()` 与 `embed()`。调用方不知道也不关心底层用的是哪家 LLM、Embedding 怎么算——模型对上层完全透明。

**Why this priority**：这是决策九（AgentCore 依赖模型服务）与 constitution 原则三的落地——"模型服务是 LLM/Embedding 唯一出口，换模型只改模型服务层"。若任何一层绕过门面直连模型，换模型就要动上层，违背唯一出口。

**Independent Test**：调用 `ModelService.chat(messages)` 与 `ModelService.embed(text)` 均返回正确结果；代码库 grep 确认 AgentCore / 业务 / RAG 侧没有直接 new `LlmClient` / `EmbeddingClient` 或持有原生客户端的路径。

**Acceptance Scenarios**：

1. **Given** 一个 LLM 客户端与一个 Embedding 客户端，**When** 业务调用 `ModelService.chat(...)`，**Then** 内部委派给 LLM 客户端；调用 `ModelService.embed(...)`，**Then** 内部委派给 Embedding 客户端，门面不自行实现模型逻辑。
2. **Given** 上层要切换模型，**When** 只改模型服务内部实现 / 配置，**Then** Agent 层与业务层代码零改动。

---

### User Story 2 — Embedding 输出恒为 1024 维、归一化、可入 ES（P1）

对任意文本调用 `embed(text)`，返回的向量长度恒等于 1024（与 ES `writing_material_chunk.embedding` 的 `dense_vector.dim` 一致，cosine 距离），同文本两次调用结果稳定，向量模长 ≈ 1（归一化，可直接 cosine 检索）。模型启动时加载一次、单例复用，绝不每次调用重载。

**Why this priority**：维度不一致直接导致 ES 入库 / 检索报错；每次加载 2.3GB 模型是性能灾难；归一化是 cosine 检索正确性的前提。这三个是课件的核心坑，必须用测试锁死。

**Independent Test**：对 "测试文本" 调 `embed`，断言 `vec.length == 1024`、两次调用全等（或近等）、模长在 0.999~1.001 之间。

**Acceptance Scenarios**：

1. **Given** 已加载的 BGE 模型，**When** `embed("测试文本")`，**Then** 返回 `float[1024]`，`dim == 1024`。
2. **Given** 同一文本，**When** 连续两次 `embed`，**Then** 两次向量一致（确定性推理）。
3. **Given** 一次推理结果 `vec`，**When** 计算模长，**Then** `|norm(vec) - 1| < 1e-3`。
4. **Given** ES `writing_material_chunk` 索引，**When** 检查 `embedding` 字段定义，**Then** `dims == 1024`、`similarity == cosine`（引用 `EsIndexConstants.EMBEDDING_DIM` / `SIMILARITY`，禁止硬编码）。

---

### User Story 3 — LLM 对话可走通、错误清晰、key 绝不落明文（P1）

`chat(messages, options)` 经 DashScope API 调用阿里 Qwen 生成文本，网络 / 鉴权错误抛清晰异常；API key 只允许 `${ENV_VAR}` 占位注入，代码与配置文件中不得出现真实 key（如 `sk-` 开头）。

**Why this priority**：凭证安全是 H4 全局不变量③（grep 无明文 key）；错误清晰可排查是生产可用前提。

**Independent Test**：`ModelProperties.getApiKey()` 返回的值不含 `sk-` 前缀；`chat` 在 mock 下游下返回预期文本，在下游异常时抛可读异常。

**Acceptance Scenarios**：

1. **Given** 环境变量 `DASHSCOPE_API_KEY` 已注入，**When** 读取 `modelProperties.getApiKey()`，**Then** 得到真实 key，且代码 / yml 中无明文（只含 `${DASHSCOPE_API_KEY}`）。
2. **Given** 下游 API 返回错误，**When** 调用 `chat`，**Then** 抛出带上下文的清晰异常，不吞错。

---

### Edge Cases

- **启动时模型路径未配置 / 文件缺失**：`load-on-start` 阶段应给出明确报错并记录（H4③ 缺则报错），不静默启动成"假装能向量化"。
- **Embedding 输入为空串 / null**：明确行为（拒绝并抛 IllegalArgumentException 或返回空向量），不产生 NaN。
- **LLM 超时 / 限流**：超时配置走 yml（`model.llm.timeout-seconds`），异常上抛由上层（Agent/流式）处理，本节不吞。
- **测试环境无真实模型 / 无 key**：Embedding 真实调用测试需真模型（用户已确认**提供 BGE 模型路径跑真实单测**，`BGE_MODEL_PATH` 环境变量注入，本地真实加载 + 真实推理）；LLM 测试用 mock 下游（无需真 key），真实 DashScope 调用留人工项。

## 功能需求（FR）

- **FR1**：`LlmClient` 封装阿里 Qwen 的 DashScope 对话调用（provider=dashscope），支持多轮 `List<Message>` 与 `ChatOptions`。
- **FR2**：`EmbeddingClient` 用 JVM 进程内 ONNX Runtime 加载本地 BGE bge-m3（`onnx/model.onnx`），启动加载一次单例，`embed(text)` 返回归一化 1024 维向量。
- **FR3**：`ModelService` 门面暴露 `chat(messages)` / `embed(text)`，只委派，不含模型逻辑。
- **FR4**：`ModelProperties` 集中 `model.llm.*` / `model.embedding.*` 配置；api-key / model-path 一律 `${ENV_VAR}`，不落明文。
- **FR5**：ES 维度契约：`embed` 输出恒 1024 维，与 `EsIndexConstants.EMBEDDING_DIM(1024)`、`SIMILARITY(cosine)` 保持一致。

## 明确不做（边界）

- **不做** AgentCore 直连任何大模型（决策九禁止）。
- **不做** Embedding 走远程 / 独立 HTTP 服务（本方案是本地 + JVM 进程内 ONNX）。
- **不做** 每次调用重新加载模型（必须 load-on-start 单例）。
- **不做** 在代码 / yml 落任何明文 key / 模型路径（一律 `${ENV_VAR}`，H4③）。
- **不做** ES 索引维度硬编码（必须引用常量，禁止写死 1024）。
- **不做** 本节之外的模型适配 / fallback / 混合检索（bge-reranker 等属扩展阶段，本轮不做）。

## 验收标准

可自动化部分由课件"验收 harness"测试套件承载（`mvn test` 全绿即通过）：

| 测试类 | 关键回归点（课件写出代码的守点） |
|--------|--------------------------------|
| `EmbeddingClientTest` | `embed_alwaysReturnsDim1024_matchesEsIndex`：输出维度恒 1024；同文本稳定；归一化（模长≈1） |
| `LlmClientTest` | chat 走通（可 mock）；错误抛清晰异常 |
| `ModelServiceTest` | 门面正确委派；`apiKey_neverReadFromPlaintextConfig`：api-key 从环境变量注入非明文 |
| `IndexDimensionTest` | `writing_material_chunk.embedding` dim=1024、检索 cosine（引用常量） |

人工项（课件"做完怎么验"，harness 判卷后需人工过）：应用启动日志显示 Embedding 模型加载成功（单例）；真实文本向量化可入 ES；调 LLM 生成文本 SSE 正常返回；改环境变量换模型后 Agent 层代码不动重启即生效。

## 依赖与假设

- 依赖前序第 1 节交付：`writing-common` 的 `EsIndexConstants`（`EMBEDDING_DIM=1024`、`SIMILARITY=cosine`、`INDEX_MATERIAL_CHUNK`）；writing-model pom 已声明依赖 writing-common。
- 外部依赖：`com.microsoft.onnxruntime:onnxruntime:1.19.2`（已在 writing-model pom 声明）；**新增 `ai.djl.huggingface:tokenizers`**（用户已确认，贴合课件 `BertTokenizer.fromPretrained` 语义，做 BGE tokenizer）；LLM 走 DashScope HTTP（pom 已有 spring-boot-starter-webflux / WebClient，**不新增** Spring AI Alibaba connector）。
- 假设：真实 BGE 模型文件路径由环境变量 `BGE_MODEL_PATH` 提供（**用户提供路径，EmbeddingClientTest 本地真实加载 + 真实推理**，断言 dim=1024 / 稳定 / 归一化）；真实 DashScope key 由 `DASHSCOPE_API_KEY` 提供（LLM 单测用 mock 下游，人工项走真实调用）。
- 配置已就绪：`writing-start/application.yaml` 已含 `model.llm.*` / `model.embedding.*`（api-key / model-path 均为 `${ENV_VAR}`、dim=1024、load-on-start=true）。
