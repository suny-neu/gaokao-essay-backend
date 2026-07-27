package com.gaokao.essay.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gaokao.essay.backend.store.AppState;
import java.util.List;
import org.junit.jupiter.api.Test;

class GradeScoreDimensionsTest {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void keepsCompleteDimensionScoresWhenTheyMatchTotalScore() throws Exception {
    JsonNode node = objectMapper.readTree("""
        [
          {"code":"content","label":"内容","score":3,"maxScore":5},
          {"code":"language","label":"语言","score":3,"maxScore":5},
          {"code":"structure","label":"结构","score":2,"maxScore":3},
          {"code":"vocabulary","label":"词汇","score":1,"maxScore":2}
        ]
        """);

    List<AppState.ScoreDimension> result = GradeScoreDimensions.parse(node, 9.0);

    assertThat(result).hasSize(4);
    assertThat(result.get(0).code).isEqualTo("content");
    assertThat(result.get(0).score).isEqualTo(3.0);
  }

  @Test
  void rejectsDimensionsWhenSumDoesNotMatchTotalScore() throws Exception {
    JsonNode node = objectMapper.readTree("""
        [
          {"code":"content","label":"内容","score":4,"maxScore":5},
          {"code":"language","label":"语言","score":3,"maxScore":5},
          {"code":"structure","label":"结构","score":2,"maxScore":3},
          {"code":"vocabulary","label":"词汇","score":1,"maxScore":2}
        ]
        """);

    assertThat(GradeScoreDimensions.parse(node, 9.0)).isEmpty();
  }

  @Test
  void rejectsMissingDuplicateOrOutOfRangeDimensions() throws Exception {
    JsonNode missing = objectMapper.readTree("""
        [{"code":"content","score":3,"maxScore":5}]
        """);
    JsonNode duplicate = objectMapper.readTree("""
        [
          {"code":"content","score":3,"maxScore":5},
          {"code":"content","score":3,"maxScore":5},
          {"code":"structure","score":2,"maxScore":3},
          {"code":"vocabulary","score":1,"maxScore":2}
        ]
        """);
    JsonNode outOfRange = objectMapper.readTree("""
        [
          {"code":"content","score":6,"maxScore":5},
          {"code":"language","score":0,"maxScore":5},
          {"code":"structure","score":2,"maxScore":3},
          {"code":"vocabulary","score":1,"maxScore":2}
        ]
        """);

    assertThat(GradeScoreDimensions.parse(missing, 3.0)).isEmpty();
    assertThat(GradeScoreDimensions.parse(duplicate, 9.0)).isEmpty();
    assertThat(GradeScoreDimensions.parse(outOfRange, 9.0)).isEmpty();
  }
}
