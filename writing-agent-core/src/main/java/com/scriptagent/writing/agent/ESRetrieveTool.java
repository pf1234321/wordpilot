/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.agent;

import com.scriptagent.writing.common.UserContext;
import com.scriptagent.writing.model.ModelService;
import com.scriptagent.writing.storage.es.MaterialChunkRepository;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 自定义 RAG 检索工具（对齐 {@link AgentTool}）：封装 ES 素材检索，写作时把相似切片作为参考素材注入 Prompt.
 *
 * <p>查询经 {@link ModelService#embed}（LLM/Embedding 唯一出口，宪法三/决策九，不直连 {@code EmbeddingClient}） 向量化，再经
 * {@link MaterialChunkRepository#search} 按 user_id + cosine 召回 TopN 切片文本. user_id 一律 {@link
 * UserContext#require()}（H4 不变量①，禁止前端参数取），保证只召回当前用户的素材. ES 失败记日志并返回空参考 （不中断生成，视为无参考素材）.
 */
@Component
public class ESRetrieveTool implements AgentTool {

  private static final Logger log = LoggerFactory.getLogger(ESRetrieveTool.class);

  private static final int TOP_K = 5;

  private final ModelService modelService;
  private final MaterialChunkRepository materialChunkRepository;

  public ESRetrieveTool(
      ModelService modelService, MaterialChunkRepository materialChunkRepository) {
    this.modelService = modelService;
    this.materialChunkRepository = materialChunkRepository;
  }

  @Override
  public String name() {
    return "es_retrieve";
  }

  @Override
  public String execute(String query) {
    // AgentTool 通用入口：user_id 仍从请求线程 ThreadLocal UserContext 取（H4 不变量①）
    return execute(UserContext.require(), query);
  }

  /**
   * 带显式 userId 的检索入口：SSE 在独立线程执行，请求线程的 ThreadLocal UserContext 不跨线程， 故由编排层把已注入的 userId
   * 显式传下（来源仍是登录鉴权链路，禁止前端参数取）。 query 向量化后按 user_id + cosine 召回 TopN 切片.
   */
  public String execute(Long userId, String query) {
    float[] vector = modelService.embed(query);
    List<String> hits;
    try {
      hits = materialChunkRepository.search(userId, vector, TOP_K);
    } catch (IOException e) {
      log.error("ES 检索失败, userId={}", userId, e);
      return "";
    }
    return String.join("\n", hits);
  }
}
