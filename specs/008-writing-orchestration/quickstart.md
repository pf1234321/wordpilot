# Quickstart: 三类写作编排（008）验证指南

本节为写作编排 api/business 层，harness 全部可单测 mock（秒级，不依赖中间件）。集成冒烟标 `@Tag("integration")` CI 跳过。

## 前置

- 前序节已交付：`AgentFactory`/`WritingAgent`/`StreamOutput`（第 5 节）、`TemplateService`/`TemplateRepository`（第 6 节）、`WritingArticle`/`WritingArticleRepository`（第 1 节）、`UserContext`/`RedisKeys`（第 3 节）、POI（writing-storage 传递）。
- 构建门禁：`mvn clean verify`（P3C/SpotBugs/FindSecBugs/PMD/Checkstyle/Spotless 全绿）。

## 验证命令与预期

### 1. 只跑本节测试

```bash
mvn -pl writing-api,writing-business -am test
```

预期：`SseStreamingServiceTest`、`WritingServiceTest`、`ArticleServiceTest` 全绿，且方法名全英文（课件原文进 `@DisplayName`）。

### 2. 关键回归单测（对号）

```bash
# 仿写必须挂检索工具（参考素材不漏注入）
mvn -pl writing-api test -Dtest=WritingServiceTest#route_rag_injectsEsRetrieveTool
# 稿件保存带 user_id + write_type（统一落稿入口）
mvn -pl writing-business test -Dtest=ArticleServiceTest#save_carriesUserIdAndWriteType
# SSE 逐段推送 + 写 Redis 缓存（断线恢复）
mvn -pl writing-api test -Dtest=SseStreamingServiceTest#stream_pushToken_sendsSseAndWritesCache
```

### 3. 全量门禁

```bash
mvn clean verify
```

预期：全模块全绿。

### 4. 依赖方向核对

```bash
# writing-api → writing-business（传递 agent-core/model/storage/common）；无循环
grep -rn "artifactId" writing-api/pom.xml
```

预期：writing-api 仅直接依赖 writing-common + writing-business；SseStreamingService 在 writing-api（agent-core 不依赖 spring-web）。

## 人工验收项（harness 之外，需真模型 / 真中间件）

1. 前端三 Tab 分别走通对话 / 仿写 / 模板，SSE 实时渲染（需启动应用 + 真 LLM）。
2. 仿写时确认 ES 检索到参考素材并注入（真 ES + Embedding）。
3. 稿件历史只显示当前用户记录，且标注写作文式（真 MySQL）。
4. 断网重连后，SSE 内容能从 Redis 缓存恢复（真 Redis）。
