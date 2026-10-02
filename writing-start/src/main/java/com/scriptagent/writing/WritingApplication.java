/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * ScriptAgent 启动入口.
 *
 * <p>单一可执行 fat JAR 由此启动；启动模块聚合 writing-api / writing-business / writing-agent-core /
 * writing-storage / writing-model / writing-common 六个业务模块.
 */
@SpringBootApplication(scanBasePackages = "com.scriptagent.writing")
@EnableAsync
public class WritingApplication {

  public static void main(String[] args) {
    SpringApplication.run(WritingApplication.class, args);
  }
}
