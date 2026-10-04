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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 三类写作路由验收（第 8 节课件 harness）：三模式路由到正确 Agent；仿写挂检索工具；模板渲染前置；未知模式拒绝不静默降级.
 *
 * <p>全部 mock（AgentFactory / SseStreamingService / TemplateRepository / TemplateService）；路由逻辑在
 * {@code sse.stream} 的 lambda 内，故 mock 其 answer 真正执行 consumer，断言路由到的模式与传给 Agent 的消息.
 */
class WritingServiceTest {

  private static final long USER_A = 1L;

  private final AgentFactory agentFactory = mock(AgentFactory.class);
  private final SseStreamingService sse = mock(SseStreamingService.class);
  private final TemplateRepository templateRepository = mock(TemplateRepository.class);
  private final TemplateService templateService = mock(TemplateService.class);
  private final ArticleService articleService = mock(ArticleService.class);
  private final WritingService service =
      new WritingService(agentFactory, sse, templateRepository, templateService, articleService);

  @BeforeEach
  void setUp() {
    UserContext.set(USER_A);
    // 路由逻辑在 sse.stream 的 lambda 内：mock 真正执行 consumer（传 mock StreamOutput），返回 emitter
    when(sse.stream(eq(USER_A), anyString(), any()))
        .thenAnswer(
            inv -> {
              @SuppressWarnings("unchecked")
              java.util.function.Consumer<StreamOutput> task = inv.getArgument(2);
              task.accept(mock(StreamOutput.class));
              return new SseEmitter(0L);
            });
  }

  @AfterEach
  void tearDown() {
    UserContext.clear();
  }

  @Test
  @DisplayName("对话写作路由到 dialog Agent（无检索工具）；sessionId 缺失由服务端生成")
  void route_dialog_noTool() throws Exception {
    WritingAgent agent = mock(WritingAgent.class);
    when(agentFactory.create(eq("dialog"), eq(USER_A), anyString())).thenReturn(agent);

    SseEmitter emitter = service.dialog(new DialogRequest("帮我写一篇新品推文", null));

    assertNotNull(emitter);
    // 路由到对话模式：工厂按 dialog 生成（该模式无检索工具）
    verify(agentFactory).create(eq("dialog"), eq(USER_A), anyString());
    // 把用户输入作为消息交给 Agent
    verify(agent).run(eq(USER_A), anyString(), eq("帮我写一篇新品推文"), any(StreamOutput.class));
  }

  @Test
  @DisplayName("关键回归：仿写路由到 rag Agent（挂 ESRetrieveTool，参考素材不漏注入）")
  void route_rag_injectsEsRetrieveTool() throws Exception {
    WritingAgent agent = mock(WritingAgent.class);
    when(agentFactory.create(eq("rag"), eq(USER_A), eq("sessX"))).thenReturn(agent);

    SseEmitter emitter = service.rag(new RagRequest("按公司年报风格写周报", "sessX"));

    assertNotNull(emitter);
    // 路由到仿写模式：工厂按 rag 生成（该模式挂 ESRetrieveTool，检索参考素材注入 Prompt）
    verify(agentFactory).create(eq("rag"), eq(USER_A), eq("sessX"));
    // 客户端回传的 sessionId 透传，需求作为消息交给 Agent
    verify(agent).run(eq(USER_A), eq("sessX"), eq("按公司年报风格写周报"), any(StreamOutput.class));
  }

  @Test
  @DisplayName("模板写作先读模板+校验启停+渲染，渲染后 Prompt 作为消息交给模板 Agent")
  void route_template_rendersBeforeAgent() throws Exception {
    TemplateDoc tpl = new TemplateDoc();
    tpl.setId("tpl-1");
    tpl.setStatus("enabled");
    when(templateRepository.findByIdAndUserId("tpl-1", USER_A)).thenReturn(Optional.of(tpl));
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("title", "周报");
    when(templateService.render(tpl, params)).thenReturn("【填充后的模板正文】周报内容");
    WritingAgent agent = mock(WritingAgent.class);
    when(agentFactory.create(eq("template"), eq(USER_A), anyString())).thenReturn(agent);

    SseEmitter emitter = service.template(new TemplateRequest("tpl-1", params, null));

    assertNotNull(emitter);
    // 编排层：读模板 + 渲染（校验启停由 loadEnabledTemplate 承担）
    verify(templateRepository).findByIdAndUserId("tpl-1", USER_A);
    verify(templateService).render(tpl, params);
    // 路由到模板模式；渲染后 Prompt（非原始 params）作为消息交给 Agent
    verify(agentFactory).create(eq("template"), eq(USER_A), anyString());
    verify(agent).run(eq(USER_A), anyString(), eq("【填充后的模板正文】周报内容"), any(StreamOutput.class));
  }

  @Test
  @DisplayName("模板停用（status != enabled）拒绝用于新写作，不生成残缺稿")
  void route_template_disabled_rejects() throws Exception {
    TemplateDoc tpl = new TemplateDoc();
    tpl.setId("tpl-1");
    tpl.setStatus("disabled");
    when(templateRepository.findByIdAndUserId("tpl-1", USER_A)).thenReturn(Optional.of(tpl));

    BusinessException e =
        org.junit.jupiter.api.Assertions.assertThrows(
            BusinessException.class,
            () -> service.template(new TemplateRequest("tpl-1", Map.of("title", "周报"), null)));

    assertEquals(ErrorCode.TEMPLATE_DISABLED, e.getErrorCode());
    verify(agentFactory, never()).create(anyString(), any(), any());
  }

  @Test
  @DisplayName("关键回归：未知模式被拒且转发到 SSE onError，不静默降级、不把 emitter 悬空")
  void route_unknownMode_rejects() throws Exception {
    // 模拟工厂对未知模式的拒绝（非法参数）
    when(agentFactory.create(anyString(), eq(USER_A), anyString()))
        .thenThrow(new IllegalArgumentException("未知写作模式: xxx"));
    StreamOutput output = mock(StreamOutput.class);
    doAnswer(
        inv -> {
          @SuppressWarnings("unchecked")
          java.util.function.Consumer<StreamOutput> task = inv.getArgument(2);
          task.accept(output);
          return new SseEmitter(0L);
        })
        .when(sse)
        .stream(eq(USER_A), anyString(), any());

    service.dialog(new DialogRequest("触发未知模式", null));

    // 工厂拒绝被转发到 SSE onError（不静默吞错、不降级为任意模式）
    verify(output).onError(any(Throwable.class));
  }

  @Test
  @DisplayName("关键回归：写作生成完成后统一落稿到稿件历史（FR-005，前端稿件历史数据源）")
  void route_dialog_persistsArticleOnCompletion() throws Exception {
    WritingAgent agent = mock(WritingAgent.class);
    when(agentFactory.create(eq("dialog"), eq(USER_A), anyString())).thenReturn(agent);
    // 断线缓存已有完整生成内容 → 生成完成后落稿
    when(sse.readCache(eq(USER_A), anyString())).thenReturn("生成内容ABC");

    service.dialog(new DialogRequest("帮我写一篇新品推文", "sess1"));

    // 落稿：user_id + write_type + 标题(指令前40字) + 完整内容；dialog 无素材/模板关联
    verify(articleService)
        .save(eq(USER_A), eq("dialog"), eq("帮我写一篇新品推文"), eq("生成内容ABC"), isNull(), isNull());
  }
}
