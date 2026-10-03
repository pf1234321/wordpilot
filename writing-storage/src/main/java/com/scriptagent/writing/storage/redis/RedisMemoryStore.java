/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.storage.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scriptagent.writing.common.constants.RedisKeys;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 短期会话 Redis 存储：{@code session:{userId}:{sessionId}} key.
 *
 * <p>仅接受对话层消息（{@link SessionRole#USER} / {@link SessionRole#ASSISTANT}），其他角色 （think / tool 等中间态）一律抛
 * {@link IllegalArgumentException} 拒收， 与 constitution 原则八"短期记忆只存对话层"一致. 默认 TTL = 30 分钟，可由 {@link
 * #expire(Long, String, Duration)} 自定义.
 */
@Repository
public class RedisMemoryStore {

  private static final Logger log = LoggerFactory.getLogger(RedisMemoryStore.class);

  /** 默认 TTL = 30 分钟. */
  public static final Duration DEFAULT_TTL = Duration.ofMinutes(30);

  private static final TypeReference<List<SessionMessage>> MESSAGE_LIST = new TypeReference<>() {};

  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;

  public RedisMemoryStore(@Lazy StringRedisTemplate redis, @Lazy ObjectMapper objectMapper) {
    this.redis = redis;
    this.objectMapper = objectMapper;
  }

  /** 追加一条对话层消息. 非对话层 role（think / tool 等中间态）抛 {@link IllegalArgumentException}，Redis 长度不变. */
  public void append(Long userId, String sessionId, SessionRole role, String content) {
    if (role != SessionRole.USER && role != SessionRole.ASSISTANT) {
      throw new IllegalArgumentException(
          "RedisMemoryStore 仅接受对话层 role=user/assistant，拒绝中间态: " + role);
    }
    String key = RedisKeys.session(userId, sessionId);
    SessionMessage msg = SessionMessage.of(role, content);
    List<SessionMessage> current = loadRaw(key);
    current.add(msg);
    try {
      String json = objectMapper.writeValueAsString(current);
      redis.opsForValue().set(key, json);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("SessionMessage 序列化失败", e);
    }
  }

  /** 取最近 N 条消息，按时间顺序（最早在前）返回；不足 N 条则返回全部. */
  public List<SessionMessage> loadRecent(Long userId, String sessionId, int maxRounds) {
    String key = RedisKeys.session(userId, sessionId);
    List<SessionMessage> all = loadRaw(key);
    if (all.size() <= maxRounds) {
      return Collections.unmodifiableList(all);
    }
    return Collections.unmodifiableList(
        new ArrayList<>(all.subList(all.size() - maxRounds, all.size())));
  }

  /** 设置 key TTL（覆盖之前的 TTL）；若 key 不存在则不操作. */
  public void expire(Long userId, String sessionId, Duration ttl) {
    String key = RedisKeys.session(userId, sessionId);
    Boolean ok = redis.expire(key, ttl);
    if (Boolean.FALSE.equals(ok)) {
      log.warn("expire 失败：key 不存在或已是 TTL={}：{}", ttl, key);
    }
  }

  /** 清空该会话的全部消息. */
  public void clear(Long userId, String sessionId) {
    redis.delete(RedisKeys.session(userId, sessionId));
  }

  private List<SessionMessage> loadRaw(String key) {
    String json = redis.opsForValue().get(key);
    if (json == null || json.isEmpty()) {
      return new ArrayList<>();
    }
    try {
      return new ArrayList<>(objectMapper.readValue(json, MESSAGE_LIST));
    } catch (JsonProcessingException e) {
      log.error("SessionMessage 反序列化失败（key={}）：{}", key, e.getMessage());
      return new ArrayList<>();
    }
  }
}
