# Repository 接口契约

**目的**：定义本节交付的 4 个 JPA Repository + 1 个 RedisMemoryStore + 2 个 ES Repository 的**对外方法签名**。

> 内部性保证：实现细节（自定义查询 JPQL、@EntityGraph、Pageable 默认值）由 implement 阶段定，**所有对外签名在本契约中固定**。

---

## JPA Repositories

### SysUserRepository

```java
public interface SysUserRepository extends JpaRepository<SysUser, Long> {
    /** 按 username 查唯一用户（username 唯一索引）；返回 Optional 防空. */
    Optional<SysUser> findByUsername(String username);
}
```

### WritingMaterialRepository

```java
public interface WritingMaterialRepository extends JpaRepository<WritingMaterial, Long> {
    /** 列出当前用户的素材（按 createTime DESC）；受 @Where 过滤已删. */
    Page<WritingMaterial> findByUserId(Long userId, Pageable pageable);
    /** 取一条素材；如不存在或不属于 userId，返回 Optional.empty（绝不返回空文集） */
    Optional<WritingMaterial> findByIdAndUserId(Long id, Long userId);
}
```

### WritingArticleRepository

```java
public interface WritingArticleRepository extends JpaRepository<WritingArticle, Long> {
    /** 列出当前用户的稿件（按 createTime DESC）；受 @Where 过滤已删. */
    Page<WritingArticle> findByUserIdOrderByCreateTimeDesc(Long userId, Pageable pageable);
    /** 取一条稿件（强制 userId 匹配） */
    Optional<WritingArticle> findByIdAndUserId(Long id, Long userId);
}
```

### WritingMemoryRepository

```java
public interface WritingMemoryRepository extends JpaRepository<WritingMemory, Long> {
    /** 按 userId 查所有未删记忆 */
    List<WritingMemory> findByUserId(Long userId);
    /** 按 userId + memoryType 查（type = style/term/habit/other） */
    List<WritingMemory> findByUserIdAndMemoryType(Long userId, String memoryType);
}
```

---

## Redis 封装

### RedisMemoryStore（核心交付）

```java
public class RedisMemoryStore {
    /** 追加一条对话层消息；中间态（role 非 USER/ASSISTANT）抛 IllegalArgumentException. */
    void append(Long userId, String sessionId, SessionRole role, String content);

    /** 取最近 maxRounds 条消息；不足返回全部. */
    List<SessionMessage> loadRecent(Long userId, String sessionId, int maxRounds);

    /** 重置 / 续期 TTL（默认 30 分钟，可覆盖） */
    void expire(Long userId, String sessionId, Duration ttl);

    /** 清除整条会话 */
    void clear(Long userId, String sessionId);
}

public enum SessionRole { USER, ASSISTANT }

public record SessionMessage(SessionRole role, String content, Instant timestamp) {}
```

> 命名约定：`session:{userId}:{sessionId}`（RedisKeys 模板）

---

## ES 封装

### MaterialChunkRepository

```java
public interface MaterialChunkRepository {
    /** 启动时检查索引是否存在，不存在则按 mapping 创建（含 dim=1024 cosine） */
    void ensureIndex();

    /** 批量写入素材的所有切片 */
    void saveChunks(Long userId, Long materialId, List<MaterialChunk> chunks);

    /** 按 userId + queryVector 取 topK 个最相似切片 */
    List<MaterialChunk> searchByUser(Long userId, float[] queryVector, int topK);

    /** 按 materialId 清理所有切片（素材删除时调用） */
    void deleteByMaterialId(Long materialId);
}
```

### TemplateRepository（接口占位）

```java
public interface TemplateRepository {
    /** 占位接口；模板 CRUD 由第 6 节实现. */
    // void save(Template template);
    // Optional<Template> findById(Long id);
    // List<Template> findAll();
}
```

---

## Bean 注入清单（preview）

```java
@Configuration
public class EsClientConfig {
    @Bean(destroyMethod = "close")
    public RestHighLevelClient elasticsearchClient(
            @Value("${elasticsearch.uris}") String uris,
            @Value("${elasticsearch.username:}") String username,
            @Value("${elasticsearch.password:}") String password) {
        // 构造 RestHighLevelClient（HTTP/ES，地址从 yml 取；带可选 basic auth）
    }
}

@Component
public class MaterialChunkIndexInitializer implements ApplicationRunner {
    @Override public void run(ApplicationArguments args) {
        materialChunkRepository.ensureIndex();
    }
}
```