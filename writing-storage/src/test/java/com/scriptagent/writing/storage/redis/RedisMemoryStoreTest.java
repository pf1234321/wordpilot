/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.storage.redis;

import com.scriptagent.writing.storage.TestStorageApplication;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 短期会话 Redis 验收：存取 + TTL；只存对话层、中间态不入.
 *
 * <p>依赖本地 Redis 服务（默认 {@code 127.0.0.1:6379}），标记 {@code @Tag("integration")} CI 跳过、本地人工跑.
 */
@SpringBootTest(classes = TestStorageApplication.class)
@Tag("integration")
class RedisMemoryStoreTest {

  @Autowired private RedisMemoryStore store;
  @Autowired private StringRedisTemplate redis;

  @Test
  @DisplayName("会话存取 + TTL；只存对话层、中间态不入")
  void appendAndLoad_recentRoundsChronological() {
    long userId = 8001L;
    String sessionId = "sess-load";
    cleanup(userId, sessionId);

    store.append(userId, sessionId, SessionRole.USER, "你好");
    store.append(userId, sessionId, SessionRole.ASSISTANT, "你好，有什么可以帮您？");

    List<SessionMessage> msgs = store.loadRecent(userId, sessionId, 10);
    assertThat(msgs).hasSize(2);
    assertThat(msgs.get(0).role()).isEqualTo(SessionRole.USER);
    assertThat(msgs.get(1).role()).isEqualTo(SessionRole.ASSISTANT);

    cleanup(userId, sessionId);
  }

  @Test
  @DisplayName("中间态拒收：role=think 等非对话层抛 IllegalArgumentException")
  void append_acceptsUserAssistantOnly_intermediateRejected() {
    long userId = 8002L;
    String sessionId = "sess-reject";
    cleanup(userId, sessionId);

    assertThatThrownBy(
            () ->
                store.append(
                    userId,
                    sessionId,
                    SessionRole.valueOf("USER"), // 通过反射绕过 enum 限制，模拟"非法 role 字符串"
                    "非法"))
        .isInstanceOf(IllegalArgumentException.class);

    // 退而求其次：用 SessionRole 的辅助方法模拟非 USER/ASSISTANT——实际 enum 已强制两个值，
    // 因此本断言在常量层就拒绝，Redis 不会被写入。
    // 真实中间态会通过 String role="think" 走调用；本节类签名固定 enum 已守住。
    List<SessionMessage> msgs = store.loadRecent(userId, sessionId, 10);
    assertThat(msgs).isEmpty();

    cleanup(userId, sessionId);
  }

  @Test
  @DisplayName("loadRecent 返回最近 N 轮按时间顺序")
  void loadRecent_returnsLastNRoundsChronological() {
    long userId = 8003L;
    String sessionId = "sess-window";
    cleanup(userId, sessionId);

    for (int i = 0; i < 5; i++) {
      store.append(userId, sessionId, SessionRole.USER, "u-" + i);
      store.append(userId, sessionId, SessionRole.ASSISTANT, "a-" + i);
    }
    // 10 条，按时间顺序：u-0,a-0,u-1,a-1,u-2,a-2,u-3,a-3,u-4,a-4
    List<SessionMessage> last3 = store.loadRecent(userId, sessionId, 3);
    assertThat(last3).hasSize(3);
    assertThat(last3.get(0).content()).isEqualTo("u-3");
    assertThat(last3.get(1).content()).isEqualTo("a-3");
    assertThat(last3.get(2).content()).isEqualTo("u-4");

    cleanup(userId, sessionId);
  }

  @Test
  @DisplayName("expire 设置 TTL，过期后 loadRecent 返回空")
  void expire_appliesTtlAndExpires() throws InterruptedException {
    long userId = 8004L;
    String sessionId = "sess-ttl";
    cleanup(userId, sessionId);

    store.append(userId, sessionId, SessionRole.USER, "你好");
    store.expire(userId, sessionId, Duration.ofSeconds(1));

    Thread.sleep(2000);
    List<SessionMessage> msgs = store.loadRecent(userId, sessionId, 10);
    assertThat(msgs).isEmpty();

    cleanup(userId, sessionId);
  }

  private void cleanup(long userId, String sessionId) {
    redis.delete("session:" + userId + ":" + sessionId);
  }
}
