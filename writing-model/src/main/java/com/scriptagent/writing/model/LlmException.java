/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.model;

/** LLM 对话调用异常：携带调用上下文（模型名 / 状态码），不吞错. */
public class LlmException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public LlmException(String message) {
    super(message);
  }

  public LlmException(String message, Throwable cause) {
    super(message, cause);
  }
}
