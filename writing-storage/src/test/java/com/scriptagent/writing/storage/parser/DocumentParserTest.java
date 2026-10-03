/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.storage.parser;

import com.scriptagent.writing.common.exception.BusinessException;
import com.scriptagent.writing.common.exception.ErrorCode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 文档解析验收：docx / pdf / txt / md 四种解析正确；坏文件 / 不支持格式报错且不落库（不静默产出半成品）. */
class DocumentParserTest {

  private final DocumentParser parser = new DocumentParser();

  @Test
  @DisplayName("docx 用 POI 解析：多段文本以换行连接")
  void parse_docx_returnsParagraphText() throws Exception {
    XWPFDocument doc = new XWPFDocument();
    doc.createParagraph().createRun().setText("第一段");
    doc.createParagraph().createRun().setText("第二段");
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    doc.write(bos);
    doc.close();

    String text = parser.parse("report.docx", new ByteArrayInputStream(bos.toByteArray()));
    assertEquals("第一段\n第二段", text);
  }

  @Test
  @DisplayName("pdf 用 PDFBox 解析：含分页拼接")
  void parse_pdf_returnsTextWithPages() throws Exception {
    byte[] bytes;
    try (PDDocument pdf = new PDDocument()) {
      PDPage page1 = new PDPage();
      pdf.addPage(page1);
      try (PDPageContentStream cs = new PDPageContentStream(pdf, page1)) {
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        cs.newLineAtOffset(50, 700);
        cs.showText("Page One Content");
        cs.endText();
      }
      PDPage page2 = new PDPage();
      pdf.addPage(page2);
      try (PDPageContentStream cs = new PDPageContentStream(pdf, page2)) {
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        cs.newLineAtOffset(50, 700);
        cs.showText("Page Two Content");
        cs.endText();
      }
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      pdf.save(bos);
      bytes = bos.toByteArray();
    }

    String text = parser.parse("guide.pdf", new ByteArrayInputStream(bytes));
    assertTrue(text.contains("Page One Content"));
    assertTrue(text.contains("Page Two Content"));
  }

  @Test
  @DisplayName("txt 直接 UTF-8 读取")
  void parse_txt_returnsContent() {
    String text =
        parser.parse(
            "notes.txt", new ByteArrayInputStream("你好，world".getBytes(StandardCharsets.UTF_8)));
    assertEquals("你好，world", text);
  }

  @Test
  @DisplayName("md 直接 UTF-8 读取")
  void parse_md_returnsContent() {
    String text =
        parser.parse(
            "readme.md", new ByteArrayInputStream("# 标题\n正文".getBytes(StandardCharsets.UTF_8)));
    assertEquals("# 标题\n正文", text);
  }

  @Test
  @DisplayName("不支持格式（如 .doc / .exe）抛 UNSUPPORTED_FORMAT，不产出半成品")
  void parse_unsupportedExtension_throwsUnsupportedFormat() {
    BusinessException e =
        assertThrows(
            BusinessException.class,
            () -> parser.parse("legacy.doc", new ByteArrayInputStream(new byte[] {1, 2, 3})));
    assertEquals(ErrorCode.UNSUPPORTED_FORMAT, e.getErrorCode());
    // .doc 是旧二进制格式，本项目边界为 docx/pdf/txt/md
    assertThrows(
        BusinessException.class,
        () -> parser.parse("setup.exe", new ByteArrayInputStream(new byte[] {1})));
  }

  @Test
  @DisplayName("损坏文件（伪 PDF 后缀）抛 PARSE_FAILED，不静默返回空文本")
  void parse_corruptPdf_throwsParseFailed() {
    BusinessException e =
        assertThrows(
            BusinessException.class,
            () ->
                parser.parse(
                    "broken.pdf",
                    new ByteArrayInputStream("not a pdf at all".getBytes(StandardCharsets.UTF_8))));
    assertEquals(ErrorCode.PARSE_FAILED, e.getErrorCode());
  }
}
