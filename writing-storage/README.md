# writing-storage — 存储层

> ScriptAgent 7 个 Maven 模块之一，定位**唯一存储出口**：JPA（MySQL）+ Redis（短期会话）+ Elasticsearch（向量切片）。

---

## 模块职责

| 子包 | 角色 | 关键类 |
|------|------|--------|
| `entity` | 4 张业务表 + 1 张审计基类 | `SysUser`, `WritingMaterial`, `WritingArticle`, `WritingMemory`, `BaseEntity` |
| `repository` | Spring Data JPA Repository，**全部强制 user_id 条件**（`@Query` JPQL + `AND deleted = 0`） | `UserRepository`, `WritingMaterialRepository`, `WritingArticleRepository`, `WritingMemoryRepository` |
| `redis` | 短期会话 Redis 存储 | `RedisMemoryStore`, `SessionMessage`, `SessionRole` |
| `es` | ES 7.17 客户端 + 素材切片仓库 | `EsClientConfig`, `MaterialChunk`, `MaterialChunkRepository`, `MaterialChunkIndexInitializer`, `TemplateRepository`（占位） |
| `parse` | 文档解析（POI/PDFBox/txt/md） | 第 4 节扩展 |
| `chunk` | 切片工具（固定长度+重叠） | 第 4 节扩展 |

---

## 关键约束

| 约束 | 出处 | 实施 |
|------|------|------|
| **所有查询强制 user_id** | constitution 原则五 | Repository `@Query` JPQL 显式 `WHERE user_id=:user_id AND deleted=0`；Spring Data 派生查询在 Hibernate 6 下被 `@SQLRestriction` 拦不住，必须 `@Query` 兜底 |
| **逻辑删除 + 审计** | constitution 原则六 | `@SQLDelete` + `@Where`（Hibernate 6 写作 `@SQLRestriction`）+ `BaseEntity.createdDate/updatedDate` |
| **Embedding 恒 1024 维** | constitution 决策七 | `EsIndexConstants.EMBEDDING_DIM=1024`；ES mapping `dims=1024, similarity=cosine` |
| **敏感配置走环境变量** | constitution H4 不变量③ | `application.yaml` 全 `${ENV_VAR}`，启动期缺则报错 |

---

## 运行前置

| 依赖 | 版本 | 默认地址 |
|------|------|----------|
| JDK | 21 | — |
| Maven | 3.9+ | — |
| MySQL | 8 | `127.0.0.1:3309/wordpilot`（dev profile 自动建表） |
| Redis | 4+ | `127.0.0.1:6379` |
| Elasticsearch | 7.17.17 | `127.0.0.1:9200`（无密码） |

---

## 测试

```bash
# 默认（CI）：跑 4 个 Repository 单测 + AuditTest，跳过 ES/Redis 集成测试
mvn -pl writing-storage clean verify -Dpmd.skip=true -Dspotbugs.skip=true -Ddependency-check.skip=true

# 本地人工：开启集成测试（需真 ES + Redis）
mvn -pl writing-storage test -Dgroups=integration

# 单跑本节测试
mvn -pl writing-storage test -Dgroups='!integration'
```

集成测试标记：`MaterialChunkRepositoryTest` / `RedisMemoryStoreTest` 加 `@Tag("integration")`，CI 通过 `pom.xml` 的 `maven-surefire-plugin <excludedGroups>integration</excludedGroups>` 默认跳过。

---

## 与后续节的契约

| 节 | 拿走的类 |
|----|----------|
| 第 2 节（模型服务） | — |
| 第 3 节（登录鉴权） | `UserRepository`, `SysUser`, `BaseEntity`, `UserContext`（在 writing-common） |
| 第 4 节（素材 RAG） | `MaterialChunkRepository`, `MaterialChunkIndexInitializer`, 文档解析/切片（待建） |
| 第 5 节（Agent 核心） | `RedisMemoryStore`, `WritingMemoryRepository`, `MaterialChunkRepository`（检索） |
| 第 6 节（模板引擎） | `TemplateRepository`（占位，本节建空接口；第 6 节实装） |