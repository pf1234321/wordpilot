/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.model;

/**
 * LLM 对话调用的可选参数.
 *
 * <p>{@code model} 为空时回退到 {@link ModelProperties.Llm#getModel()} 的默认模型；其余字段可空.
 */
public record ChatOptions(String model, Double temperature, Double topP, Integer maxTokens) {

  /** 仅指定模型名，其余取默认. */
  public static ChatOptions ofModel(String model) {
    return new ChatOptions(model, null, null, null);
  }
}
