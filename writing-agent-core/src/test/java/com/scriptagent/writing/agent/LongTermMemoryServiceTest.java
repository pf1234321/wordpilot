/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.agent;

import com.scriptagent.writing.storage.entity.WritingMemory;
import com.scriptagent.writing.storage.repository.WritingMemoryRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 长期记忆单测：save_memory 写入、按 user_id 加载、user_id 隔离、量大截断（课件 harness）. */
@ExtendWith(MockitoExtension.class)
class LongTermMemoryServiceTest {

  private static final Long USER_A = 1L;
  private static final Long USER_B = 2L;

  @Mock private WritingMemoryRepository repository;

  private LongTermMemoryService service;

  @BeforeEach
  void setUp() {
    service = new LongTermMemoryService(repository);
  }

  @Test
  @DisplayName("save_memory 写入：强制 user_id，memory_type/content/source 落对")
  void save_persistsMemoryWithUserIdAndSource() {
    service.save(USER_A, "style", "喜欢简洁风格", "save_memory");
    ArgumentCaptor<WritingMemory> captor = ArgumentCaptor.forClass(WritingMemory.class);
    verify(repository).save(captor.capture());
    WritingMemory m = captor.getValue();
    assertEquals(USER_A, m.getUserId());
    assertEquals("style", m.getMemoryType());
    assertEquals("喜欢简洁风格", m.getContent());
    assertEquals("save_memory", m.getSource());
  }

  @Test
  void load_returnsContentsForUser() {
    WritingMemory m = new WritingMemory();
    m.setUserId(USER_A);
    m.setContent("喜欢简洁风格");
    when(repository.findByUserId(USER_A)).thenReturn(List.of(m));
    assertEquals(List.of("喜欢简洁风格"), service.load(USER_A));
    verify(repository).findByUserId(USER_A);
  }

  @Test
  @DisplayName("user_id 隔离：B 用户加载不到 A 的记忆（repo 收到的是 B 的 userId）")
  void load_isolatesByUserId() {
    when(repository.findByUserId(USER_B)).thenReturn(List.of());
    assertEquals(List.of(), service.load(USER_B));
    verify(repository).findByUserId(USER_B);
  }

  @Test
  void load_truncatesToMaxWhenOverflow() {
    List<WritingMemory> many = new ArrayList<>();
    for (int i = 0; i < 25; i++) {
      WritingMemory m = new WritingMemory();
      m.setContent("记忆" + i);
      many.add(m);
    }
    when(repository.findByUserId(USER_A)).thenReturn(many);
    assertEquals(20, service.load(USER_A).size()); // 量大按最新截断到上限
  }
}
