# Quickstart: 第 1 节存储层验证清单

**目标**：跑 `mvn verify` + 单测 + 集成测试，验证五项验收点（跨用户隔离 / 逻辑删除 / 审计自动 / ES 同步 / Redis 中间态拒收）。

## 前置条件

- JDK 21
- Maven 3.9+
- MySQL 8 在 localhost:3309（默认配置见 application.yaml，root/root）
- Redis 7 在 localhost:6379
- ES 7.17 在 localhost:9200

> ⚠️ `application.yaml` 中含本机明文中间件配置与 DashScope key（用户自己改的）；**集成测试**只依赖本机中间件，**不依赖**外部 API。

## 跑通流程

### 单测（CI 默认跑）

```bash
# 跑全部单测，跳过 @Tag("integration") 的集成测试
mvn -B -ntp test \
    -Dgroups='!integration' \
  writing-storage
```

预期：5 个测试类全绿

- `WritingMemoryRepositoryTest`（@DataJpaTest，H2 in-memory）
- `ArticleRepositoryTest`（@DataJpaTest）
- `RedisMemoryStoreTest`（@SpringBootTest，依赖本地 Redis）
- `MaterialChunkRepositoryTest`（@Tag("integration"，需本地 ES；CI 跳过）
- `AuditTest`（@DataJpaTest）

### 全量门禁（本地）

```bash
# 包含静态检查（Spotless + Checkstyle + SpotBugs + PMD + P3C + OWASP DepCheck）
mvn -B -ntp verify
```

预期：`BUILD SUCCESS`

### 端到端冒烟（@Tag("integration"），人工）

```bash
# 起中间件（任选）
docker compose up -d mysql redis elasticsearch

# 跑集成测试
mvn -B -ntp test writing-storage -Dgroups='integration'
```

预期：
- `MaterialChunkRepositoryTest.ensureIndex_createsWritingMaterialChunkedWithDim1024Cosine` 绿
- `MaterialChunkRepositoryTest.searchByUser_filtersByUserId` 绿
- `MaterialChunkRepositoryTest.deleteByMaterialId_removesAllChunks` 绿

### 启动后冒烟

```bash
mvn -B -ntp spring-boot:run -pl writing-start
```

预期：
- 启动无报错
- MySQL 自动建 4 张表：`sys_user` / `writing_material` / `writing_article` / `writing_memory`
- ES 自动建索引 `writing_material_chunk`（含 `dim=1024` `cosine` mapping）
- Redis 连接成功

```bash
mysql -h127.0.0.1 -P3309 -uroot -proot wordpilot -e "SHOW TABLES;"
# 预期：4 张表

curl http://127.0.0.1:9200/writing_material_chunk/_mapping?pretty
# 预期：properties.embedding.type = dense_vector, dims = 1024

curl http://127.0.0.1:9200/_cluster/health
# 预期：green 或 yellow（单实例 yellow 正常）
```

## 关键回归断言映射

| 课件断言 | 测试方法名（@DisplayName 保留课件原文） |
|--------|---------------------------------|
| `findByUser` 只回当前用户记忆 | `@DisplayName("按 user_id 查、user_id 隔离；逻辑删除后查不到、历史保留")` → `findByUser_returnsOnlyOwnData_noCrossUser` |
| 逻辑删除后查不到但行保留 | `@DisplayName("逻辑删除后查不到、历史保留")` → `delete_logical_hiddenFromQuery_rowRetained` |
| 分页仅当前用户 | `@DisplayName("分页仅当前用户；逻辑删除生效")` → `findByUserId_returnsOnlyOwnArticlesPaged_logicalDeletedHidden` |
| @CreatedDate 自动填充 | `@DisplayName("@CreatedDate 自动填充 create_time，非手填")` → `audit_autoFillsCreateTimeAndUpdateTime` |
| 会话只存对话层 | `@DisplayName("会话存取 + TTL；只存对话层、中间态不入")` → `append_acceptsUserAssistantOnly_intermediateRejected` |
| ES 检索带 user_id | `@DisplayName("ES 检索带 user_id")` → `searchByUser_filtersByUserId` |
| 删除素材按 material_id 清切片 | `@DisplayName("删除按 material_id 清干净")` → `deleteByMaterialId_removesAllChunks` |

## 剩余人工项

- 实模型 BGE bge-m3：本节不调用 embedding，只保证 dim=1024 mapping 与 bge-m3 对齐
- 真中间件 ES + Redis：集成测试需要本地实 ES / Redis；CI 默认跳过 `@Tag("integration")`
- SSE 冒烟：写作流式输出属于第 3 / 第 5 / 第 8 节，本节无 SSE
- 真用户数据：MySQL 自动建表后，本节不播种任何 demo 用户