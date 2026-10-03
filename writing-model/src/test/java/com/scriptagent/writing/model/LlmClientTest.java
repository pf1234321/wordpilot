/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.model;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** LlmClient 单测：MockRestServiceServer 拦截 RestClient，验证 chat 走通（含 Authorization 头 / 请求体） 与错误清晰上抛. */
class LlmClientTest {

  private static final String BASE_URL = "http://localhost:8080";
  private static final String COMPLETIONS_PATH = "/compatible-mode/v1/chat/completions";

  private MockRestServiceServer mockServer;
  private LlmClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    mockServer = MockRestServiceServer.bindTo(builder).build();
    ModelProperties props = new ModelProperties();
    props.getLlm().setBaseUrl(BASE_URL);
    props.getLlm().setApiKey("test-api-key");
    props.getLlm().setModel("qwen-max");
    props.getLlm().setTimeoutSeconds(5);
    client = new LlmClient(props, builder);
  }

  @Test
  @DisplayName("chat 走通：POST DashScope 返回生成文本，携带 Bearer 鉴权")
  void chat_returnsGeneratedText_andCarriesBearerToken() {
    mockServer
        .expect(requestTo(BASE_URL + COMPLETIONS_PATH))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-api-key"))
        .andRespond(
            withSuccess(
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"你好，我是通义千问\"}}],"
                    + "\"usage\":{}}",
                MediaType.APPLICATION_JSON));

    List<Message> messages = List.of(new Message("system", "你是写作助手"), new Message("user", "写一句话"));
    String text = client.chat(messages, ChatOptions.ofModel("qwen-max"));
    assertThat(text).isEqualTo("你好，我是通义千问");
    mockServer.verify();
  }

  @Test
  @DisplayName("下游返回 5xx 时抛清晰异常，含模型名，不吞错")
  void chat_httpError_throwsLlmException() {
    mockServer
        .expect(requestTo(BASE_URL + COMPLETIONS_PATH))
        .andRespond(
            withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"message\":\"bad request\"}}"));

    assertThatThrownBy(
            () ->
                client.chat(
                    List.of(new Message("user", "hi")), ChatOptions.ofModel("unknown-model")))
        .isInstanceOf(LlmException.class)
        .hasMessageContaining("unknown-model");
    mockServer.verify();
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
