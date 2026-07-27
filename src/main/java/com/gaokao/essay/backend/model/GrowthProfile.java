package com.gaokao.essay.backend.model;

import java.util.List;
import java.util.Map;

public record GrowthProfile(
    int totalFormalGrades,
    String activeEssayType,
    Map<String, EssayTypeProfile> profiles,
    DailyTask dailyTask,
    List<ErrorItem> recentErrors,
    List<MasteryItem> masteryItems
) {

  public record EssayTypeProfile(
      String essayType,
      String state,
      List<TrendPoint> scoreTrend,
      Map<String, List<CapabilityPoint>> capabilityTrends,
      Comparison comparison
  ) {
  }

  public record TrendPoint(String recordId, long createdAt, double score, double maxScore) {
  }

  public record CapabilityPoint(String recordId, long createdAt, String code, double percent) {
  }

  public record Comparison(String headline, List<String> improved, List<String> declined) {
  }

  public record ErrorItem(
      String code,
      String label,
      String status,
      int occurrences,
      int consecutiveOccurrences,
      String evidence,
      String essayType
  ) {
  }

  public record DailyTask(
      String code,
      String title,
      String reason,
      String essayType,
      String route,
      int minutes
  ) {
  }

  public record MasteryItem(String code, String label, String status, long updatedAt) {
  }
}
