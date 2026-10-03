/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.model;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.scriptagent.writing.common.constants.EsIndexConstants.EMBEDDING_DIM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link EmbeddingClient} 契约单测：注入 fake session / tokenizer，不触发真实 2.3GB 模型加载，离线 {@code mvn test} 恒绿.
 *
 * <p>锁定课件 harness 关键回归：输出维度恒 = {@link EsIndexConstants#EMBEDDING_DIM}(1024)（与 ES 索引一致）、
 * 归一化（模长≈1）、同文本输出稳定。真实模型推理由 {@link EmbeddingClientRealInference}（integration， {@code
 * BGE_MODEL_PATH} 注入）验证，不把机器路径写进仓库.
 */
class EmbeddingClientTest {

  @Test
  @DisplayName("embed_alwaysReturnsDim1024_matchesEsIndex")
  void embed_alwaysReturnsDim1024_matchesEsIndex() throws Exception {
    assertThat(newClient().embed("测试文本")).hasSize(EMBEDDING_DIM);
  }

  @Test
  @DisplayName("embed 输出归一化：模长≈1")
  void embed_vectorNormalized_normApproxOne() throws Exception {
    assertThat(norm(newClient().embed("测试文本"))).isBetween(0.999, 1.001);
  }

  @Test
  @DisplayName("embed 同文本稳定：两次调用一致")
  void embed_sameText_outputStable() throws Exception {
    EmbeddingClient client = newClient();
    assertThat(client.embed("测试文本")).containsExactly(client.embed("测试文本"));
  }

  @Test
  void embed_rejectsNullAndBlank() {
    EmbeddingClient client = new EmbeddingClient(new ModelProperties());
    assertThatThrownBy(() -> client.embed(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("不能为空");
    assertThatThrownBy(() -> client.embed("  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("不能为空");
  }

  @Test
  void embed_throwsWhenModelNotLoaded() {
    EmbeddingClient client = new EmbeddingClient(new ModelProperties());
    assertThatThrownBy(() -> client.embed("x"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("未加载");
  }

  private static double norm(float[] vec) {
    double sumSq = 0;
    for (float v : vec) {
      sumSq += (double) v * v;
    }
    return Math.sqrt(sumSq);
  }

  /** 构造注入 fake session/tokenizer 的 EmbeddingClient：返回确定性的 1024 维单位向量（首分量=1，模长=1）. */
  private EmbeddingClient newClient() throws Exception {
    OrtEnvironment env = OrtEnvironment.getEnvironment();
    float[] unit = new float[EMBEDDING_DIM];
    unit[0] = 1.0f;
    OnnxTensor outputTensor = OnnxTensor.createTensor(env, unit);

    OrtSession session = mock(OrtSession.class);
    when(session.getInputInfo())
        .thenReturn(
            Map.of(
                "input_ids", mock(NodeInfo.class),
                "attention_mask", mock(NodeInfo.class)));
    when(session.getOutputNames()).thenReturn(Set.of("sentence_embedding"));
    OrtSession.Result result = mock(OrtSession.Result.class);
    when(result.get("sentence_embedding")).thenReturn(Optional.of(outputTensor));
    when(session.run(anyMap())).thenReturn(result);

    HuggingFaceTokenizer tokenizer = mock(HuggingFaceTokenizer.class);
    Encoding encoding = mock(Encoding.class);
    when(encoding.getIds()).thenReturn(new long[] {0, 1, 2, 2});
    when(encoding.getAttentionMask()).thenReturn(new long[] {1, 1, 1, 1});
    when(tokenizer.encode(anyString())).thenReturn(encoding);

    EmbeddingClient client = new EmbeddingClient(new ModelProperties());
    client.setEnvironment(env);
    client.setSession(session);
    client.setTokenizer(tokenizer);
    client.setOutputName("sentence_embedding");
    return client;
  }
}
