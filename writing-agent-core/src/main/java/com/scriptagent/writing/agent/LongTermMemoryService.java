/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.agent;

import com.scriptagent.writing.storage.entity.WritingMemory;
import com.scriptagent.writing.storage.repository.WritingMemoryRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 长期记忆实现（对齐 {@link LongTermMemoryBase}），读写 MySQL {@code writing_memory}，按 user_id 隔离.
 *
 * <p>跨会话记住用户稳定偏好；Agent 经 {@code save_memory} 工具回写（{@link SaveMemoryTool}），系统不自动抽取 （宪法原则八）. 加载量限制
 * {@link #MAX_LOAD} 条，按最新（repo 按 id 倒序）截断，避免撑爆 system prompt.
 */
@Service
public class LongTermMemoryService implements LongTermMemoryBase {

  /** 单次注入 system prompt 的长期记忆条数上限（量大按最新截断）. */
  private static final int MAX_LOAD = 20;

  private final WritingMemoryRepository repository;

  public LongTermMemoryService(WritingMemoryRepository repository) {
    this.repository = repository;
  }

  @Override
  public void save(Long userId, String memoryType, String content, String source) {
    WritingMemory memory = new WritingMemory();
    memory.setUserId(userId);
    memory.setMemoryType(memoryType);
    memory.setContent(content);
    memory.setSource(source);
    repository.save(memory);
  }

  @Override
  public List<String> load(Long userId) {
    List<WritingMemory> all = repository.findByUserId(userId);
    if (all.size() <= MAX_LOAD) {
      return toContents(all);
    }
    // findByUserId 按 id 倒序（最新在前），取最近 MAX_LOAD 条
    return toContents(new ArrayList<>(all.subList(0, MAX_LOAD)));
  }

  private static List<String> toContents(List<WritingMemory> memories) {
    List<String> contents = new ArrayList<>(memories.size());
    for (WritingMemory m : memories) {
      contents.add(m.getContent());
    }
    return contents;
  }
}
