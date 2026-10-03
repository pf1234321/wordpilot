/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.common.config;

import com.scriptagent.writing.common.encoder.BcryptPasswordEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 密码编码器 Bean 注册：把 {@link BcryptPasswordEncoder} 注册为 Spring Bean，供 LoginService 注入使用（第 3 节登录与鉴权）.
 *
 * <p>原为第 1 节空占位；按用户决策改造为注册 BCrypt Bean。编码器仅借用 jbcrypt 算法， 不依赖 SpringSecurity（决策三）.
 */
@Configuration
public class PasswordEncoderConfig {

  @Bean
  public BcryptPasswordEncoder bcryptPasswordEncoder() {
    return new BcryptPasswordEncoder();
  }
}
