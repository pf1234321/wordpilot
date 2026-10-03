/* Copyright (c) 2026 ScriptAgent. Licensed under Apache License 2.0. */
package com.scriptagent.writing.storage.parser;

import com.scriptagent.writing.common.exception.BusinessException;
import com.scriptagent.writing.common.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;

/**
 * 素材文档解析器：按扩展名分发解析 docx / pdf / txt / md 为纯文本.
 *
 * <p>docx 用 POI（段落 {@code \n} 连接）、pdf 用 PDFBox（含分页拼接）、txt/md 直接 UTF-8 读取；不支持格式抛 {@link
 * ErrorCode#UNSUPPORTED_FORMAT}，损坏 / 无法解析的文件抛 {@link ErrorCode#PARSE_FAILED}（课件坑点：不静默产出 半成品空文本）.
 *
 * <p>说明：课件示例用增强 switch（{@code default ->}），P3C/ASM 静态门禁解析不了该形态，本实现改用传统 switch 多 case
 * 落空，语义与课件一致（按扩展名分发到不同解析器）.
 */
@Component
public class DocumentParser {

  /**
   * 解析为纯文本.
   *
   * @param fileName 原始文件名（含扩展名，大小写不敏感）
   * @param in 文件输入流（docx/pdf 需可读完整字节）
   * @return 解析后的纯文本
   */
  public String parse(String fileName, InputStream in) {
    String ext = extensionOf(fileName);
    try {
      switch (ext) {
        case "docx":
          return parseDocx(in);
        case "pdf":
          return parsePdf(in);
        case "txt":
        case "md":
          return parseText(in);
        default:
          throw new BusinessException(ErrorCode.UNSUPPORTED_FORMAT);
      }
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw new BusinessException(ErrorCode.PARSE_FAILED, e.getMessage());
    }
  }

  private String parseDocx(InputStream in) throws IOException {
    try (XWPFDocument doc = new XWPFDocument(in)) {
      StringBuilder sb = new StringBuilder();
      for (XWPFParagraph p : doc.getParagraphs()) {
        if (sb.length() > 0) {
          sb.append('\n');
        }
        sb.append(p.getText());
      }
      return sb.toString();
    }
  }

  private String parsePdf(InputStream in) throws IOException {
    // PDFBox 3.x Loader.loadPDF 接受 byte[]/File/RandomAccessRead；素材文件受 max-size-mb 限制，一次性读入可接受
    try (PDDocument pdf = Loader.loadPDF(in.readAllBytes())) {
      PDFTextStripper stripper = new PDFTextStripper();
      stripper.setSortByPosition(true);
      // PDFTextStripper 默认遍历全部页并拼接文本（含分页换行），即"分页拼接"
      return stripper.getText(pdf);
    }
  }

  private String parseText(InputStream in) throws IOException {
    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
  }

  /** 提取小写扩展名；无扩展名 / 以 . 结尾返回空串（落入不支持分支）. */
  private static String extensionOf(String fileName) {
    if (fileName == null) {
      return "";
    }
    int dot = fileName.lastIndexOf('.');
    if (dot < 0 || dot == fileName.length() - 1) {
      return "";
    }
    return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
  }
}
