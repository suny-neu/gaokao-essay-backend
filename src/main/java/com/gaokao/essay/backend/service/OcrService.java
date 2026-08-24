package com.gaokao.essay.backend.service;

import com.gaokao.essay.backend.config.GaokaoProperties;
import com.gaokao.essay.backend.model.ApiException;
import com.gaokao.essay.backend.util.TextUtils;
import java.io.IOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class OcrService {

  private static final Pattern PARAGRAPH_ONE_PATTERN = Pattern.compile(
      "^\\s*(?:(?:paragraph|para\\.?)\\s*1|第一段(?:首句)?)\\s*[:：.、-]?\\s*(.*)$",
      Pattern.CASE_INSENSITIVE
  );
  private static final Pattern PARAGRAPH_TWO_PATTERN = Pattern.compile(
      "^\\s*(?:(?:paragraph|para\\.?)\\s*2|第二段(?:首句)?)\\s*[:：.、-]?\\s*(.*)$",
      Pattern.CASE_INSENSITIVE
  );

  private final AiGatewayService aiGatewayService;
  private final ContentSafetyService contentSafetyService;
  private final GaokaoProperties properties;

  public OcrService(
      AiGatewayService aiGatewayService,
      ContentSafetyService contentSafetyService,
      GaokaoProperties properties
  ) {
    this.aiGatewayService = aiGatewayService;
    this.contentSafetyService = contentSafetyService;
    this.properties = properties;
  }

  public boolean isEnabled() {
    return properties.getOcr().isEnabled();
  }

  public boolean isReady() {
    return aiGatewayService.isVisionReady();
  }

  public Map<String, Object> extractText(String openId, MultipartFile file, String scene) {
    if (file == null || file.isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "OCR_FILE_REQUIRED", "请先上传待识别图片");
    }
    try {
      byte[] bytes = file.getBytes();
      validateImageFile(file, bytes);
      String mimeType = resolveMimeType(file, bytes);
      String normalizedScene = normalizeScene(scene);
      String base64 = Base64.getEncoder().encodeToString(bytes);
      String text = aiGatewayService.requestVisionOcr(mimeType, base64, normalizedScene).trim();
      contentSafetyService.verifyOcrText(openId, text);
      Map<String, Object> response = new LinkedHashMap<>();
      response.put("text", text);
      response.put("lineCount", text.isEmpty() ? 0 : text.split("\\R").length);
      response.put("source", "remote");
      response.put("provider", aiGatewayService.getVisionProviderLabel());
      response.put("scene", normalizedScene);
      if ("question".equals(normalizedScene)) {
        response.putAll(splitContinuationQuestion(text));
      }
      return response;
    } catch (IOException error) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "OCR_READ_FAILED", "读取图片失败，请重新选择图片后再试");
    }
  }

  private void validateImageFile(MultipartFile file, byte[] bytes) {
    long maxUploadBytes = Math.max(properties.getSecurity().getMaxUploadBytes(), 1024L * 1024L);
    if (bytes.length > maxUploadBytes) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "OCR_FILE_TOO_LARGE", "图片过大，请压缩到 5MB 以内后再试");
    }

    String mimeType = TextUtils.lower(file.getContentType());
    if (!mimeType.isBlank() && !List.of("image/jpeg", "image/jpg", "image/png", "image/webp").contains(mimeType)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "OCR_FILE_TYPE_INVALID", "只支持 JPG、PNG、WebP 图片");
    }

    if (!looksLikeSupportedImage(bytes)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "OCR_FILE_CONTENT_INVALID", "上传内容不是可识别的图片文件");
    }
  }

  private String resolveMimeType(MultipartFile file, byte[] bytes) {
    String mimeType = TextUtils.lower(file.getContentType());
    if (List.of("image/jpeg", "image/jpg", "image/png", "image/webp").contains(mimeType)) {
      return "image/jpg".equals(mimeType) ? "image/jpeg" : mimeType;
    }
    if (isPng(bytes)) {
      return "image/png";
    }
    if (isWebp(bytes)) {
      return "image/webp";
    }
    return "image/jpeg";
  }

  private String normalizeScene(String scene) {
    String normalized = TextUtils.lower(scene);
    return List.of("task", "source", "draft", "requirements", "question").contains(normalized) ? normalized : "task";
  }

  private Map<String, String> splitContinuationQuestion(String text) {
    Map<String, StringBuilder> sections = new LinkedHashMap<>();
    sections.put("sourceMaterial", new StringBuilder());
    sections.put("paragraphOneStarter", new StringBuilder());
    sections.put("paragraphTwoStarter", new StringBuilder());
    String activeField = "sourceMaterial";
    boolean foundStarter = false;

    for (String line : String.valueOf(text).replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
      Matcher firstMatcher = PARAGRAPH_ONE_PATTERN.matcher(line);
      Matcher secondMatcher = PARAGRAPH_TWO_PATTERN.matcher(line);
      if (firstMatcher.matches()) {
        activeField = "paragraphOneStarter";
        foundStarter = true;
        appendLine(sections.get(activeField), firstMatcher.group(1));
      } else if (secondMatcher.matches()) {
        activeField = "paragraphTwoStarter";
        foundStarter = true;
        appendLine(sections.get(activeField), secondMatcher.group(1));
      } else {
        appendLine(sections.get(activeField), line);
      }
    }

    Map<String, String> result = new LinkedHashMap<>();
    result.put("sourceMaterial", foundStarter ? sections.get("sourceMaterial").toString().trim() : text.trim());
    result.put("paragraphOneStarter", foundStarter ? sections.get("paragraphOneStarter").toString().trim() : "");
    result.put("paragraphTwoStarter", foundStarter ? sections.get("paragraphTwoStarter").toString().trim() : "");
    return result;
  }

  private void appendLine(StringBuilder target, String line) {
    if (target.length() > 0) {
      target.append('\n');
    }
    target.append(line == null ? "" : line);
  }

  private boolean looksLikeSupportedImage(byte[] bytes) {
    return isJpeg(bytes) || isPng(bytes) || isWebp(bytes);
  }

  private boolean isJpeg(byte[] bytes) {
    return bytes.length > 3
        && (bytes[0] & 0xFF) == 0xFF
        && (bytes[1] & 0xFF) == 0xD8
        && (bytes[2] & 0xFF) == 0xFF;
  }

  private boolean isPng(byte[] bytes) {
    return bytes.length > 8
        && (bytes[0] & 0xFF) == 0x89
        && bytes[1] == 0x50
        && bytes[2] == 0x4E
        && bytes[3] == 0x47;
  }

  private boolean isWebp(byte[] bytes) {
    return bytes.length > 12
        && bytes[0] == 0x52
        && bytes[1] == 0x49
        && bytes[2] == 0x46
        && bytes[3] == 0x46
        && bytes[8] == 0x57
        && bytes[9] == 0x45
        && bytes[10] == 0x42
        && bytes[11] == 0x50;
  }
}
