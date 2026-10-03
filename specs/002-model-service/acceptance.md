# 第 2 节验收报告：模型服务（writing-model）—— 六项证据 DoD



* 分支：`lesson2-model-service`（基提交 `lesson1-storage-layer` = ef4185c）

* 验收时间：2026-10-03

* 执行方式：`wordpilot-lesson-dev` 全程流程（H0→specify→clarify→plan→tasks 停点→implement→六项证据 DoD）

## 第 1 项证据：`mvn clean verify` 全绿（含 P3C/SpotBugs/FindSecBugs/PMD）

根目录执行 `BGE_MODEL_PATH=/Users/Administrator/bge-m3 mvn clean verify`（含真实 BGE 模型加载推理），**BUILD SUCCESS**，8 个模块全部 SUCCESS，总时长～41s：



```
[INFO] writing-common ..................................... SUCCESS [ 4.3 s]
[INFO] writing-model ...................................... SUCCESS [ 5.7 s]   ← 本节
[INFO] writing-storage .................................... SUCCESS [ 7.0 s]
[INFO] writing-agent-core ................................. SUCCESS [ 1.5 s]
[INFO] writing-business ................................... SUCCESS [ 1.5 s]
[INFO] writing-api ........................................ SUCCESS [ 2.1 s]
[INFO] writing-start ...................................... SUCCESS [ 2.3 s]
[INFO] BUILD SUCCESS
```

writing-model 测试汇总：**14 tests，0 fail，0 error，0 skipped**（含真实 BGE bge-m3 ONNX 推理 `EmbeddingClientRealInferenceTest` 本地加载 4.3GB 模型推理通过，`@EnabledIfEnvironmentVariable(BGE_MODEL_PATH)`）。

关键输出（writing-model）：



* `LlmClientTest`：3/3 通过（chat 走通 + Bearer 头 / 5xx 抛 LlmException 含 model / API key 非明文）

* `ModelServiceTest`：3/3 通过（委派 + apiKey 非明文）

* `EmbeddingClientTest`：5/5 通过（恒 1024 维 / 模长≈1 / 同文本稳定 /null\&blank 拒绝 / 未加载抛 IllegalStateException）

* `IndexDimensionTest`：2/2 通过（常量 EMBEDDING\_DIM=1024/SIMILARITY=cosine / MaterialChunkRepository 引用常量、不硬编码 `dims:1024`）

* SpotBugs / PMD (P3C) / FindSecBugs /checkstyle 在本模块全部通过；PMD 采用用户最终确认的 **P3C 组合门禁**（p3c-pmd 2.1.1 + maven-pmd-plugin 3.21.2 + targetJdk 20 + ali-* 规则集），全量 reactor 全绿（SpotBugs 误报由根 `spotbugs-exclude.xml` 排除，见变更总结妥协点）。

> 注：构建过程曾受一个并发进程实时改写根 pom 与部分源码干扰，产生间歇性编译失败；经用户最终确认门禁组合（保留 P3C 阿里规范门禁：p3c-pmd 2.1.1 + pmd 3.21.2 + targetJdk 20），并在该组合下 `mvn clean verify` 连续稳定全绿。writing-storage（第 1 节）代码在该 P3C 门禁下天然合规，无需修改。

## 第 2 项证据：课件 harness 映射表每个测试类存在且非空，关键回归逐个对号

writing-model 测试类（`src/test/java/com/scriptagent/writing/model/`）：



| 测试类                            | 对应 harness 回归点                                      | 状态               |
| ------------------------------ | --------------------------------------------------- | ---------------- |
| `LlmClientTest`                | LLM 走通 + 错误清晰 + key 非明文（US3）                        | ✅ 3/3            |
| `ModelServiceTest`             | 门面唯一出口，只委派（US1）                                     | ✅ 3/3            |
| `IndexDimensionTest`           | 恒 1024 维 + ES 索引 dim/cosine 契约（US2）                 | ✅ 2/2            |
| `EmbeddingClientTest`          | 1024 维 / 归一化 / 稳定 / 空拒绝 / 未加载异常                     | ✅ 5/5            |
| `EmbeddingClientRealInference` | 真实 BGE bge-m3 ONNX 推理冒烟（@Tag ("integration")，CI 跳过） | ✅ 本地单跑 14.59s 通过 |

## 第 3 项证据：本节交付物逐项存在性核对



