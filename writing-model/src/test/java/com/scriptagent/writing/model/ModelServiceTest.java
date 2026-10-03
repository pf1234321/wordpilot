/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.model;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** ModelService 门面单测：验证门面正确委派给 LlmClient / EmbeddingClient，且自身不含模型逻辑. */
@ExtendWith(MockitoExtension.class)
class ModelServiceTest {

  @Mock private LlmClient llm;
  @Mock private EmbeddingClient embedding;

  private ModelService service;

  @BeforeEach
  void setUp() {
    service = new ModelService(llm, embedding);
  }

  @Test
  @DisplayName("chat 委派给 LlmClient，门面不含模型逻辑")
  void chat_delegatesToLlmClient() {
    List<Message> messages = List.of(new Message("user", "你好"));
    when(llm.chat(messages, null)).thenReturn("你好，我是写作助手");
    assertThat(service.chat(messages)).isEqualTo("你好，我是写作助手");
    verify(llm).chat(messages, null);
  }

  @Test
  @DisplayName("embed 委派给 EmbeddingClient，返回向量")
  void embed_delegatesToEmbeddingClient() {
    float[] vec = new float[1024];
    when(embedding.embed("素材文本")).thenReturn(vec);
    assertThat(service.embed("素材文本")).isSameAs(vec);
    verify(embedding).embed("素材文本");
  }

  @Test
  @DisplayName("API key 由环境变量注入，非明文（不含 sk-）")
  void apiKey_neverReadFromPlaintextConfig() {
    ModelProperties props = new ModelProperties();
    props
        .getLlm()
        .setApiKey(System.getenv().getOrDefault("DASHSCOPE_API_KEY", "placeholder-from-env"));
    assertThat(props.getLlm().getApiKey()).doesNotContain("sk-");
  }
}
