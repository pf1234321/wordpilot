/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * LLM 对话调用封装：经 DashScope（OpenAI 兼容端点）调用阿里通义千问（Qwen）.
 *
 * <p>用 {@link RestClient} 同步封装 DashScope HTTP（课件"代码怎么写"点名 RestClient 封装接口），测试可用 spring-test 的
 * MockRestServiceServer 拦截 mock，无新增测试依赖. LLM 唯一出口的一部分（宪法原则三）： AgentCore / 写作编排 / RAG 不直连模型，统一经
 * {@link ModelService} 门面到此. API key 经 {@link ModelProperties} 的 {@code ${DASHSCOPE_API_KEY}}
 * 注入，不落明文（H4 不变量③）. 错误抛 {@link LlmException}，携带模型名 / 状态码，不吞错.
 *
 * <p>端点与报文格式经真实 DashScope 调用实测（2026-10-03）：OpenAI 兼容端点
 * {@code /compatible-mode/v1/chat/completions} 返回顶层 {@code choices[0].message.content}，请求体顶层
 * {@code model/messages}；原生端点 {@code /api/v1/services/aigc/text-generation/generation} 实测返回
 * {@code output.text} 纯文本（无 choices 字段），与代码解析结构不匹配，故不采用原生端点.
 */
@Component
public class LlmClient {

  private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

  /** DashScope OpenAI 兼容（compatible-mode）文本生成接口路径（真实 API 实测可用）. */
  private static final String COMPLETIONS_PATH = "/compatible-mode/v1/chat/completions";

  private final ModelProperties props;
  private final RestClient restClient;

  public LlmClient(ModelProperties props, RestClient.Builder restClientBuilder) {
    this.props = props;
    this.restClient = restClientBuilder.baseUrl(props.getLlm().getBaseUrl()).build();
  }

  /**
   * 多轮对话生成.
   *
   * @param messages 消息列表（system / user / assistant）
   * @param options 可选参数；{@code model} 为空时回退默认模型
   * @return 生成的文本
   */
  public String chat(List<Message> messages, ChatOptions options) {
    String model =
        options != null && options.model() != null ? options.model() : props.getLlm().getModel();
    ChatRequest request = new ChatRequest(model, messages, options);
    try {
      ChatResponse response =
          restClient
              .post()
              .uri(COMPLETIONS_PATH)
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + props.getLlm().getApiKey())
              .contentType(MediaType.APPLICATION_JSON)
              .body(request)
              .retrieve()
              .body(ChatResponse.class);
      if (response == null
          || response.choices() == null
          || response.choices().isEmpty()
          || response.choices().get(0).message() == null) {
        throw new LlmException("DashScope 返回空结果, model=" + model);
      }
      return response.choices().get(0).message().content();
    } catch (LlmException ex) {
      throw ex;
    } catch (RestClientResponseException ex) {
      if (log.isErrorEnabled()) {
        log.error(
            "DashScope 调用失败, model={}, status={}, body={}",
            sanitizeLog(model),
            ex.getStatusCode(),
            sanitizeLog(ex.getResponseBodyAsString()));
      }
      throw new LlmException(
          "DashScope 调用失败, model=" + model + ", status=" + ex.getStatusCode(), ex);
    } catch (Exception ex) {
      if (log.isErrorEnabled()) {
        log.error("DashScope 调用异常, model={}", sanitizeLog(model), ex);
      }
      throw new LlmException("DashScope 调用异常, model=" + model, ex);
    }
  }

  /** 日志安全：去除可能注入 CR/LF 的字符，避免日志伪造（FindSecBugs CRLF_INJECTION_LOGS）. */
  private static String sanitizeLog(String value) {
    return value == null ? "null" : value.replace("\r", "\\r").replace("\n", "\\n");
  }

  /** DashScope OpenAI 兼容请求体：顶层 model/messages；null 字段不序列化（topP→top_p、maxTokens→max_tokens）. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  record ChatRequest(
      String model,
      List<Message> messages,
      Double temperature,
      @JsonProperty("top_p") Double topP,
      @JsonProperty("max_tokens") Integer maxTokens) {

    ChatRequest(String model, List<Message> messages, ChatOptions options) {
      this(
          model,
          messages,
          options != null ? options.temperature() : null,
          options != null ? options.topP() : null,
          options != null ? options.maxTokens() : null);
    }
  }

  /** DashScope OpenAI 兼容响应体：顶层 choices[0].message.content. */
  record ChatResponse(List<Choice> choices) {}

  record Choice(ChoiceMessage message) {}

  record ChoiceMessage(String role, String content) {}
}
