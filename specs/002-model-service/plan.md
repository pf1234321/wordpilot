# Implementation Plan: 模型服务（writing-model）

**Branch**: `lesson2-model-service` | **Date**: 2026-10-03 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/002-model-service/spec.md`

## Summary

在 `writing-model` 模块把"调大模型"统一收口（决策九）：`LlmClient`（阿里 Qwen / DashScope，RestClient 封装 HTTP）与 `EmbeddingClient`（本地 BGE bge-m3 + ONNX Runtime + DJL tokenizer，启动加载一次单例，输出恒 1024 维归一化向量），统一由 `ModelService` 门面暴露 `chat()` / `embed()`；配置集中 `ModelProperties`（`model.llm.*` / `model.embedding.*`，全 `${ENV_VAR}`）。AgentCore 与业务层只依赖门面，不直连任何模型（宪法原则三）。

## Technical Context

**Language/Version**: Java 21 (Spring Boot 3.3.5)

**Primary Dependencies**:
- `com.scriptagent:writing-common` — `EsIndexConstants`（`EMBEDDING_DIM=1024` / `SIMILARITY=cosine` / `INDEX_MATERIAL_CHUNK`）、`UserContext`
- `com.microsoft.onnxruntime:onnxruntime:1.19.2` — 本地 BGE bge-m3 ONNX 推理（已在 pom 声明）
- `ai.djl.huggingface:tokenizers:0.38.0` — 读 `tokenizer.json` 做 SentencePiece 分词（**Softgate 已确认新增**；Maven Central 当前 0.38.0）
- `spring-boot-starter-webflux` — 提供 `spring-web` 的 `RestClient`（**课件明确写 RestClient 封装 DashScope HTTP**；MockRestServiceServer 可 mock，无新增测试依赖）
- `spring-boot-starter-test`（JUnit 5 + Mockito + MockRestServiceServer）— 测试

**Model 选型（已核实落地）**:
- LLM：阿里云百炼通义千问 Qwen，DashScope REST `https://dashscope.aliyuncs.com`，model=`qwen-max`
- Embedding：本地 BGE **bge-m3**（路径经 `${BGE_MODEL_PATH}` 注入，当前 `/Users/Administrator/bge-m3`）：`onnx/model.onnx` + `onnx/model.onnx_data`（2.26GB）；**SentencePiece 分词**（XLMRobertaTokenizer，读 `onnx/tokenizer.json`，词表 250002，hidden=1024，max_seq=8192，特殊 token `<s>=0 <pad>=1 </s>=2 <unk>=3`）；模型内置 Transformer+Pooling+Normalize，输出即归一化 1024 维向量
- bge-reranker-v2-m3：重排器、无 ONNX 导出，**本节不用**（预留扩展阶段 ReRank）

**Storage / 契约**: 无新增落库；仅引用 `EsIndexConstants` 做维度契约校验（ES `writing_material_chunk.embedding` dim=1024 + cosine，第 1 节已建）

**Testing**:
- `EmbeddingClientTest`（单测，**恒绿**）：注入 fake `OrtSession` + `HuggingFaceTokenizer`（Mockito），验证 `embed` 输出维 = `EsIndexConstants.EMBEDDING_DIM(1024)`、归一化（模长≈1）、同文本稳定；不触发真实 2.3GB 模型加载
- `LlmClientTest`（单测，恒绿）：`MockRestServiceServer` mock DashScope，验证 chat 走通 + 下游错误抛清晰异常 + api-key 非明文
- `ModelServiceTest`（单测，恒绿）：门面正确委派 LlmClient / EmbeddingClient；`apiKey_neverReadFromPlaintextConfig`
- `IndexDimensionTest`（单测，恒绿）：ES 索引 dim 恒 = 常量 1024、cosine（引用常量禁止硬编码）
- `EmbeddingClientRealInference`（`@Tag("integration")`，CI 跳过）：`BGE_MODEL_PATH=/Users/Administrator/bge-m3` 加载真实模型，实测真实推理的 1024 维 / 归一化 / 稳定；**本地跑通作为真实模型证据**（不把机器路径写进仓库）
- `mvn clean verify` 全绿（含 P3C/SpotBugs/FindSecBugs/PMD）

**Target Platform**: Linux server（内网部署）；Embedding CPU 推理（写作量级够用），单例启动加载

**Performance Goals**:
- Embedding 单文本推理（CPU，bge-m3 24 层 / 2.3GB）：单次 < 2s（预估；写作量级可接受）
- LLM chat：受 DashScope 网络与 qwen-max 限制，超时走 `model.llm.timeout-seconds(60)`

