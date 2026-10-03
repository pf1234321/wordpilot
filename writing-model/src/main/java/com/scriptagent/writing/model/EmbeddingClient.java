/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.model;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static com.scriptagent.writing.common.constants.EsIndexConstants.EMBEDDING_DIM;

/**
 * Embedding 向量化：本地 BGE bge-m3 + JVM 进程内 ONNX Runtime 推理（数据不出内网）.
 *
 * <p>模型目录需含官方 ONNX 导出（{@code onnx/model.onnx} + {@code onnx/model.onnx_data}）与 {@code
 * tokenizer.json}. {@link #load()} 启动加载一次（单例），输出恒 {@code dim}=1024 维归一化向量 （宪法 §9.3：与 ES {@code
 * writing_material_chunk.embedding} 的 dense_vector.dim 一致，cosine）. 模型路径经 {@code ${BGE_MODEL_PATH}}
 * 注入，不落明文（H4 不变量③）.
 */
@Component
public class EmbeddingClient {

  private static final Logger log = LoggerFactory.getLogger(EmbeddingClient.class);

  /** ONNX 模型相对路径（模型目录下）. */
  private static final String ONNX_MODEL_RELATIVE = "onnx/model.onnx";

  private static final String TOKENIZER_JSON_RELATIVE = "tokenizer.json";

  /** L2 归一化模长下限，低于视为零向量，拒绝归一化（避免除以 0）. */
  private static final double NORM_EPSILON = 1e-8;

  private final ModelProperties props;

  private OrtEnvironment environment;
  private OrtSession session;
  private HuggingFaceTokenizer tokenizer;
  private String outputName;

  public EmbeddingClient(ModelProperties props) {
    this.props = props;
  }

  /** 启动加载一次，单例复用；失败即报错，不静默降级. */
  @PostConstruct
  public void load() {
    String modelPath = props.getEmbedding().getModelPath();
    if (modelPath == null || modelPath.isBlank()) {
      throw new IllegalStateException("BGE_MODEL_PATH 未配置，无法加载 Embedding 模型");
    }
    String onnxPath = modelPath + "/" + ONNX_MODEL_RELATIVE;
    String tokenizerPath = modelPath + "/" + TOKENIZER_JSON_RELATIVE;
    try {
      this.environment = OrtEnvironment.getEnvironment();
      this.session = environment.createSession(onnxPath, new OrtSession.SessionOptions());
      this.tokenizer =
          HuggingFaceTokenizer.builder()
              .optTokenizerPath(Paths.get(tokenizerPath))
              .optTruncation(false)
              .optPadding(false)
              .build();
      this.outputName = pickOutputName(session.getOutputNames());
      if (log.isInfoEnabled()) {
        log.info(
            "Embedding 模型加载成功, model={}, dim={}, onnx={}, output={}",
            sanitizeLog(props.getEmbedding().getModel()),
            props.getEmbedding().getDim(),
            sanitizeLog(onnxPath),
            sanitizeLog(outputName));
      }
    } catch (Exception ex) {
      throw new IllegalStateException("Embedding 模型加载失败, modelPath=" + modelPath, ex);
    }
  }

