package com.gaokao.essay.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SentenceDiagnosisClassifierTest {

  private final SentenceDiagnosisClassifier classifier = new SentenceDiagnosisClassifier();

  @Test
  void preservesExplicitErrorCorrection() {
    assertThat(classifier.normalize("ERROR_CORRECTION", "GRAMMAR", "时态错误").kind())
        .isEqualTo("ERROR_CORRECTION");
  }

  @Test
  void expressionUpgradeCannotCarryAnErrorType() {
    assertThat(classifier.normalize("EXPRESSION_UPGRADE", "GRAMMAR", "更自然").errorType())
        .isEqualTo("NONE");
  }

  @Test
  void infersLegacyKeywordDiagnostics() {
    assertThat(classifier.normalize("", "", "主谓一致错误").legacyInferred()).isTrue();
  }

  @Test
  void leavesUnknownNewKindsUncounted() {
    SentenceDiagnosisClassifier.Classification result = classifier.normalize(
        "FUTURE_KIND",
        "FUTURE_TYPE",
        "主谓一致错误"
    );

    assertThat(result.kind()).isEmpty();
    assertThat(result.errorType()).isEmpty();
    assertThat(result.legacyInferred()).isFalse();
  }
}
