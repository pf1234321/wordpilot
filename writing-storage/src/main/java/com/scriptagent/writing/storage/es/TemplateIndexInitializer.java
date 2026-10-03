/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.storage.es;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 应用启动时确保 {@code writing_template} 模板索引存在.
 *
 * <p>实现 {@link ApplicationRunner}，Spring 上下文就绪后立即执行，索引已存在则跳过；失败时记录日志，不阻塞应用启动（运维侧可独立触发重建）.
 */
@Component
public class TemplateIndexInitializer implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(TemplateIndexInitializer.class);

  private final TemplateRepository repository;

  public TemplateIndexInitializer(TemplateRepository repository) {
    this.repository = repository;
  }

  @Override
  public void run(ApplicationArguments args) {
    try {
      repository.ensureIndex();
    } catch (Exception ex) {
      log.error("ES 模板索引初始化失败（已记录，后续可手工重建）：{}", ex.getMessage(), ex);
    }
  }
}
