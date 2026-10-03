/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static com.scriptagent.writing.common.constants.EsIndexConstants.EMBEDDING_DIM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Embedding 真实模型集成测试：加载本地 BGE bge-m3 做真实 ONNX 推理，验证 1024 维 / 归一化 / 稳定.
 *
 * <p>仅当 {@code BGE_MODEL_PATH} 环境变量已设置（指向含 {@code onnx/model.onnx} 的 bge-m3 目录）时运行， CI
 * 未设置则跳过（不落机器路径进仓库）. 这是"做完怎么验"人工项"对一个文本向量化确认维度 1024 可入 ES"的自动化证据.
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "BGE_MODEL_PATH", matches = ".+")
class EmbeddingClientRealInferenceTest {

  @Test
  @DisplayName("真实 bge-m3 推理：1024 维 / 归一化 / 同文本稳定")
  void realInference_dim1024_normalized_stable() {
    ModelProperties props = new ModelProperties();
    props.getEmbedding().setModelPath(System.getenv("BGE_MODEL_PATH"));
    EmbeddingClient client = new EmbeddingClient(props);
    client.load();

    String text = "ScriptAgent 智稿引擎的素材向量化";
    float[] first = client.embed(text);
    float[] second = client.embed(text);

    assertThat(first).hasSize(EMBEDDING_DIM);
    assertThat(first).containsExactly(second);
    double norm = 0.0;
    for (float v : first) {
      norm += (double) v * v;
    }
    assertThat(Math.sqrt(norm)).isBetween(0.999, 1.001);
  }
}
