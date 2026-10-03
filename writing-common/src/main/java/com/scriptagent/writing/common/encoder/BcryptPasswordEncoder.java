/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.common.encoder;

import org.mindrot.jbcrypt.BCrypt;

/**
 * BCrypt 密码编码器（独立 jbcrypt 实现，不依赖 SpringSecurity）.
 *
 * <p>符合 skill 原则四"极简登录，不用 SpringSecurity"；只借用 BCrypt 算法能力. 以实例方法暴露 {@code encode}/{@code matches}，由
 * {@code PasswordEncoderConfig} 注册为 Spring Bean 供 LoginService 注入使用（第 3 节登录与鉴权）.
 */
public class BcryptPasswordEncoder {

  /** 默认 cost = 10（jbcrypt 库） */
  private static final int DEFAULT_COST = 10;

  public BcryptPasswordEncoder() {}

  public String encode(String raw) {
    if (raw == null) {
      throw new IllegalArgumentException("原始密码不能为空");
    }
    return BCrypt.hashpw(raw, BCrypt.gensalt(DEFAULT_COST));
  }

  public boolean matches(String raw, String hashed) {
    if (raw == null || hashed == null || hashed.isEmpty()) {
      return false;
    }
    try {
      return BCrypt.checkpw(raw, hashed);
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }
}