**Constraints**:
- 单向依赖：`writing-agent-core → writing-model`；writing-model 只依赖 writing-common，不依赖 storage/business
- 模型服务是 LLM/Embedding 唯一出口；AgentCore / 业务 / RAG 只依赖 `ModelService` 门面，grep 确认无直连 LlmClient / EmbeddingClient 路径
- `api-key` / `model-path` 一律 `${ENV_VAR}`（`DASHSCOPE_API_KEY` / `BGE_MODEL_PATH`），代码与 yml 明文 0 处（H4③）；启动期缺则报错（客户端 `@PostConstruct` 显式校验）
- Embedding 输出恒 1024 维，ES 索引 dim 引用常量不硬编码（H4④）
- 模型启动加载一次（单例），绝不每次调用重载

**Scale/Scope**: 内网单实例；用户 ~200；写作量级 CPU 推理够用，大批量再考虑 GPU/ONNX 加速（本节不实现）

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 状态 | 说明 |
|-------|-----|------|
| 原则一：SpringBoot3.x + Java21 单体应用 | ✅ | writing-model 在 7 模块中，单向依赖 writing-common |
| 原则二：Agent 无状态，状态外置 | N/A | 本节交付模型出口，Agent 编排属第 5 节 |
| 原则三：AgentScope 使用边界；LLM/Embedding 统一经模型服务 | ✅ | **本节即决策九落点**：`ModelService` 是唯一出口，AgentCore 不直连模型 |
| 原则四：极简登录，不用 SpringSecurity | N/A | 本节不涉登录 |
| 原则五：查询强制携带 user_id | N/A | 本节无业务查询（纯模型调用） |
| 原则六：JPA 落地规范 | N/A | 本节无 JPA 实体 |
| 原则七：RAG 管线自实现 | ✅ | Embedding 由本节提供，供第 4 节 RAG 经模型服务调用 |
| 原则八：记忆三层分层 | N/A | 本节不涉记忆 |
| 原则九：三类写作能力共享一套 Agent 底座 | ✅ | 三类写作的 LLM 生成都经本节 `ModelService.chat` |
| 原则十：每个 user story 完成后有可演示成果 | ✅ | 4 个 harness 测试类全绿 + 真实模型集成测试可跑（本地 BGE 真实推理） |

## Project Structure

### Documentation (this feature)

```text
specs/002-model-service/
├── plan.md              # 本文件
├── spec.md              # 需求规格
├── contracts/           # ModelService / LlmClient / EmbeddingClient 契约
└── tasks.md             # /speckit-tasks 输出
```

### Source Code (repository root)

本节交付物集中在 `writing-model` 模块（已存在，仅 package-info + pom）。

```text
writing-model/
├── src/main/java/com/scriptagent/writing/model/
│   ├── ModelProperties.java      # @ConfigurationProperties(prefix="model")
│   ├── LlmClient.java            # 阿里 Qwen / DashScope 对话调用（RestClient）
│   ├── EmbeddingClient.java      # 本地 BGE bge-m3 + ONNX Runtime + DJL tokenizer
│   ├── ModelService.java         # 门面：chat() / embed()（唯一出口）
│   └── package-info.java
└── src/test/java/com/scriptagent/writing/model/
    ├── EmbeddingClientTest.java          # 契约单测（fake session/tokenizer，恒绿）
    ├── LlmClientTest.java                # 单测（MockRestServiceServer）
    ├── ModelServiceTest.java             # 单测（委派 + api-key 非明文）
    ├── IndexDimensionTest.java           # 单测（ES dim=1024 cosine 常量）
    └── EmbeddingClientRealInference.java # @Tag("integration") 真实模型
```

writing-common / writing-storage 本节不新增代码（仅引用 `EsIndexConstants` 常量）。

**pom 变更**：writing-model/pom.xml 新增 `ai.djl.huggingface:tokenizers:0.38.0`（其余依赖已声明）。

**Structure Decision**: 单体 7 模块；本节交付集中在 writing-model，不写 Controller / Service 编排 / Agent（属第 3~8 节）。

## Complexity Tracking

> 本节唯一需 justify 的点：新增 DJL tokenizers 0.38.0 依赖（onnxruntime 不含分词器；bge-m3 为 SentencePiece 分词，需库读 tokenizer.json）——已由用户 Softgate 确认。其余无 Constitution 违例。
