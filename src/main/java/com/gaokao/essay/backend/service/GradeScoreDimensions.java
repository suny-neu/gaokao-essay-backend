package com.gaokao.essay.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.gaokao.essay.backend.store.AppState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class GradeScoreDimensions {
  private static final Map<String, Definition> DEFINITIONS = new LinkedHashMap<>();

  static {
    DEFINITIONS.put("content", new Definition("内容", 5.0));
    DEFINITIONS.put("language", new Definition("语言", 5.0));
    DEFINITIONS.put("structure", new Definition("结构", 3.0));
    DEFINITIONS.put("vocabulary", new Definition("词汇", 2.0));
  }

  private GradeScoreDimensions() {
  }

  static List<AppState.ScoreDimension> parse(JsonNode node, double expectedTotal) {
    if (node == null || !node.isArray()) {
      return List.of();
    }
    Map<String, AppState.ScoreDimension> parsed = new LinkedHashMap<>();
    for (JsonNode item : node) {
      String code = item.path("code").asText("");
      Definition definition = DEFINITIONS.get(code);
      double score = item.path("score").asDouble(Double.NaN);
      double maxScore = item.path("maxScore").asDouble(Double.NaN);
      if (definition == null
          || parsed.containsKey(code)
          || !Double.isFinite(score)
          || !Double.isFinite(maxScore)
          || Math.abs(maxScore - definition.maxScore) > 0.001
          || score < 0
          || score > maxScore) {
        return List.of();
      }
      AppState.ScoreDimension dimension = new AppState.ScoreDimension();
      dimension.code = code;
      dimension.label = definition.label;
      dimension.score = score;
      dimension.maxScore = maxScore;
      parsed.put(code, dimension);
    }
    if (parsed.size() != DEFINITIONS.size() || !parsed.keySet().containsAll(DEFINITIONS.keySet())) {
      return List.of();
    }
    double total = parsed.values().stream().mapToDouble(item -> item.score).sum();
    if (!Double.isFinite(expectedTotal) || Math.abs(total - expectedTotal) > 0.01) {
      return List.of();
    }
    List<AppState.ScoreDimension> result = new ArrayList<>();
    DEFINITIONS.keySet().forEach(code -> result.add(parsed.get(code)));
    return result;
  }

  private static final class Definition {
    private final String label;
    private final double maxScore;

    private Definition(String label, double maxScore) {
      this.label = label;
      this.maxScore = maxScore;
    }
  }
}
