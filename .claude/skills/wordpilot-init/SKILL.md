---
name: wordpilot-init
description: >-
  初始化 ScriptAgent（智稿引擎）的工程地基：Maven 7 模块骨架、Spring Boot 3.x + Java 21、
  Spring Data JPA + MySQL8、Redis4、ES7、AgentScope2.0、POI + PDFBox、结构化日志、Actuator 监控、
  OpenAPI + 统一响应/错误码、开发规范（Spotless Google 格式 + 阿里 P3C + Checkstyle）、
  代码安全检查（SpotBugs + Find Security Bugs + PMD + OWASP Dependency-Check）、CI + pre-commit，
  以及可选的前端 Vue3 + Vite 骨架。当用户要「初始化项目 / 搭工程骨架 / 起脚手架 / 配中间件
  MySQL Redis ES / 加日志监控 / 加开发规范 / 加代码安全检查」时使用。
---

# ScriptAgent（智稿引擎）项目初始化 Skill

把"工程地基"一次性、标准化地装好——业务逻辑（三类核心写作能力 + 四类支撑能力）不在本 skill 范围内。
本 skill 面向 Java 21 + Spring Boot 3.x + AgentScope + MySQL/Redis/ES 的写作中台单体，也可复用于同类技术栈项目。

## 什么时候用

- 新建 ScriptAgent 仓库、或给空仓库起工程骨架时
- 要给项目补齐日志 / 监控 / API 规范 / 开发规范 / 安全检查时
- 要配好中间件（MySQL / Redis / ES7）连接时
- 任何 Java 21 + Spring Boot 3.x + AgentScope + MySQL/Redis/ES 的写作中台单体，想一次到位装好工程地基时

## 不做什么（边界）

- 不实现三类核心写作能力（一句话多轮对话 / 素材仿写 RAG / 模板写作）与四类支撑能力（登录 / 素材管理 /
  模板管理 / 稿件历史）——那是业务模块，走 Spec-Kit 的 user story 拆解
- 不实现文档解析逻辑、RAG 管线、Agent 编排、记忆分层——这些是业务代码
- 不硬编码任何密钥 / token / API key / 数据库密码——一律用环境变量占位（`${ENV_VAR}`）
- 不替换已存在的业务代码；只新增基础设施与配置

## 前置约定（来自 constitution + TechnicalSolution）

实施前确认这些硬约束，写进配置：
- **JDK 21、Spring Boot 3.x、Maven 7 模块、单可执行 fat JAR** 部署，数据不出内网
- **中间件**：MySQL8（业务数据）、Redis4（Token / 会话 / SSE 缓存 / 限流）、ES7（素材向量库 / 模板库）
- **ORM**：Spring Data JPA（Hibernate）；**Agent 底座**：AgentScope2.0（Agent 管理 / 工具调用 / 记忆管理抽象）
- **文档解析**：POI（docx）+ PDFBox（pdf）+ txt/md 直读
- **前端（可选）**：Vue3 + Vite + Element Plus + Pinia + Axios + SSE
- **模型服务**：LLM = 阿里云百炼通义千问 Qwen（DashScope API）、Embedding = BGE bge-m3（本地模型 + JVM 进程内 ONNX Runtime 推理，维度 1024），
  API key 与服务地址一律 `${ENV_VAR}`，两条调用链独立、可切换
- 代码必须过 Google 格式 + 阿里编码规约 + 安全扫描，才能合并

---

## 初始化步骤（按顺序执行，每步完成后 `git commit`）

### 0. 确认参数

向用户确认：`groupId`、根 `artifactId`、7 个模块清单（默认 ScriptAgent 模块）、端口（默认 8080）、
JDK（21）、MySQL/Redis/ES 的连接信息（host / port / 库名），以及是否需要前端 Vue3 骨架。

### 1. Maven 7 模块骨架

建父 `pom.xml`（packaging=pom，统一版本管理）+ 7 个子模块：
`writing-start`（启动模块，含 `main`，打 fat JAR）、`writing-api`、`writing-business`、
`writing-agent-core`、`writing-storage`、`writing-model`、`writing-common`。

**依赖方向（单向、无循环）**：`writing-start → writing-api → writing-business → writing-agent-core / writing-storage / writing-model / writing-common`。

```
wordpilot/
├── writing-start        # 启动聚合：主类、依赖聚合、fat JAR
├── writing-api          # 接口：登录、素材、三类写作、模板、稿件；Token 拦截器
├── writing-business     # 业务编排：写作路由、素材调度、模板渲染、稿件历史
├── writing-agent-core   # Agent 核心：Agent 工厂、三层记忆、ESRetrieveTool、Prompt、SSE（依赖 writing-model）
├── writing-storage      # 存储：JPA Entity/Repository、Redis 操作、ES 操作、解析、切片
├── writing-model        # 模型服务：LLM（阿里 Qwen）、Embedding（BGE）、模型适配
└── writing-common       # 公共基础：工具类、加密、UserContext、异常、常量、DTO、枚举、JPA 审计监听器
```