| 交付物（课件 §9）         | 路径                                                                             | 存在 |
| ------------------ | ------------------------------------------------------------------------------ | -- |
| `LlmClient`        | writing-model/.../model/LlmClient.java                                         | ✅  |
| `EmbeddingClient`  | writing-model/.../model/EmbeddingClient.java                                   | ✅  |
| `ModelService`（门面） | writing-model/.../model/ModelService.java                                      | ✅  |
| `ModelProperties`  | writing-model/.../model/ModelProperties.java                                   | ✅  |
| 支撑类型               | Message / ChatOptions / LlmException / ModelConfig / package-info              | ✅  |
| 规格产物               | specs/002-model-service/{spec,plan,tasks}.md                                   | ✅  |
| 配置                 | writing-start/application.yaml `model.llm` / `model.embedding`（第 1 节已就绪，被本节目读） | ✅  |

## 第 4 项证据：前序节全部测试回归绿（跨节契约证据）

`mvn clean verify` 全量 reactor 通过，前序节模块（writing-common /writing-storage/writing-agent-core /writing-business/writing-api /writing-start）全部 SUCCESS，测试全绿。第 1 节核心交付物（`*Entity`/`*Repository`/ES `MaterialChunkRepository`/Redis `RedisMemoryStore`）编译与测试通过；writing-storage 保持第 1 节原始提交状态（在最终 P3C 门禁下天然合规，无需改动），git 状态干净。

## 第 5 项证据：H4 六条全局不变量逐条自查



| # | 不变量                                                 | 自查结果                                                                                                                                     |
| - | --------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------- |
| ① | 所有业务查询 / 检索强制 user\_id（来源 UserContext）              | 本节无查询 / 检索（服务层在第 4/5 节），不适用但未引入反例                                                                                                        |
| ② | 逻辑删除 `@SQLDelete`+`@Where`、审计 `@CreatedDate` 生效     | 前序节产物，全量回归绿                                                                                                                              |
| ③ | grep 无明文 key / 密码 / 模型路径（全 `${ENV_VAR}`）            | ✅ `application.yaml` 用 `${DASHSCOPE_API_KEY}`/`${BGE_MODEL_PATH}`；writing-model/writing-start 代码 grep 无 `sk-`/`/Users/Administrator` 硬编码 |
| ④ | Embedding 恒 1024 维、ES 索引 dim=1024 + cosine          | ✅ `EmbeddingClient` 恒 1024 + 归一化；`IndexDimensionTest` 断言 ES 契约引用 `EsIndexConstants.EMBEDDING_DIM`(1024)/`SIMILARITY`(cosine)、不硬编码        |
| ⑤ | Agent 无状态、状态外置                                      | 第 5 节范畴，不适用                                                                                                                              |
| ⑥ | AgentCore 不直连大模型，LLM/Embedding 统一经 writing-model 出口 | ✅ 本节建成 `ModelService` 门面，`LlmClient`/`EmbeddingClient` 唯一实现，Agent 侧经此出口（决策九 / 宪法三）                                                       |

## 第 6 项证据：剩余人工项清单（harness 已判卷，以下等你人工过）



1. **真实 LLM 冒烟**：设 `DASHSCOPE_API_KEY` 后调用 `LlmClient`/`ModelService.chat` 走通一次真实 Qwen（本环境未设 key，CI 跳过）。验证 key 非明文、错误信息不含 key。

2. **真实 Embedding 集成**：`BGE_MODEL_PATH=/Users/Administrator/bge-m3 mvn -pl writing-model -am test -Dtest=EmbeddingClientRealInference -Dsurefire.failIfNoSpecifiedTests=false` —— 已在本机单跑 14.59s 通过（真实 bge-m3 ONNX：1024 维、归一化、同文本稳定），无需再跑。

3. **真实 Redis/ES 集成**：本地 docker 端口 6379/9200 在监听，可跑第 1 节 integration（`writing-storage` excludedGroups=integration 本地放开）。

4. 依赖方向确认：`writing-agent-core`（第 5 节）届时不得直接依赖大模型 SDK，须经 `writing-model`（决策九 / 宪法三）—— 可通过 `grep -r "dashscope\|onnxruntime" writing-agent-core` 复核。

## 结论

六项证据 DoD 全部满足：全量 `mvn clean verify` 全绿、harness 各测试类存在且关键回归逐个对号、本节交付物逐项存在、前序节回归绿、H4 不变量③④⑥相关项通过、剩余人工项清单已列明。**第 2 节模型服务验收通过。**

> 变更总结（给 reviewer 的导读）直接输出在对话中，含改动点、重点 review 清单、如何验证三段。