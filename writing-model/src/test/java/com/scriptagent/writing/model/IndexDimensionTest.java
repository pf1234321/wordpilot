/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.model;

import com.scriptagent.writing.common.constants.EsIndexConstants;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** ES 索引维度契约校验：素材切片索引 embedding 维度恒 1024、similarity=cosine，引用常量（禁止硬编码）. */
class IndexDimensionTest {

  @Test
  @DisplayName("writing_material_chunk 索引 embedding dim=1024、检索 cosine，引用常量")
  void esIndex_dim1024_cosine_matchesConstants() {
    assertThat(EsIndexConstants.EMBEDDING_DIM).isEqualTo(1024);
    assertThat(EsIndexConstants.SIMILARITY).isEqualTo("cosine");
    assertThat(EsIndexConstants.INDEX_MATERIAL_CHUNK).isEqualTo("writing_material_chunk");
  }

  @Test
  @DisplayName("存储端索引 mapping 引用 EMBEDDING_DIM/SIMILARITY 常量，禁止硬编码 dims=1024")
  void storageIndexMapping_referencesConstants_notHardcoded() throws IOException {
    Path repo =
        Path.of(
            "..",
            "writing-storage",
            "src",
            "main",
            "java",
            "com",
            "scriptagent",
            "writing",
            "storage",
            "es",
            "MaterialChunkRepository.java");
    String src = Files.readString(repo);
    assertThat(src).contains("EMBEDDING_DIM");
    assertThat(src).contains("SIMILARITY");
    assertThat(src).doesNotContain("\"dims\":1024");
  }
}
