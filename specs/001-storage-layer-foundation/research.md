# Phase 0 Research: 存储层技术选型

**目的**：解决 spec 中提到但需要在 plan 阶段定型的实现层选择。所有决定已在 spec 边界内，不创建 spec 外概念。

## Decision 1：ES 7 Java 客户端 = `RestHighLevelClient`

- **Decision**：使用 `org.elasticsearch.client.RestHighLevelClient`（已在父 pom `dependencyManagement` 锁定 `7.17.17`，与 ES7.17 服务端对齐）。
- **Rationale**：
  - 项目要求 ES7.17 服务端（constitution 决策七 + 风险表）；rest-high-level-client 与 7.x 服务端二进制兼容
  - 不引 `spring-data-elasticsearch`（避免 spring-data 双数据源混乱；spring-data-jpa 与 spring-data-elasticsearch 是两套）
  - 不升 8.x 客户端（与 7.x 服务端不兼容）
- **Alternatives considered**：
  - spring-data-elasticsearch —— 与 spring-data-jpa 命名空间冲突，复杂度过高
  - ES Java API Client（8.x 风格）—— 与 7.x 服务端不兼容
  - 直接 RestTemplate 调 ES HTTP —— 太原始

## Decision 2：ES 索引 `writing_material_chunk` 启动时自动创建

- **Decision**：`MaterialChunkIndexInitializer` 实现 `ApplicationRunner`，Spring 启动时检查索引是否存在，不存在则按 mapping 创建（含 `dim=1024` `dense_vector` + `cosine`）。
- **Rationale**：
  - 内网部署，单实例启动一次，无须按需懒创建
  - 启动时 fail-fast 比首次写入时报错更早暴露问题
  - 测试 setup 也走同一个 Initializer，集成测试不需手动建索引
- **Alternatives considered**：
  - 首次写入懒创建 —— 失败点延后，不利于早期诊断
  - 手动 SQL 脚本 —— 增加运维负担，与"内网一键启动"目标不一致

## Decision 3：ES mapping 字段

```
{
  "properties": {
    "id":          { "type": "long" },
    "material_id": { "type": "long" },
    "user_id":     { "type": "long" },
    "chunk_index": { "type": "integer" },
    "chunk_text":  { "type": "text" },
    "embedding":   {
      "type": "dense_vector",
      "dims": 1024           // 取 EsIndexConstants.EMBEDDING_DIM
    }
  }
}
```

- **Rationale**：dim 必须恒等于 `EsIndexConstants.EMBEDDING_DIM = 1024`（bge-m3 对齐），similarity 默认 cosine（与 ES 默认 dense_vector 一致）。

## Decision 4：Redis 会话 value = JSON 字符串（Redis String 类型）

- **Decision**：`session:{userId}:{sessionId}` 的 value 是 JSON 字符串，包含整条消息数组；`append` 用 `get + 修改 + set` 写回（带 TTL）；不直接用 Redis List 原生。
- **Rationale**：
  - JSON 序列化便于调试（`redis-cli get` 可看全消息）+ 跨语言可读
  - 默认 max-rounds=10（memory.short-term.max-rounds），单条会话字节数 < 50KB，get/set 性能足够
  - loadRecent 顺序从 JSON 数组末段取，简单且不依赖 Redis List 命令边界
- **Alternatives considered**：
  - Redis List 原生（lpush + lrange）—— 写入性能略好，但中间态拒收、role 校验需要序列化层；放弃

## Decision 6：Redis 会话 role 枚举 = `SessionRole { USER, ASSISTANT }`

- **Decision**：枚举 `SessionRole`，`RedisMemoryStore.append` 接受 `SessionRole`，非法枚举值在编译期就拒绝；中间态（THINK / TOOL 等）从枚举中**不存在**，物理上无法传入。
- **Rationale**：
  - 用枚举比 String 校验更稳（编译期 vs 运行期）
  - 不需要运行时"中间态拒收"——因为枚举就只有 USER/ASSISTANT 两种
  - 如果将来要加 TOOL role，让枚举显式列出 + 加白名单注释

## Decision 7：会话级 TTL 默认 = 30 分钟（application.yaml 已对齐）

- **Decision**：`session:{userId}:{sessionId}` 默认 TTL = 30 分钟；可通过 application.yaml `memory.short-term.ttl-minutes` 覆盖。
- **Rationale**：
  - 内部用户写作中台，30 分钟会话窗口覆盖大多数多轮对话场景
  - 短 TTL + 长期记忆回写（save_memory 工具）保证上下文不丢

## Decision 8：SysUser 字段裁剪

- **Decision**：`SysUser` 仅保留 `id, password (BCrypt hashed), nickname, createTime, updateTime, deleted`。**无 `is_active`** 字段，账号激活/禁用复用 `deleted`。
- **Rationale**：
  - 第 3 节登录鉴权只问"账号存在 + 密码对"，不需要 `is_active` 二态
  - 减少字段 = 减少迁移脚本 + 减少误用
  - 将来要加"账号禁用 / 封禁"再独立加字段（如 `disabled_at` timestamp）
- **Alternatives considered**：
  - `is_active` boolean —— 与 deleted 语义重叠，引入歧义

## Decision 9：WritingArticle 排序 = `createTime DESC`

- **Decision**：分页默认按 `createTime DESC`（最新稿件在前），写 `findByUserOrderByCreateTimeDesc`。
- **Rationale**：用户稿件列表 = 时间线，最新的优先
- **Alternatives considered**：
  - `updateTime DESC` —— 与"稿件列表"语义不一致（编辑过的旧稿会跳到顶）
  - 客户端排序 —— 加权客户端复杂度

## Decision 10：测试数据库 = H2 in-memory（@DataJpaTest 默认）

- **Decision**：`@DataJpaTest` 用 Spring Boot 默认 H2 in-memory（jpa 模式兼容 MySQL）；Redis / ES 集成测试用 `@SpringBootTest` 配本地中间件，打 `@Tag("integration")`，CI 默认跳过。
- **Rationale**：
  - H2 与 MySQL dialect 接近（用 `MySQLDialect`），自动建表验证 + 逻辑删除语义与 MySQL 一致
  - `@DataJpaTest` 默认走 H2，速度快（单测 < 1s / 类）
  - ES 集成测试本地需启 ES，太慢不适合 CI 默认；打 tag 让人工跑
- **必须验证**：H2 是否与 MySQL 在 `@SQLDelete` + `@Where` 注解行为上一致 —— 验证 H2 supports 软删除语法（已确认 yes）

## Decision 11：Hibernate 6.x `@SQLDelete` + `@Where` 是否被 P3C / SpotBugs 误报

- **Decision**：使用标准 Hibernate 注解 `@SQLDelete(sql = "...")` + `@Where(clause = "...")`；已知 P3C / SpotBugs 不报这两个注解。

## Decision 12：MaterialChunk ES 文档 ID 策略

- **Decision**：ES doc id = `"{materialId}_{chunkIndex}"`（字符串拼接），便于按 `materialId` 前缀清理（用 wildcard query：`DELETE WHERE _id LIKE 'materialId_*'`）。
- **Rationale**：
  - 单素材多切片天然按 materialId 维度聚集
  - 清理时一次 wildcard + bulk delete 比"先 query 再 delete"少一次 round-trip