### 2. 基础依赖与版本管理（父 pom `dependencyManagement` / `pluginManagement`）

固定 Spring Boot BOM、AgentScope2.0、`spring-boot-starter-data-jpa` + `mysql-connector-j`、
`spring-boot-starter-data-redis`、ES7 客户端（Java Client 或兼容 ES7 的 `spring-data-elasticsearch`）、
POI、PDFBox、springdoc-openapi、`spring-boot-starter-web`、`actuator`、`micrometer-registry-prometheus`、
logstash-logback-encoder 等版本。**具体版本号以实施时最新稳定版为准，先锁定再开发。**

> ⚠️ ES7 与客户端 / Spring Data 版本组合必须先锁定并验证连通，避免版本兼容问题（见需求文档风险表）。

### 3. 中间件连接配置（ScriptAgent 特有，重点）

`writing-start/src/main/resources/application.yaml`，连接信息一律 `${ENV_VAR}`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://${MYSQL_HOST}:3306/${MYSQL_DB}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: ${MYSQL_USER}
    password: ${MYSQL_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: update        # 开发环境自动建表；生产环境设为 none，手动执行 SQL 脚本
    open-in-view: false
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}

elasticsearch:
  uris: ${ES_URIS}            # 如 http://localhost:9200
  index:
    material_chunk: writing_material_chunk   # dim=1024, cosine

model:
  llm:
    provider: dashscope
    api_key: ${DASHSCOPE_API_KEY}
    model: qwen-max
  embedding:
    provider: bge
    model-path: ${BGE_MODEL_PATH}    # 本地 bge-m3 模型目录（含 onnx/model.onnx）
    model: bge-m3
    dim: 1024
    load-on-start: true              # 启动时加载一次，单例
memory:
  short-term:
    max-rounds: 10            # 短期记忆默认最近 10 轮
```

生产环境（profile=prod）关闭自动建表、手动执行 SQL；敏感配置缺失时启动清晰报错，不静默失败。

### 4. 日志（结构化）

`writing-start/src/main/resources/logback-spring.xml`：
- 开发环境：彩色 console pattern
- 生产环境（profile=prod）：JSON 输出（`LogstashEncoder`），带 `traceId`（MDC）
- 统一通过 SLF4J 打日志，**禁止 `System.out`**

### 5. 监控（Actuator + Micrometer + Prometheus，可选）

依赖：`spring-boot-starter-actuator`、`micrometer-registry-prometheus`。`application.yaml`：

```yaml
management:
  endpoints.web.exposure.include: health,info,prometheus,metrics
  endpoint.health.probes.enabled: true
```

暴露 `/actuator/health`、`/actuator/info`、`/actuator/prometheus`。

### 6. HTTP Server + API 规范

- 依赖：`spring-boot-starter-web`，`server.port: 8080`；**JDK 21 虚拟线程开启**（`spring.threads.virtual.enabled: true`），适配 SSE 长连接高并发
- 依赖：`springdoc-openapi-starter-webmvc-ui`（Swagger UI `/swagger-ui.html`，spec `/v3/api-docs`）
- 在 `writing-api` 建：
  - `ApiResponse<T>`：统一响应体（`code` / `message` / `data` / `timestamp`）
  - `GlobalExceptionHandler`（`@RestControllerAdvice`）：异常 → 标准 JSON 错误（`errorCode` / `message` / `timestamp`），覆盖 400 / 404 / 500 / 503
  - REST 约定：资源名词复数、`/api` 前缀、合理 HTTP 状态码

### 7. 开发规范（Google 格式 + 阿里编码规约，两层互补）

职责分开，互不冲突：
- **格式层 — Google**：Spotless + google-java-format，管缩进、import 顺序、空白、换行——`apply` 一键自动修。
- **编码规约层 — 阿里巴巴 Java 开发手册**：通过 **P3C（p3c-pmd ruleset）** 落地，管命名、并发、异常处理、集合、OOP、日志、SQL 等"怎么写才对"的规约。
- **兜底 — Checkstyle**（`google_checks.xml`）+ 根目录 `.editorconfig`。

**格式：Spotless + google-java-format**
```xml
<plugin>
  <groupId>com.diffplug.spotless</groupId>
  <artifactId>spotless-maven-plugin</artifactId>
  <configuration>
    <java>
      <googleJavaFormat><style>GOOGLE</style></googleJavaFormat>
      <removeUnusedImports/>
      <importOrder/>
    </java>
  </configuration>
  <executions><execution><goals><goal>check</goal></goals></execution></executions>
