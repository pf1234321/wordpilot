/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 模型服务集中配置，绑定 application.yaml 的 {@code model.*}.
 *
 * <p>LLM（{@code model.llm.*}）与 Embedding（{@code model.embedding.*}）两段独立配置；API key 与模型路径 一律经 {@code
 * ${ENV_VAR}} 占位注入，代码与 yml 不落明文（H4 全局不变量③）. 由 {@code ModelConfig} 的
 * {@code @EnableConfigurationProperties} 注册并绑定.
 */
@ConfigurationProperties(prefix = "model")
public class ModelProperties {

  private Llm llm = new Llm();
  private Embedding embedding = new Embedding();

  public Llm getLlm() {
    return llm;
  }

  public void setLlm(Llm llm) {
    this.llm = llm;
  }

  public Embedding getEmbedding() {
    return embedding;
  }

  public void setEmbedding(Embedding embedding) {
    this.embedding = embedding;
  }

  /** LLM 对话调用配置（阿里 Qwen / DashScope）. */
  public static class Llm {
    private String provider = "dashscope";
    private String apiKey; // ${DASHSCOPE_API_KEY}
    private String baseUrl = "https://dashscope.aliyuncs.com";
    private String model = "qwen-max";
    private int timeoutSeconds = 60;

    public String getProvider() {
      return provider;
    }

    public void setProvider(String provider) {
      this.provider = provider;
    }

    public String getApiKey() {
      return apiKey;
    }

    public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
    }

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public String getModel() {
      return model;
    }

    public void setModel(String model) {
      this.model = model;
    }

    public int getTimeoutSeconds() {
      return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
      this.timeoutSeconds = timeoutSeconds;
    }
  }

  /** Embedding 向量化配置（本地 BGE bge-m3 + ONNX Runtime）. */
  public static class Embedding {
    private String provider = "bge";
    private String modelPath; // ${BGE_MODEL_PATH}
    private String model = "bge-m3";
    private int dim = 1024;
    private boolean loadOnStart = true;

    public String getProvider() {
      return provider;
    }

    public void setProvider(String provider) {
      this.provider = provider;
    }

    public String getModelPath() {
      return modelPath;
    }

    public void setModelPath(String modelPath) {
      this.modelPath = modelPath;
    }

    public String getModel() {
      return model;
    }

    public void setModel(String model) {
      this.model = model;
    }

    public int getDim() {
      return dim;
    }

    public void setDim(int dim) {
      this.dim = dim;
    }

    public boolean isLoadOnStart() {
      return loadOnStart;
    }

    public void setLoadOnStart(boolean loadOnStart) {
      this.loadOnStart = loadOnStart;
    }
  }
}
