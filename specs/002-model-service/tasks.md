# Tasks: 模型服务（writing-model）

**Input**: Design documents from `/specs/002-model-service/`
**Prerequisites**: plan.md (required), spec.md (required)
**Tests**: 课件"验收 harness"四个测试类（EmbeddingClientTest / LlmClientTest / ModelServiceTest / IndexDimensionTest）——关键回归测试原样落地，先于对应实现（harness 先行）。另加 `EmbeddingClientRealInference`（`@Tag("integration")`，CI 跳过）跑真实模型。

**Organization**: 按 user story 分组，每条可独立实现与测试。

## 路径约定
- 实现：`writing-model/src/main/java/com/scriptagent/writing/model/*`
- 测试：`writing-model/src/test/java/com/scriptagent/writing/model/*`

## Phase 1: Foundational（阻塞前置）

- [x] T001 在 `writing-model/pom.xml` 引入 `ai.djl.huggingface:tokenizers:0.38.0` 依赖（BGE tokenizer，用户已确认）；保留已有 onnxruntime 1.19.2 / webflux
- [x] T002 创建 `ModelProperties.java`（`@ConfigurationProperties(prefix="model")`，绑定 `model.llm.*` / `model.embedding.*`；api-key / model-path 经 `${ENV_VAR}`，缺则客户端启动校验报错）

## Phase 2: User Story 1 — LLM 对话出口（P1）

**Goal**: `LlmClient` 封装 DashScope 通义千问（RestClient）；`ModelService.chat()` 门面委派。

### Tests（harness 先行）
- [x] T010 [US1] `LlmClientTest.java` —— chat 走通（`MockRestServiceServer` mock DashScope 返回桩文本）；下游错误抛清晰异常（含模型名）；api-key 不含 `sk-`（环境变量注入）
- [x] T011 [US1] `ModelServiceTest.java` —— 门面正确委派给 LlmClient / EmbeddingClient；`apiKey_neverReadFromPlaintextConfig` 断言

### Implementation
- [x] T012 [US1] `LlmClient.java` —— RestClient 调 DashScope **OpenAI 兼容端点** `/compatible-mode/v1/chat/completions`（2026-10-03 真实 API 实测：原生端点 `/api/v1/services/aigc/text-generation/generation` 返回 `output.text` 无 choices，与解析结构不匹配，故改用兼容端点），`chat(messages, options)`，异常不吞、含上下文
- [x] T013 [US1] `ModelService.java` —— 门面 `chat(messages)` 委派 LlmClient（依赖 T012）

## Phase 3: User Story 2 — Embedding 出口（P1）

**Goal**: `EmbeddingClient` 本地 BGE bge-m3 + ONNX Runtime + DJL tokenizer，启动加载一次单例，输出恒 1024 维归一化向量。

### Tests（harness 先行）
- [x] T020 [US2] `EmbeddingClientTest.java` —— **契约单测（恒绿）**：注入 fake `OrtSession` + `HuggingFaceTokenizer`（Mockito），验证 `embed_alwaysReturnsDim1024_matchesEsIndex`（vec.length==1024 对常量）；同文本两次调用稳定；`norm(vec)≈1`（0.999~1.001）；模型路径未配置时抛清晰异常；空串/null 抛 IllegalArgumentException
- [x] T021 [US2] `IndexDimensionTest.java` —— ES `writing_material_chunk.embedding` dim 恒 = `EsIndexConstants.EMBEDDING_DIM`(1024)、`SIMILARITY`(cosine)，引用常量禁止硬编码
- [x] T022 [US2] `EmbeddingClientRealInference.java`（`@Tag("integration")`，CI 跳过）—— `BGE_MODEL_PATH=/Users/Administrator/bge-m3` 加载真实模型，实测真实推理的 1024 维 / 归一化 / 稳定（本地跑通，不把机器路径写进仓库）

### Implementation
- [x] T023 [US2] `EmbeddingClient.java` —— `@PostConstruct` load-on-start 加载 `onnx/model.onnx`（单例字段）+ DJL `HuggingFaceTokenizer`（读 `onnx/tokenizer.json`）；`embed(text)` tokenize → 动态探测输入名构建 input_ids/attention_mask(/token_type_ids) → session.run → 提取并校验 1024 维输出 → 返回归一化 float[]；空串/null 抛 IllegalArgumentException

## Phase 4: Polish & Cross-Cutting

- [x] T030 `mvn clean verify` 全绿（含 P3C/SpotBugs/FindSecBugs/PMD），输出关键结果
- [x] T031 验收报告：课件 harness 映射表逐类对号、交付物 ls 核对、H4 不变量③④自查、剩余人工项清单

## 依赖与执行顺序
- 测试任务（T010/T011/T020/T021）先于或伴随对应实现（harness 先行）
- T002（ModelProperties）→ T012（LlmClient）→ T013（ModelService）；T023（EmbeddingClient）依赖 T002
- 每任务落地即跑该模块测试，红了当场修，不攒到最后
