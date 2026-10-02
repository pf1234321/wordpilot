/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc OpenAPI 配置.
 *
 * <p>访问路径：
 *
 * <ul>
 *   <li>Swagger UI：{@code /swagger-ui.html}
 *   <li>JSON spec：{@code /v3/api-docs}
 * </ul>
 */
@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI wordpilotOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("ScriptAgent API")
                .description("通用 AI 写作中台后端接口")
                .version("v0.1.0")
                .contact(new Contact().name("ScriptAgent Team"))
                .license(new License().name("Apache 2.0")));
  }
}