  /**
   * 文本向量化：tokenize → ONNX 推理 → 归一化 1024 维向量.
   *
   * @param text 非空文本
   * @return 归一化 float[1024] 向量，可直接 cosine 检索
   */
  public float[] embed(String text) {
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException("embed 输入不能为空");
    }
    ensureLoaded();
    String inputIdName = "input_ids";
    String attentionMaskName = "attention_mask";
    try {
      Encoding encoding = tokenizer.encode(text);
      long[] inputIds = encoding.getIds();
      long[] attentionMask = encoding.getAttentionMask();

      Map<String, ?> inputInfo = session.getInputInfo();
      if (!inputInfo.containsKey(inputIdName) || !inputInfo.containsKey(attentionMaskName)) {
        throw new IllegalStateException(
            "Embedding 模型输入节点不符合预期, 需要 "
                + inputIdName
                + "/"
                + attentionMaskName
                + ", 实际: "
                + inputInfo.keySet());
      }

      try (OnnxTensor idsTensor =
              OnnxTensor.createTensor(
                  environment, LongBuffer.wrap(inputIds), new long[] {1, inputIds.length});
          OnnxTensor maskTensor =
              OnnxTensor.createTensor(
                  environment,
                  LongBuffer.wrap(attentionMask),
                  new long[] {1, attentionMask.length});
          OrtSession.Result result =
              session.run(Map.of(inputIdName, idsTensor, attentionMaskName, maskTensor))) {
        OnnxValue output =
            result
                .get(outputName)
                .orElseThrow(() -> new IllegalStateException("Embedding 输出节点不存在: " + outputName));
        return normalize(readVector(output));
      }
    } catch (OrtException ex) {
      throw new IllegalStateException("Embedding 推理失败", ex);
    }
  }

  private void ensureLoaded() {
    if (session == null || tokenizer == null) {
      throw new IllegalStateException("Embedding 模型未加载（load-on-start 未执行或失败）");
    }
  }

  /** 从模型输出名中选句子向量输出：优先 sentence_embedding / dense_vecs / last_hidden_state，否则取首个. */
  private String pickOutputName(Set<String> names) {
    if (names == null || names.isEmpty()) {
      throw new IllegalStateException("Embedding 模型无输出节点");
    }
    for (String name : names) {
      if ("sentence_embedding".equals(name)
          || "dense_vecs".equals(name)
          || "last_hidden_state".equals(name)) {
        return name;
      }
    }
    return names.iterator().next();
  }

  /** 读取 1×{@code dim} 输出的扁平向量（取第一行）. */
  private float[] readVector(OnnxValue output) {
    if (!(output instanceof OnnxTensor)) {
      throw new IllegalStateException("Embedding 输出不是张量, type=" + output.getType());
    }
    OnnxTensor tensor = (OnnxTensor) output;
    long[] shape = tensor.getInfo().getShape();
    if (shape == null || shape.length == 0 || shape[shape.length - 1] != EMBEDDING_DIM) {
      throw new IllegalStateException(
          "Embedding 输出维度异常, 期望 "
              + EMBEDDING_DIM
              + ", 实际 shape="
              + (shape == null ? "null" : java.util.Arrays.toString(shape)));
    }
    float[] flat = new float[EMBEDDING_DIM];
    FloatBuffer buffer = tensor.getFloatBuffer();
    buffer.get(flat);
    return flat;
  }

  /** L2 归一化：保证模长≈1，可直接 cosine. */
  private float[] normalize(float... vec) {
    double norm = 0.0;
    for (float v : vec) {
      norm += (double) v * v;
    }
    norm = Math.sqrt(norm);
    if (norm < NORM_EPSILON) {
      throw new IllegalStateException("Embedding 向量模长为 0，归一化失败");
    }
    float[] normalized = new float[vec.length];
    for (int i = 0; i < vec.length; i++) {
      normalized[i] = (float) (vec[i] / norm);
    }
    return normalized;
  }

  /** 日志安全：去除可能注入 CR/LF 的字符，避免日志伪造（FindSecBugs CRLF_INJECTION_LOGS）. */
  private static String sanitizeLog(String value) {
    return value == null ? "null" : value.replace("\r", "\\r").replace("\n", "\\n");
  }

  @PreDestroy
  public void close() {
    if (session != null) {
      try {
        session.close();
      } catch (OrtException ex) {
        log.warn("关闭 Embedding 模型会话失败", ex);
      }
    }
  }

  // ---- 契约单测注入缝（package-private）：注入 fake session/tokenizer，不触发真实 2.3GB 模型加载 ----

  void setEnvironment(OrtEnvironment environment) {
    this.environment = environment;
  }

  void setSession(OrtSession session) {
    this.session = session;
  }

  void setTokenizer(HuggingFaceTokenizer tokenizer) {
    this.tokenizer = tokenizer;
  }

  void setOutputName(String outputName) {
    this.outputName = outputName;
  }
}
