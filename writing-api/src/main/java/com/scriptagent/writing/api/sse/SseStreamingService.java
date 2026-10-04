/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.api.sse;

import com.scriptagent.writing.agent.StreamOutput;
import com.scriptagent.writing.common.constants.RedisKeys;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 流式封装（第 8 节写作编排，writing-api）.
 *
 * <p>把 writing-agent-core 的 {@link StreamOutput} 回调桥接为 SSE 帧，并逐段写 Redis {@code
 * sse:cache:{userId}:{sessionId}}（决策六：流式内容缓存，支持断线恢复）。生成任务在独立线程执行，保证 onToken 帧 边生成边推送、前端增量渲染；缓存 TTL
 * 随每次追加刷新，断线后经 {@link #readCache} 恢复。
 *
 * <p>适配器（{@link #newStreamAdapter}）包级可见，供单元测试直接驱动 onToken/onComplete/onError，断言逐段推送 + 缓存写入。
 */
@Service
public class SseStreamingService {

  /** 断线恢复缓存 TTL：与短期会话默认一致（30 分钟），随追加刷新. */
  private static final Duration CACHE_TTL = Duration.ofMinutes(30);

  private final StringRedisTemplate redis;
  private final ExecutorService executor = Executors.newCachedThreadPool();

  public SseStreamingService(StringRedisTemplate redis) {
    this.redis = redis;
  }

  /**
   * 开启一次 SSE 流式输出.
   *
   * @param userId 当前用户（缓存 key 按 user_id 隔离）
   * @param sessionId 会话 id
   * @param task 生成任务：接收 {@link StreamOutput} 并驱动其 onToken/onComplete/onError
   * @return 已开始异步推送的 {@link SseEmitter}（不设超时，长文生成）
   */
  public SseEmitter stream(
      Long userId, String sessionId, java.util.function.Consumer<StreamOutput> task) {
    SseEmitter emitter = new SseEmitter(0L);
    executor.execute(() -> task.accept(newStreamAdapter(userId, sessionId, emitter)));
    return emitter;
  }

  /**
   * 构造 {@link StreamOutput} → SSE + Redis 缓存 适配器：onToken 逐段发帧并追加写缓存；onComplete 结束； onError
   * 以错误结束。包级可见供测试直接驱动.
   */
  StreamOutput newStreamAdapter(Long userId, String sessionId, SseEmitter emitter) {
    return new StreamOutput() {
      @Override
      public void onToken(String token) {
        appendCache(userId, sessionId, token);
        try {
          emitter.send(SseEmitter.event().data(token));
        } catch (IOException e) {
          throw new IllegalStateException("SSE 帧推送失败", e);
        }
      }

      @Override
      public void onComplete() {
        emitter.complete();
      }

      @Override
      public void onError(Throwable throwable) {
        emitter.completeWithError(throwable);
      }
    };
  }

  /** 读回断线缓存（断线恢复）：返回 {@code sse:cache:{userId}:{sessionId}} 已累计内容；无则 null. */
  public String readCache(Long userId, String sessionId) {
    return redis.opsForValue().get(RedisKeys.sseCache(userId, sessionId));
  }

  /** 追加一段 token 到断线缓存并刷新 TTL（断线重连可恢复完整内容）. */
  private void appendCache(Long userId, String sessionId, String token) {
    String key = RedisKeys.sseCache(userId, sessionId);
    redis.opsForValue().append(key, token);
    redis.expire(key, CACHE_TTL);
  }
}
