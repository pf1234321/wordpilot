/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.api.controller;

import com.scriptagent.writing.api.dto.ApiResponse;
import com.scriptagent.writing.api.dto.MaterialDetail;
import com.scriptagent.writing.api.dto.MaterialListItem;
import com.scriptagent.writing.business.MaterialService;
import com.scriptagent.writing.common.UserContext;
import com.scriptagent.writing.common.exception.BusinessException;
import com.scriptagent.writing.common.exception.ErrorCode;
import com.scriptagent.writing.storage.entity.WritingMaterial;
import com.scriptagent.writing.storage.repository.WritingMaterialRepository;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 素材管理接口验收（全链路联调新增）：上传转 Service；列表/详情 user_id 隔离；越权按不存在. */
class MaterialControllerTest {

  private static final long USER_A = 1L;

  private final MaterialService materialService = mock(MaterialService.class);
  private final WritingMaterialRepository materialRepository =
      mock(WritingMaterialRepository.class);
  private final MaterialController controller =
      new MaterialController(materialService, materialRepository);

  @BeforeEach
  void setUp() {
    UserContext.set(USER_A);
  }

  @AfterEach
  void tearDown() {
    UserContext.clear();
  }

  @Test
  @DisplayName("上传素材：转 Service 并返回素材 id")
  void upload_delegatesToService_returnsId() throws Exception {
    MultipartFile file = mock(MultipartFile.class);
    when(file.isEmpty()).thenReturn(false);
    when(file.getOriginalFilename()).thenReturn("a.txt");
    when(file.getInputStream())
        .thenReturn(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));
    when(materialService.upload(eq("a.txt"), any(InputStream.class))).thenReturn(42L);

    ApiResponse<Long> resp = controller.upload(file);

    assertEquals(42L, resp.data());
    verify(materialService).upload(eq("a.txt"), any(InputStream.class));
  }

  @Test
  @DisplayName("上传空文件拒绝，不进入 Service")
  void upload_emptyFile_rejects() throws Exception {
    MultipartFile file = mock(MultipartFile.class);
    when(file.isEmpty()).thenReturn(true);

    BusinessException e = assertThrows(BusinessException.class, () -> controller.upload(file));

    assertEquals(ErrorCode.PARSE_FAILED, e.getErrorCode());
    verify(materialService, org.mockito.Mockito.never()).upload(any(), any());
  }

  @Test
  @DisplayName("素材列表：按当前用户查询并映射为列表视图（不带正文）")
  void list_mapsToListItems_userScoped() {
    WritingMaterial m = new WritingMaterial();
    m.setId(1L);
    m.setFileName("a.txt");
    m.setFileType("txt");
    m.setFileSize(1024L);
    m.setChunkCount(3);
    m.setCreateTime(LocalDateTime.of(2026, 10, 4, 10, 0));
    Page<WritingMaterial> page = new PageImpl<>(List.of(m));
    when(materialRepository.findByUserId(eq(USER_A), any(Pageable.class))).thenReturn(page);

    ApiResponse<Page<MaterialListItem>> resp = controller.list(0, 10);

    MaterialListItem item = resp.data().getContent().get(0);
    assertEquals(1L, item.id());
    assertEquals("a.txt", item.fileName());
    assertEquals("txt", item.fileType());
    verify(materialRepository).findByUserId(eq(USER_A), any(Pageable.class));
  }

  @Test
  @DisplayName("素材详情：返回含正文的详情（预览）")
  void detail_returnsContent() {
    WritingMaterial m = new WritingMaterial();
    m.setId(5L);
    m.setContentText("素材正文");
    when(materialRepository.findByIdAndUserId(5L, USER_A)).thenReturn(Optional.of(m));

    ApiResponse<MaterialDetail> resp = controller.detail(5L);

    assertEquals("素材正文", resp.data().contentText());
  }

  @Test
  @DisplayName("素材详情：他人素材/不存在统一按不存在处理（防越权探测）")
  void detail_notFoundThrows() {
    when(materialRepository.findByIdAndUserId(9L, USER_A)).thenReturn(Optional.empty());

    BusinessException e = assertThrows(BusinessException.class, () -> controller.detail(9L));

    assertEquals(ErrorCode.MATERIAL_NOT_FOUND, e.getErrorCode());
  }

  @Test
  @DisplayName("删除素材：转 Service（先清 ES 再 MySQL 逻辑删除）")
  void delete_callsService() {
    controller.delete(7L);
    verify(materialService).delete(7L);
  }
}
