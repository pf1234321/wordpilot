/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.api.sse;

import com.scriptagent.writing.agent.StreamOutput;
import com.scriptagent.writing.common.constants.RedisKeys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SSE 流式封装验收（第 8 节课件 harness）：逐段推送 + 写 Redis 断线缓存 + 断线恢复.
 *
 * <p>直接驱动 {@link SseStreamingService#newStreamAdapter}（同步、确定性），不依赖异步调度；Redis 用 {@link
 * StringRedisTemplate} mock，断线恢复用例用 in-memory 累加模拟真实 append 语义.
 */
class SseStreamingServiceTest {

  private static final long USER_A = 1L;
  private static final String SESSION = "sess-001";

  @Test
  @DisplayName("关键回归：逐段推送发 SSE 帧并追加写 sse:cache:{userId}:{sessionId}（参考素材不漏，断线可恢复）")
  void stream_pushToken_sendsSseAndWritesCache() throws Exception {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(valueOps);
    SseStreamingService service = new SseStreamingService(redis);
    SseEmitter emitter = spy(new SseEmitter(0L));

    StreamOutput output = service.newStreamAdapter(USER_A, SESSION, emitter);
    output.onToken("第一段");
    output.onToken("第二段");

    String key = RedisKeys.sseCache(USER_A, SESSION);
    // 每段都发 SSE 帧
    verify(emitter, times(2)).send(any(SseEmitter.SseEventBuilder.class));
    // 每段都追加写缓存 key，且缓存 key 带 TTL（随追加刷新）
    verify(valueOps, times(2)).append(eq(key), anyString());
    verify(redis, times(2)).expire(eq(key), any());
  }

  @Test
  @DisplayName("生成完成：推送完成信号结束 emitter")
  void stream_complete_endsEmitter() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    when(redis.opsForValue()).thenReturn(mock(ValueOperations.class));
    SseStreamingService service = new SseStreamingService(redis);
    SseEmitter emitter = spy(new SseEmitter(0L));

    service.newStreamAdapter(USER_A, SESSION, emitter).onComplete();

    verify(emitter).complete();
  }

  @Test
  @DisplayName("生成失败：以错误结束 emitter")
  void stream_error_endsWithError() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    when(redis.opsForValue()).thenReturn(mock(ValueOperations.class));
    SseStreamingService service = new SseStreamingService(redis);
    SseEmitter emitter = spy(new SseEmitter(0L));
    IllegalStateException boom = new IllegalStateException("生成失败");

    service.newStreamAdapter(USER_A, SESSION, emitter).onError(boom);

    verify(emitter).completeWithError(boom);
  }

  @Test
  @DisplayName("读缓存：readCache 返回 sse:cache:{userId}:{sessionId} 已累计内容")
  void readCache_returnsAccumulatedContent() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(valueOps);
    when(valueOps.get(RedisKeys.sseCache(USER_A, SESSION))).thenReturn("第一段第二段");
    SseStreamingService service = new SseStreamingService(redis);

    String cached = service.readCache(USER_A, SESSION);

    assertEquals("第一段第二段", cached);
  }

  @Test
  @DisplayName("关键回归：断线重连以同 sessionId 从缓存恢复完整内容，不丢稿")
  void reconnect_replaysCachedContent() throws Exception {
    // 模拟真实 Redis：append 逐段落缓存、get 返回已累计完整内容
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(valueOps);
    when(valueOps.get(RedisKeys.sseCache(USER_A, SESSION))).thenReturn("开头正文结尾");
    SseStreamingService service = new SseStreamingService(redis);
    SseEmitter emitter = spy(new SseEmitter(0L));

    // 生成过程逐段推送，模拟中途断线
    StreamOutput output = service.newStreamAdapter(USER_A, SESSION, emitter);
    output.onToken("开头");
    output.onToken("正文");
    output.onToken("结尾");
    // 生成期间每段都落缓存（真实 Redis 据此累加）
    verify(valueOps, times(3)).append(eq(RedisKeys.sseCache(USER_A, SESSION)), anyString());

    // 断线重连：同 sessionId 从缓存恢复完整内容
    String recovered = service.readCache(USER_A, SESSION);

    assertNotNull(recovered);
    assertTrue(recovered.contains("开头"));
    assertTrue(recovered.contains("正文"));
    assertTrue(recovered.contains("结尾"));
    assertEquals("开头正文结尾", recovered);
  }

  @Test
  @DisplayName("无缓存时 readCache 返回 null")
  void readCache_returnsNull_whenEmpty() {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(valueOps);
    when(valueOps.get(anyString())).thenReturn(null);
    SseStreamingService service = new SseStreamingService(redis);

    assertNull(service.readCache(USER_A, SESSION));
  }
}
