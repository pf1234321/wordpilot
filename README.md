# ScriptAgent（智稿引擎）

> 通用 AI 写作中台 · Java 21 + Spring Boot 3.x + AgentScope2.0 · 部署在企业内网

---

## 项目简介

基于 AgentScope2.0 的**通用 AI 写作中台**，面向内部用户提供三类核心写作能力：

- 一句话多轮对话写作
- 素材文档仿写（RAG）
- 固定模板写作

---

## 技术栈

| 组件 | 选型 |
|------|------|
| 语言 / 运行时 | Java 21 |
| 框架 | Spring Boot 3.3.5 |
| ORM | Spring Data JPA |
| Agent 底座 | AgentScope2.0 |
| 缓存 / 会话 | Redis 7+ |
| 检索引擎 | Elasticsearch 7.17 |
| 数据库 | MySQL 8 |
| 文档解析 | POI（docx）+ PDFBox（pdf） |
| 安全 | BCrypt + UUID Token + 拦截器（手写极简登录，不引入 SpringSecurity） |
| 监控 | Actuator + Micrometer + Prometheus |
| 日志 | SLF4J + Logback + logstash-logback-encoder（JSON） |

---

## 模块结构（7 个 Maven 模块）

```
wordpilot/
├── writing-start        # 启动聚合：主类、依赖聚合、fat JAR
├── writing-api          # 接口层：登录、素材、三类写作、模板、稿件；Token 拦截器
├── writing-business     # 业务编排：写作路由、素材调度、模板渲染、稿件历史
├── writing-agent-core   # Agent 核心：Agent 工厂、三层记忆、ESRetrieveTool、Prompt、SSE
├── writing-storage      # 存储层：JPA Entity/Repository、Redis、文档解析、切片
├── writing-model        # 模型服务：LLM（阿里 Qwen）、Embedding（BGE bge-m3）
└── writing-common       # 公共基础：工具类、加密、UserContext、异常、常量、DTO、枚举
```

**依赖方向（单向、无循环）**：`writing-start → writing-api → writing-business → writing-agent-core / writing-storage / writing-model / writing-common`。

---

## 快速开始

### 1. 前置条件

- JDK 21
- Maven 3.9+
- MySQL 8.x（端口 3306）
- Redis 7.x（端口 6379）
- Elasticsearch 7.17.x（端口 9200）

### 2. 配置环境变量

```bash
export MYSQL_HOST=127.0.0.1
export MYSQL_PORT=3306
export MYSQL_DB=wordpilot
export MYSQL_USER=root
export MYSQL_PASSWORD=your-password

export REDIS_HOST=127.0.0.1
export REDIS_PORT=6379

export ES_URIS=http://127.0.0.1:9200

export DASHSCOPE_API_KEY=your-dashscope-key
export BGE_MODEL_PATH=/path/to/bge-m3
```

### 3. 启动

```bash
mvn clean package -DskipTests
java -jar writing-start/target/wordpilot.jar
```

或开发模式：

```bash
mvn -pl writing-start spring-boot:run
```

启动后访问：

- `http://localhost:8080/api/health` —— 健康检查
- `http://localhost:8080/actuator/health` —— Actuator
- `http://localhost:8080/actuator/prometheus` —— Prometheus 指标
- `http://localhost:8080/swagger-ui.html` —— Swagger UI

---

## 代码规范与质量保障

| 层级 | 工具 | 职责 |
|------|------|------|
| 格式 | Spotless + google-java-format | 缩进 / import / 空白 / 换行 |
| 编码规约 | 阿里 P3C（PMD） | 命名 / 并发 / 异常 / 集合 / OOP / 日志 / SQL |
| 兜底 | Checkstyle（Google checks） | 禁 System.out / 禁 tab / import 顺序 |
| 安全 | SpotBugs + Find Security Bugs | OWASP Top 10 |
| 依赖 | OWASP Dependency-Check | 第三方依赖已知 CVE（CVSS ≥ 7 阻断） |

一键跑全部检查：

```bash
mvn verify
```

自动修格式：

```bash
mvn spotless:apply
```

---

## 实施节奏

按 SDD 五阶段拆 5 个 user story，跑通优先于完美。

| 阶段 | 能力主线 | 验收 Demo |
|------|---------|----------|
| 第一阶段 | 基础底座 | 项目可启动、登录跑通、鉴权生效、自动建表 |
| 第二阶段 | 素材 RAG 底座 | 上传素材可见、ES 可检索切片 |
| 第三阶段 | Agent 核心 | 三种模式都能调用 LLM 产出、SSE 流式 |
| 第四阶段 | 前端工作台 | Vue3 三大 Tab 全流程 |
| 第五阶段 | 联调优化 | 端到端全链路稳定、越权拦截 |

> 本仓库当前处于**第一阶段：工程地基初始化**完成态.

---

## License

Apache License 2.0