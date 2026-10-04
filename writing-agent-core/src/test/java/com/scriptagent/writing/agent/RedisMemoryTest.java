/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.agent;

import com.scriptagent.writing.storage.redis.RedisMemoryStore;
import com.scriptagent.writing.storage.redis.SessionMessage;
import com.scriptagent.writing.storage.redis.SessionRole;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 短期会话记忆单测：只存对话层、丢弃中间态、窗口滑出（课件 harness 关键回归）. */
@ExtendWith(MockitoExtension.class)
class RedisMemoryTest {

  private static final Long USER_ID = 1L;
  private static final String SESSION = "s1";
  private static final int MAX_ROUNDS = 10;

  @Mock private RedisMemoryStore store;

  private RedisMemory newMemory() {
    return new RedisMemory(USER_ID, SESSION, store, MAX_ROUNDS);
  }

  @Test
  @DisplayName(
      "shortTermMemory_storesOnlyDialog_dropsIntermediate：只存 user+assistant、丢弃 think/tool 中间态")
  void shortTermMemory_storesOnlyDialog_dropsIntermediate() {
    RedisMemory memory = newMemory();
    memory.append("think", "先查素材再写"); // 思考过程，应被丢弃
    memory.append("user", "帮我写推文");
    memory.append("assistant", "初稿如下…");
    // 只落对话层 user+assistant
    verify(store).append(USER_ID, SESSION, SessionRole.USER, "帮我写推文");
    verify(store).append(USER_ID, SESSION, SessionRole.ASSISTANT, "初稿如下…");
    // 中间态未写入 Redis
    verify(store, never()).append(USER_ID, SESSION, SessionRole.USER, "先查素材再写");
    verify(store, never()).append(USER_ID, SESSION, SessionRole.ASSISTANT, "先查素材再写");
  }

  @Test
  @DisplayName("loadRecent 返回存储层已滑窗的最近 N 轮（超过窗口的最早轮次由 RedisMemoryStore 滑出）")
  void loadRecent_returnsWindowedHistoryFromStore() {
    RedisMemory memory = newMemory();
    List<SessionMessage> history =
        List.of(
            new SessionMessage(SessionRole.USER, "帮我写推文", Instant.now()),
            new SessionMessage(SessionRole.ASSISTANT, "初稿如下…", Instant.now()));
    when(store.loadRecent(USER_ID, SESSION, MAX_ROUNDS)).thenReturn(history);
    assertEquals(history, memory.loadRecent(MAX_ROUNDS));
    verify(store).loadRecent(USER_ID, SESSION, MAX_ROUNDS);
  }

  @Test
  void maxRounds_returnsConfiguredWindow() {
    assertEquals(MAX_ROUNDS, newMemory().maxRounds());
  }
}
