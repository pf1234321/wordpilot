# Implementation Plan: 存储层统一封装

**Branch**: `001-storage-layer-foundation` | **Date**: 2026-10-03 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-storage-layer-foundation/spec.md`

## Summary

把 MySQL（JPA）/ Redis / ES7 三层数据访问封装在 `writing-storage` 模块，是这一层仅表达"统一访问 + 强制 user_id + 逻辑删除 + 审计自动"。三层写入提供统一的接口形态（Spring Data Repository / RedisTemplate / RestHighLevelClient），上层业务模块不直接接触 Hibernate Session、不直接写 Redis Key 字面量、不直接拼 ES DSL。

## Technical Context

**Language/Version**: Java 21 (Spring Boot 3.3.5)

**Primary Dependencies**:
- `spring-boot-starter-data-jpa` (Hibernate 6.x) — JPA + MySQL
- `spring-boot-starter-data-redis` (Lettuce) — Redis
- `elasticsearch-rest-high-level-client:7.17.17` — ES 7 Java Client
- `org.springframework.data:spring-data-jpa` — JpaRepository / @DataJpaTest
- `com.mysql:mysql-connector-j:8.4.0` — MySQL JDBC
- `org.mindrot:jbcrypt:0.4` — BCrypt（不引入 SpringSecurity）
- `net.logstash.logback:logstash-logback-encoder:7.4` — JSON 日志（已在 writing-start）

**Storage**:
- MySQL 8 — sys_user / writing_material / writing_article / writing_memory 四表；ddl-auto=update 仅开发环境
- Redis 7 — token:、session:{userId}:{sessionId}、sse:cache:、ratelimit:
- Elasticsearch 7.17 — writing_material_chunk 索引（dim=1024 cosine）

**Testing**:
- `spring-boot-starter-test`（JUnit 5 + Spring TestContext + AssertJ）
- `@DataJpaTest` — JPA Repository 测试（H2 in-memory 隔离）+ 自带 MySQL testcontainer 可选
- `@SpringBootTest` — Redis / ES 集成测试
- `@Tag("integration")` — 集成测试，CI 标记为"no hit"（线上人工跑）
- `mvn verify` 全绿

**Target Platform**: Linux server (内网部署)，JVM 21 虚拟线程（spring.threads.virtual.enabled=true）

**Project Type**: web-service（单体 Spring Boot 启动聚合，writing-start → writing-api → writing-business → writing-agent-core / writing-storage / writing-model / writing-common）

**Performance Goals**:
- 跨用户隔离查询（`findByUser` 单表分页）：p95 < 50ms（开发库 ~500 条/用户，预估）
- ES `knn` topK=20 + user_id filter：p95 < 200ms（1k 切片/用户下）
- 会话 `loadRecent(10)` Redis 拉取：p95 < 10ms

**Constraints**:
- 单向依赖：`writing-start → writing-api → writing-business → writing-agent-core / writing-storage / writing-model / writing-common`
- 所有 MySQL/Redis/ES 连接配置走 `${ENV_VAR}`，明文 key / 密码 / 模型路径 0 处
- `@SQLDelete` + `@Where` 逻辑删除，物理 DELETE SQL 在 Repository 实现中匹配为 0
- ES `dim=1024` 取 `EsIndexConstants.EMBEDDING_DIM` 常量，不允许硬编码

**Scale/Scope**:
- 内网部署，单实例起步；用户量 ~200，预留 50x 增长空间
- 素材切片预估：人均 50 篇 × 20 切片 = 1000 切片 / 用户，10 年 ~2M 切片（ES 单分片够）

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 状态 | 说明 |
|-------|-----|------|
| 原则一：SpringBoot3.x + Java21 单体应用 | ✅ | Spring Boot 3.3.5 + Java 21，模块单向依赖、fat JAR 已验证可打 |
| 原则二：Agent 无状态，状态外置 | ✅ | 本节是存储层落点；上层 AgentCore 通过 storage 帮 Agent 实现"状态外置" |
| 原则三：AgentScope 使用边界；LLM 统一经模型服务 | ✅ | 本节不依赖 AgentScope；不引 LLM；ES dim=1024 与 bge-m3 对齐（cosine） |
| 原则四：极简登录，不用 SpringSecurity | ✅ | BCrypt 用 jbcrypt 库（独立），不引 SpringSecurity |
| 原则五：查询强制携带 user_id | ✅ | Entity / Repository / Service 任何业务查询 MUST 带 user_id；UserContext 是唯一来源；grep 把关时不启用 |
| 原则六：JPA 落地规范 | ✅ | Entity/Repository 放 writing-storage；@CreatedDate 由 AuditEntityListener 自动填；逻辑删除 @SQLDelete + @Where；分页 Pageable |
| 原则七：RAG 管线自实现 | N/A | 本节只交付 MaterialChunkRepository（写入 + 检索 + 清理切片）；解析 / 切片 / Embedding 由第 4 节实现 |
| 原则八：记忆三层分层 | ✅ | 短期 Redis 仅存 user/assistant 对话层（FR-004 + RedisMemoryStore.role 校验）；长期 MySQL `writing_memory` 表（Entity 定义本节，第 5 节接入） |
| 原则九：三类写作能力共享一套 Agent 底座 | N/A | 本节是 Agent 底座之前的存储底座 |
| 原则十：每个 user story 完成后有可演示成果 | ✅ | 本节交付物：5 个测试类全绿 + MySQL 4 表自动建 + Redis 会话可跑 + ES 索引可查 |

## Project Structure

### Documentation (this feature)

```text
specs/001-storage-layer-foundation/
├── plan.md              # 本文件（/speckit-plan 输出）
├── research.md          # Phase 0 输出（/speckit-plan 输出）
├── data-model.md        # Phase 1 输出（/speckit-plan 输出）
├── quickstart.md        # Phase 1 输出（/speckit-plan 输出）
├── contracts/           # Phase 1 输出（/speckit-plan 输出）
└── tasks.md             # Phase 2 输出（/speckit-tasks 输出，本 plan 不创建）
```

### Source Code (repository root)

本节交付物集中在 `writing-storage` 模块（已存在，Java 源目录已建）。

```text
writing-storage/
├── src/main/java/com/scriptagent/writing/storage/
│   ├── entity/                              # 4 个 JPA Entity
│   │   ├── SysUser.java
│   │   ├── WritingMaterial.java
│   │   ├── WritingArticle.java
│   │   └── WritingMemory.java
│   ├── repository/                          # 4 个 Spring Data Repository
│   │   ├── SysUserRepository.java
│   │   ├── WritingMaterialRepository.java
│   │   ├── WritingArticleRepository.java
│   │   └── WritingMemoryRepository.java
│   ├── redis/                               # Redis 封装
│   │   ├── RedisMemoryStore.java            # 短期会话（核心交付）
│   │   └── RedisKeys.java                    # （移到 writing-common 复用；本节在 writing-common 已有 RedisKeys）
│   ├── es/                                  # ES 封装
│   │   ├── MaterialChunkRepository.java     # 切片检索 + 清理
│   │   ├── TemplateRepository.java          # 接口占位（第 6 节实现）
│   │   ├── MaterialChunkIndexInitializer.java # 启动建索引
│   │   └── EsClientConfig.java               # RestHighLevelClient Bean
│   └── package-info.java
└── src/test/java/com/scriptagent/writing/storage/
    ├── entity/                              # entity-level 单测（可选）
    ├── repository/                          # @DataJpaTest
    │   ├── WritingMemoryRepositoryTest.java
    │   ├── ArticleRepositoryTest.java
    │   └── AuditTest.java
    ├── redis/
    │   └── RedisMemoryStoreTest.java
    └── es/
        └── MaterialChunkRepositoryTest.java  # @Tag("integration")
```

writing-common 模块复用现有类（`UserContext`、`AuditEntityListener`、`Auditable`、`RedisKeys`、`EsIndexConstants`），本节不新增 writing-common 代码。

**Structure Decision**: 单体 Spring Boot 7 模块架构；本节交付物集中在 writing-storage 一个模块，**不写 Service / Controller**（属于第 3~8 节业务模块）。

## Complexity Tracking

> 本节无 Constitution 违例需 justify。