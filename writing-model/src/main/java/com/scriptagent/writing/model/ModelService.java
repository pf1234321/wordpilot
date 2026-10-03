/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.model;

import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 模型服务统一门面：暴露 {@code chat()} 与 {@code embed()}，是 AgentCore / 写作编排 / RAG 管线访问 模型的唯一出口（宪法原则三 / 决策九）.
 *
 * <p>上层只依赖本门面，不直接拿 {@link LlmClient} / {@link EmbeddingClient}，换模型只改模型服务层.
 */
@Service
public class ModelService {

  private final LlmClient llm;
  private final EmbeddingClient embedding;

  public ModelService(LlmClient llm, EmbeddingClient embedding) {
    this.llm = llm;
    this.embedding = embedding;
  }

  /** 多轮对话生成（默认模型与参数）. */
  public String chat(List<Message> messages) {
    return llm.chat(messages, null);
  }

  /** 多轮对话生成（带可选参数）. */
  public String chat(List<Message> messages, ChatOptions options) {
    return llm.chat(messages, options);
  }

  /** 文本向量化：恒 1024 维归一化向量，可直接入 ES cosine 检索. */
  public float[] embed(String text) {
    return embedding.embed(text);
  }
}
