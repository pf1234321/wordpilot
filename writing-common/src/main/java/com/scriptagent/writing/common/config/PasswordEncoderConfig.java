/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.common.config;

import org.springframework.context.annotation.Configuration;

/**
 * 占位：未来接入 {@code PasswordEncoder} 接口抽象时把编码器注册为 Bean.
 *
 * <p>当前阶段使用 {@link com.scriptagent.writing.common.encoder.BcryptPasswordEncoder} 的静态方法， 不需要 Spring
 * 容器托管；保留本类仅为后续扩展.
 */
@Configuration
public class PasswordEncoderConfig {}
