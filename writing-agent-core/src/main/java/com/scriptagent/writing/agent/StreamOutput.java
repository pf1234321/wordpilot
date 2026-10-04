/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.agent;

/**
 * 流式输出回调抽象：writing-agent-core 不依赖 spring-web，以回调承载生成结果的逐段推送（plan 决策②）.
 *
 * <p>SSE 端点与前端渲染由第 8 节 API 层（{@code SseStreamingService}）桥接本抽象，把回调转成 SSE 帧.
 */
public interface StreamOutput {

  /** 推送一段文本（逐段流式）. */
  void onToken(String token);

  /** 生成完成. */
  void onComplete();

  /** 生成失败. */
  void onError(Throwable throwable);
}
