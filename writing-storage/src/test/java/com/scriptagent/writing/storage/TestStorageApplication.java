/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.storage;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 测试用 Spring Boot 配置：{@code @DataJpaTest} 需要定位 {@code @SpringBootConfiguration}.
 *
 * <p>仅在测试包中可见，扫描 writing-storage 内的 Entity / Repository / Redis / ES 组件.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackages = "com.scriptagent.writing.storage.entity")
@EnableJpaRepositories(basePackages = "com.scriptagent.writing.storage.repository")
@ComponentScan(basePackages = "com.scriptagent.writing.storage")
public class TestStorageApplication {}
