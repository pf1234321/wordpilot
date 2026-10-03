/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */

/**
 * writing-storage 测试根包：验收 harness 套件.
 *
 * <p>JPA 测试默认 {@code @DataJpaTest} 走 H2 in-memory，Redis/ES 测试依赖 {@code @Tag("integration")} 标注 CI
 * 跳过、本地人工跑. 测试方法名遵循英文驼峰，课件原文以 {@code @DisplayName} 保留以便对号.
 */
package com.scriptagent.writing.storage;
