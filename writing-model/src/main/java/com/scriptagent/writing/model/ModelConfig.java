/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.model;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 模型服务配置装配：启用 {@link ModelProperties} 绑定（{@code model.*}）.
 *
 * <p>Bean（{@link LlmClient} / {@link EmbeddingClient} / {@link ModelService}）由 {@code @Component}
 * 注解经 writing-start 的 {@code scanBasePackages=com.scriptagent.writing} 自动扫描.
 */
@Configuration
@EnableConfigurationProperties(ModelProperties.class)
public class ModelConfig {}
