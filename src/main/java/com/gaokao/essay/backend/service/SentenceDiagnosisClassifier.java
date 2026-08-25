package com.gaokao.essay.backend.service;

import java.util.Locale;
import java.util.Set;

public class SentenceDiagnosisClassifier {

  private static final String ERROR_CORRECTION = "ERROR_CORRECTION";
  private static final String EXPRESSION_UPGRADE = "EXPRESSION_UPGRADE";
  private static final String NONE = "NONE";
  private static final Set<String> ERROR_TYPES = Set.of(
      "GRAMMAR", "TENSE", "ARTICLE", "SPELLING", "WORD_CHOICE", "PUNCTUATION", "CONTENT"
  );

  public Classification normalize(String kind, String errorType, String diagnosis) {
    String normalizedKind = normalizeToken(kind);
    String normalizedErrorType = normalizeToken(errorType);

    if (EXPRESSION_UPGRADE.equals(normalizedKind)) {
      return new Classification(EXPRESSION_UPGRADE, NONE, false);
    }
    if (ERROR_CORRECTION.equals(normalizedKind)) {
      return ERROR_TYPES.contains(normalizedErrorType)
          ? new Classification(ERROR_CORRECTION, normalizedErrorType, false)
          : Classification.uncounted();
    }
    if (!normalizedKind.isEmpty()) {
      return Classification.uncounted();
    }

    String inferredErrorType = inferLegacyErrorType(diagnosis);
    return inferredErrorType.isEmpty()
        ? Classification.uncounted()
        : new Classification(ERROR_CORRECTION, inferredErrorType, true);
  }

  private String inferLegacyErrorType(String diagnosis) {
    String text = diagnosis == null ? "" : diagnosis.toLowerCase(Locale.ROOT);
    if (containsAny(text, "拼写", "单词拼错", "大小写", "spelling", "misspell", "capitalization")) {
      return "SPELLING";
    }
    if (containsAny(text, "语法", "时态", "主谓一致", "冠词", "介词", "单复数", "句法",
        "grammar", "tense", "subject-verb", "article", "preposition", "plural")) {
      return "GRAMMAR";
    }
    if (containsAny(text, "标点", "punctuation", "comma", "apostrophe")) {
      return "PUNCTUATION";
    }
    if (containsAny(text, "用词", "词汇", "搭配", "word choice", "collocation")) {
      return "WORD_CHOICE";
    }
    if (containsAny(text, "内容", "细节", "空洞", "具体", "要点", "信息不足", "content", "detail")) {
      return "CONTENT";
    }
    return "";
  }

  private boolean containsAny(String text, String... keywords) {
    for (String keyword : keywords) {
      if (text.contains(keyword)) {
        return true;
      }
    }
    return false;
  }

  private String normalizeToken(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }

  public record Classification(String kind, String errorType, boolean legacyInferred) {
    private static Classification uncounted() {
      return new Classification("", "", false);
    }
  }
}
