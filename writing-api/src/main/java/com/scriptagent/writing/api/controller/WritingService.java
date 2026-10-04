/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.api.controller;

import com.scriptagent.writing.agent.AgentFactory;
import com.scriptagent.writing.agent.StreamOutput;
import com.scriptagent.writing.agent.WritingAgent;
import com.scriptagent.writing.api.dto.DialogRequest;
import com.scriptagent.writing.api.dto.RagRequest;
import com.scriptagent.writing.api.dto.TemplateRequest;
import com.scriptagent.writing.api.sse.SseStreamingService;
import com.scriptagent.writing.business.ArticleService;
import com.scriptagent.writing.business.TemplateService;
import com.scriptagent.writing.common.UserContext;
import com.scriptagent.writing.common.exception.BusinessException;
import com.scriptagent.writing.common.exception.ErrorCode;
import com.scriptagent.writing.storage.es.TemplateDoc;
import com.scriptagent.writing.storage.es.TemplateRepository;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 三类写作路由（第 8 节写作编排，writing-api）.
 *
 * <p>按用户选择的写作文式（dialog / rag / template）把请求路由到 {@link AgentFactory} 生成的无状态 Agent， 统一经 {@link
 * SseStreamingService} SSE 流式返回。只做路由与协调（宪法九：一套底座三种模式；编排层不越界）： 不碰 Agent 内部、不直连模型。user_id 唯一来源 {@link
 * UserContext#require()}（H4 不变量①，禁止前端参数取）。 模板模式在编排层完成"读模板→校验启停→渲染"，模板 Agent 只接收渲染后 Prompt（技术方案 §4.3
 * / §7.2 / 流程五）。
 */
@RestController
@RequestMapping("/api/writing")
public class WritingService {

  private final AgentFactory agentFactory;
  private final SseStreamingService sse;
  private final TemplateRepository templateRepository;
  private final TemplateService templateService;
  private final ArticleService articleService;

  public WritingService(
      AgentFactory agentFactory,
      SseStreamingService sse,
      TemplateRepository templateRepository,
      TemplateService templateService,
      ArticleService articleService) {
    this.agentFactory = agentFactory;
    this.sse = sse;
    this.templateRepository = templateRepository;
    this.templateService = templateService;
    this.articleService = articleService;
  }

  /** 一句话多轮对话写作：路由到对话 Agent（无检索工具），user_id 取自 UserContext. */
  @PostMapping("/dialog")
  public SseEmitter dialog(@Valid @RequestBody DialogRequest req) {
    Long userId = UserContext.require();
    String sessionId = resolveSession(req.sessionId());
    return sse.stream(
        userId, sessionId, output -> runAgent(userId, sessionId, "dialog", req.content(), output));
  }

  /** 素材仿写（RAG）：路由到仿写 Agent（挂 ESRetrieveTool，检索参考素材注入），user_id 取自 UserContext. */
  @PostMapping("/rag")
  public SseEmitter rag(@Valid @RequestBody RagRequest req) {
    Long userId = UserContext.require();
    String sessionId = resolveSession(req.sessionId());
    return sse.stream(
        userId, sessionId, output -> runAgent(userId, sessionId, "rag", req.requirement(), output));
  }

  /**
   * 模板写作：编排层先读模板（user_id 隔离）→ 校验启停 → 渲染填充（含必填校验），再把渲染后 Prompt 交模板 Agent （TEMPLATE_CORE 系统提示提供身份）.
   */
  @PostMapping("/template")
  public SseEmitter template(@Valid @RequestBody TemplateRequest req) {
    Long userId = UserContext.require();
    String sessionId = resolveSession(req.sessionId());
    TemplateDoc tpl = loadEnabledTemplate(req.templateId(), userId);
    String rendered = templateService.render(tpl, req.params());
    return sse.stream(
        userId, sessionId, output -> runAgent(userId, sessionId, "template", rendered, output));
  }

  /**
   * 执行一次写作路由 + 推理；生成完成（agent.run 返回）后把累积内容统一落稿到稿件历史（FR-005）， 使前端"稿件历史"能读到真实生成结果。任何异常（含工厂未知模式拒绝） 转发到
   * {@link StreamOutput#onError}，让 SSE 以错误帧结束，不静默吞错、不把 emitter 悬空（路由不静默降级，FR-002）.
   */
  private void runAgent(
      Long userId, String sessionId, String mode, String message, StreamOutput output) {
    try {
      WritingAgent agent = agentFactory.create(mode, userId, sessionId);
      agent.run(userId, sessionId, message, output);
      // 三类写作统一落稿：从断线缓存读回完整生成内容（SSE 逐段写入 redis sse:cache）
      String content = sse.readCache(userId, sessionId);
      if (content != null && !content.isBlank()) {
        articleService.save(userId, mode, titleOf(message), content, null, null);
      }
    } catch (RuntimeException e) {
      output.onError(e);
    }
  }

  /** 稿件标题：取写作指令前 40 字（去换行），空输入给兜底标题. */
  private static String titleOf(String message) {
    if (message == null || message.isBlank()) {
      return "未命名稿件";
    }
    String oneLine = message.replace('\n', ' ').trim();
    return oneLine.length() > 40 ? oneLine.substring(0, 40) : oneLine;
  }

  /** 读模板并校验启停：缺失/越权统一按不存在处理；ES 受检异常转业务异常（触发回滚/统一响应）. */
  private TemplateDoc loadEnabledTemplate(String templateId, Long userId) {
    TemplateDoc tpl;
    try {
      tpl =
          templateRepository
              .findByIdAndUserId(templateId, userId)
              .orElseThrow(() -> new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND));
    } catch (IOException e) {
      throw new BusinessException(ErrorCode.ES_OPERATION_FAILED, e.getMessage());
    }
    if (!"enabled".equals(tpl.getStatus())) {
      throw new BusinessException(ErrorCode.TEMPLATE_DISABLED);
    }
    return tpl;
  }

  /** 会话 id：请求可携带（多轮回传续上下文）；缺失由服务端生成. */
  private static String resolveSession(String sessionId) {
    return (sessionId == null || sessionId.isBlank()) ? UUID.randomUUID().toString() : sessionId;
  }
}