</plugin>
```

**编码规约：阿里 P3C（挂在 PMD 上）**
```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-pmd-plugin</artifactId>
  <configuration>
    <rulesets>
      <ruleset>rulesets/java/ali-pmd.xml</ruleset>
      <ruleset>rulesets/java/ali-concurrent.xml</ruleset>
      <ruleset>rulesets/java/ali-exception.xml</ruleset>
    </rulesets>
  </configuration>
  <dependencies>
    <dependency>
      <groupId>com.alibaba.p3c</groupId>
      <artifactId>p3c-pmd</artifactId>
    </dependency>
  </dependencies>
  <executions><execution><goals><goal>check</goal></goals></execution></executions>
</plugin>
```

> 分工原则：**Google 管「长什么样」（格式），阿里管「怎么写才对」（规约）**，两者职责不同、可并存。
> 若个别风格规则冲突，以 google-java-format 为准（它能自动修，省争论）。

### 8. 代码安全检查

父 pom 加四件套：
- **SpotBugs** + **Find Security Bugs**（findsecbugs 插件，覆盖 OWASP Top 10：SQL 注入、XSS、路径穿越、弱加密、XXE、不安全反序列化等）
- **PMD**（源码层规则）
- **OWASP Dependency-Check**（`dependency-check-maven`，扫第三方依赖已知 CVE，设 `failBuildOnCVSS`）

```xml
<plugin>
  <groupId>com.github.spotbugs</groupId>
  <artifactId>spotbugs-maven-plugin</artifactId>
  <configuration>
    <effort>Max</effort><threshold>Low</threshold>
    <plugins><plugin>
      <groupId>com.h3xstream.findsecbugs</groupId>
      <artifactId>findsecbugs-plugin</artifactId>
    </plugin></plugins>
  </configuration>
</plugin>
```

### 9. CI + pre-commit

- **pre-commit**（本地）：提交前跑 `mvn spotless:check`（或 `spotless:apply`）+ 快速 SpotBugs
- **CI**（GitHub Actions）：`mvn verify` 串起 spotless:check → checkstyle → spotbugs → pmd → dependency-check；任一不过则红，禁止合并

### 10.（可选）前端 Vue3 + Vite 骨架

在项目内建前端目录：Vue3 + Vite + Element Plus + Pinia + Axios（拦截器统一 Token，401 跳登录）+ SSE（EventSource / fetch stream）。
- 页面骨架：登录页、核心工作台（三大写作 Tab）、素材管理、模板管理、稿件历史
- 路由守卫：未登录跳登录页

### 11. 验证

- `mvn clean verify` 全绿
- `mvn -pl writing-start spring-boot:run` 起得来
- MySQL / Redis / ES 连接配置能连通（启动无连接报错，ES 索引可建）
- 访问 `/actuator/health`（UP）、`/actuator/prometheus`（有指标）、`/swagger-ui.html`（能打开）
- 故意写一行不规范代码 → `spotless:check` 报错；故意引一个有 CVE 的旧依赖 → depcheck 报警

---

## 检查清单（Definition of Done）

- [ ] 7 个 Maven 模块骨架建好，`mvn clean package` 出 fat JAR
- [ ] MySQL / Redis / ES7 连接配置就位、能连通，ES 索引 dim=1024
- [ ] 结构化日志（prod 为 JSON，含 traceId），无 `System.out`
- [ ] `/actuator/health` `/info` `/prometheus` 可访问（可选）
- [ ] 虚拟线程开启（`spring.threads.virtual.enabled=true`）
- [ ] springdoc：`/swagger-ui.html` 可打开，统一 `ApiResponse` + `GlobalExceptionHandler` 就位
- [ ] Spotless（Google 格式）+ 阿里 P3C（编码规约）+ Checkstyle + `.editorconfig` 全部生效
- [ ] SpotBugs + Find Security Bugs + PMD + OWASP Dependency-Check 接入 `mvn verify`
- [ ] pre-commit + CI 跑通，任一检查失败即阻断
- [ ] 敏感配置全用 `${ENV_VAR}` 占位（数据库密码 / DashScope key / BGE 模型路径 / Redis 密码），无明文
- [ ]（可选）前端 Vite 骨架跑通，SSE 流式占位就绪

---

## 与 constitution / Spec-Kit 的分工

- **本 skill**：把工程地基"装上"（一次性、可复用、跨模块）
- **constitution**：把硬约束"钉死"（7 模块单向依赖、Agent 无状态、user_id 强制、记忆三层、AgentScope 边界、模型选型…），让 AI 每次都遵守
- **CI + pre-commit**：把检查"强制执行"（机器把关，不靠人自觉）
- **Spec-Kit user story**：地基起好后，再按 SDD 五阶段（US-1 基础底座 → US-2 素材 RAG → US-3 Agent 核心 → US-4 前端 → US-5 联调）逐个开发三类写作能力

> 版本号、插件坐标、`google_checks.xml` 路径等以实施时官方文档为准；本 skill 给的是流程与配置骨架，不锁死具体版本。
