package com.gaokao.essay.backend.service;

import com.gaokao.essay.backend.model.GrowthProfile;
import com.gaokao.essay.backend.repository.EssayRecordRepository;
import com.gaokao.essay.backend.store.AppState;
import com.gaokao.essay.backend.util.TextUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class GrowthProfileService {

  private static final Pattern SCORE_PATTERN =
      Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:分)?\\s*/\\s*(\\d+(?:\\.\\d+)?)");

  private static final List<ErrorRule> ERROR_RULES = List.of(
      rule("task_completion", "内容要点", "内容|要点|遗漏|任务|信息不全|偏题|跑题|覆盖不全"),
      rule("structure_flow", "结构推进", "结构|衔接|过渡|段落|逻辑|推进|段首句|呼应"),
      rule("language_naturalness", "语言自然度", "模板|机器|AI腔|学术腔|生硬|不自然|套话"),
      rule("grammar_accuracy", "语法准确度", "语法|时态|主谓一致|拼写|冠词|搭配|标点"),
      rule("show_not_tell", "细节外显", "show|tell|动作|细节|外显|空泛|情绪词"),
      rule("word_count", "字数控制", "字数|词数|超标|不足|降档"),
      rule("tone_identity", "语气与身份", "语气|身份|称呼|格式|礼貌"),
      rule("continuation_alignment", "续写协同", "协同|线索|回收|首句|前文|闭环")
  );

  private static final Set<String> TRUSTED_ERROR_TYPES = Set.of(
      "GRAMMAR", "TENSE", "ARTICLE", "SPELLING", "WORD_CHOICE", "PUNCTUATION", "CONTENT"
  );

  private final EssayRecordRepository essayRecordRepository;

  public GrowthProfileService(EssayRecordRepository essayRecordRepository) {
    this.essayRecordRepository = essayRecordRepository;
  }

  public GrowthProfile load(String userId, String requestedEssayType) {
    return buildFromRecords(essayRecordRepository.findRecentByUserId(
        userId,
        0,
        50,
        "grade",
        null,
        "SUCCESS"
    ), requestedEssayType);
  }

  GrowthProfile buildFromRecords(List<AppState.EssayRecord> sourceRecords, String requestedEssayType) {
    String activeEssayType = normalizeEssayType(requestedEssayType);
    List<AppState.EssayRecord> records = (sourceRecords == null ? List.<AppState.EssayRecord>of() : sourceRecords).stream()
        .filter(Objects::nonNull)
        .filter(item -> "grade".equalsIgnoreCase(item.mode))
        .filter(item -> "SUCCESS".equalsIgnoreCase(item.taskStatus))
        .sorted(Comparator.comparingLong(item -> item.createdAt))
        .collect(Collectors.toCollection(ArrayList::new));

    Map<String, GrowthProfile.EssayTypeProfile> profiles = new LinkedHashMap<>();
    profiles.put("application", buildEssayTypeProfile("application", records));
    profiles.put("continuation", buildEssayTypeProfile("continuation", records));

    List<AppState.EssayRecord> activeRecords = filterByType(records, activeEssayType);
    List<GrowthProfile.ErrorItem> errors = buildErrors(activeEssayType, activeRecords);
    List<GrowthProfile.MasteryItem> masteryItems = buildMasteryItems(activeRecords);
    GrowthProfile.DailyTask dailyTask = selectDailyTask(
        activeEssayType,
        profiles.get(activeEssayType),
        errors
    );

    return new GrowthProfile(
        records.size(),
        activeEssayType,
        profiles,
        dailyTask,
        errors,
        masteryItems
    );
  }

  private GrowthProfile.EssayTypeProfile buildEssayTypeProfile(
      String essayType,
      List<AppState.EssayRecord> records
  ) {
    List<AppState.EssayRecord> typedRecords = filterByType(records, essayType);
    List<GrowthProfile.TrendPoint> scoreTrend = new ArrayList<>();
    Map<String, List<GrowthProfile.CapabilityPoint>> capabilityTrends = new LinkedHashMap<>();
    capabilityTrends.put("content", new ArrayList<>());
    capabilityTrends.put("language", new ArrayList<>());
    capabilityTrends.put("structure", new ArrayList<>());
    capabilityTrends.put("vocabulary", new ArrayList<>());

    for (AppState.EssayRecord record : typedRecords) {
      parseScore(record.scoreText).ifPresent(score ->
          scoreTrend.add(new GrowthProfile.TrendPoint(
              record.id,
              record.createdAt,
              score.value(),
              score.max()
          ))
      );
      appendCapabilityPoints(record, capabilityTrends);
    }

    String state = scoreTrend.isEmpty()
        ? "EMPTY"
        : scoreTrend.size() == 1 ? "STARTING_POINT" : "TRACKING";
    GrowthProfile.Comparison comparison = buildComparison(essayType, scoreTrend, capabilityTrends);
    return new GrowthProfile.EssayTypeProfile(
        essayType,
        state,
        scoreTrend,
        capabilityTrends,
        comparison
    );
  }

  private List<AppState.EssayRecord> filterByType(
      List<AppState.EssayRecord> records,
      String essayType
  ) {
    return records.stream()
        .filter(item -> essayType.equalsIgnoreCase(item.essayType))
        .collect(Collectors.toCollection(ArrayList::new));
  }

  private void appendCapabilityPoints(
      AppState.EssayRecord record,
      Map<String, List<GrowthProfile.CapabilityPoint>> target
  ) {
    if (record.analysis == null || record.analysis.scoreDimensions == null) {
      return;
    }
    for (AppState.ScoreDimension dimension : record.analysis.scoreDimensions) {
      if (dimension == null
          || !target.containsKey(dimension.code)
          || !Double.isFinite(dimension.score)
          || !Double.isFinite(dimension.maxScore)
          || dimension.maxScore <= 0
          || dimension.score < 0
          || dimension.score > dimension.maxScore) {
        continue;
      }
      double percent = roundOneDecimal(dimension.score / dimension.maxScore * 100);
      target.get(dimension.code).add(new GrowthProfile.CapabilityPoint(
          record.id,
          record.createdAt,
          dimension.code,
          percent
      ));
    }
  }

  private GrowthProfile.Comparison buildComparison(
      String essayType,
      List<GrowthProfile.TrendPoint> scoreTrend,
      Map<String, List<GrowthProfile.CapabilityPoint>> capabilityTrends
  ) {
    String label = "continuation".equals(essayType) ? "读后续写" : "应用文";
    if (scoreTrend.isEmpty()) {
      return new GrowthProfile.Comparison(
          "完成第一次正式批改后，这里会形成成长起点",
          List.of(),
          List.of()
      );
    }
    if (scoreTrend.size() == 1) {
      return new GrowthProfile.Comparison(
          "这是你的" + label + "成长起点",
          List.of(),
          List.of()
      );
    }

    GrowthProfile.TrendPoint previous = scoreTrend.get(scoreTrend.size() - 2);
    GrowthProfile.TrendPoint latest = scoreTrend.get(scoreTrend.size() - 1);
    double previousPercent = previous.score() / previous.maxScore() * 100;
    double latestPercent = latest.score() / latest.maxScore() * 100;
    double delta = roundOneDecimal(latestPercent - previousPercent);
    String headline = delta > 0
        ? "比上次提高 " + formatNumber(delta) + " 个百分点"
        : delta < 0
            ? "比上次下降 " + formatNumber(Math.abs(delta)) + " 个百分点"
            : "与上次得分持平";

    List<String> improved = new ArrayList<>();
    List<String> declined = new ArrayList<>();
    for (Map.Entry<String, List<GrowthProfile.CapabilityPoint>> entry : capabilityTrends.entrySet()) {
      List<GrowthProfile.CapabilityPoint> points = entry.getValue();
      if (points.size() < 2) {
        continue;
      }
      double capabilityDelta =
          points.get(points.size() - 1).percent() - points.get(points.size() - 2).percent();
      if (capabilityDelta > 0.01) {
        improved.add(capabilityLabel(entry.getKey()) + " +" + formatNumber(capabilityDelta) + "%");
      } else if (capabilityDelta < -0.01) {
        declined.add(capabilityLabel(entry.getKey()) + " " + formatNumber(capabilityDelta) + "%");
      }
    }
    return new GrowthProfile.Comparison(headline, improved, declined);
  }

  private List<GrowthProfile.ErrorItem> buildErrors(
      String essayType,
      List<AppState.EssayRecord> records
  ) {
    List<GrowthProfile.ErrorItem> result = new ArrayList<>();
    for (ErrorRule rule : ERROR_RULES) {
      int occurrences = 0;
      int consecutive = 0;
      String evidence = "";

      for (AppState.EssayRecord record : records) {
        String recordEvidence = buildGrowthErrorEvidence(record.analysis);
        if (rule.pattern().matcher(recordEvidence).find()) {
          occurrences += 1;
          evidence = recordEvidence;
        }
      }
      for (int index = records.size() - 1; index >= 0; index -= 1) {
        String recordEvidence = buildGrowthErrorEvidence(records.get(index).analysis);
        if (!rule.pattern().matcher(recordEvidence).find()) {
          break;
        }
        consecutive += 1;
      }

      if (occurrences > 0) {
        result.add(new GrowthProfile.ErrorItem(
            rule.code(),
            rule.label(),
            consecutive >= 2 || occurrences >= 3 ? "REPEATED" : "NEW",
            occurrences,
            consecutive,
            abbreviateEvidence(evidence),
            essayType
        ));
      }
    }
    result.sort(Comparator
        .comparingInt(GrowthProfile.ErrorItem::consecutiveOccurrences).reversed()
        .thenComparing(Comparator.comparingInt(GrowthProfile.ErrorItem::occurrences).reversed()));
    return result;
  }

  private List<GrowthProfile.MasteryItem> buildMasteryItems(List<AppState.EssayRecord> records) {
    if (records.size() < 4) {
      return List.of();
    }
    List<AppState.EssayRecord> latestThree = records.subList(records.size() - 3, records.size());
    List<GrowthProfile.MasteryItem> result = new ArrayList<>();
    for (ErrorRule rule : ERROR_RULES) {
      boolean appearedBefore = records.subList(0, records.size() - 3).stream()
          .anyMatch(item -> rule.pattern().matcher(buildGrowthErrorEvidence(item.analysis)).find());
      boolean absentRecently = latestThree.stream()
          .noneMatch(item -> rule.pattern().matcher(buildGrowthErrorEvidence(item.analysis)).find());
      if (appearedBefore && absentRecently) {
        result.add(new GrowthProfile.MasteryItem(
            rule.code(),
            rule.label(),
            "MASTERED",
            latestThree.get(latestThree.size() - 1).createdAt
        ));
      }
    }
    return result;
  }

  private GrowthProfile.DailyTask selectDailyTask(
      String essayType,
      GrowthProfile.EssayTypeProfile profile,
      List<GrowthProfile.ErrorItem> errors
  ) {
    Optional<GrowthProfile.ErrorItem> repeated = errors.stream()
        .filter(item -> "REPEATED".equals(item.status()))
        .findFirst();
    if (repeated.isPresent()) {
      GrowthProfile.ErrorItem item = repeated.get();
      return new GrowthProfile.DailyTask(
          item.code(),
          "今天只练：" + item.label(),
          "这个问题已连续出现 " + item.consecutiveOccurrences() + " 篇，先把它改稳。",
          essayType,
          "/pages/tutor/index?type=" + essayType + "&focus=" + item.code(),
          10
      );
    }

    String lowestCapability = findLowestCapability(profile.capabilityTrends());
    if (!TextUtils.isBlank(lowestCapability)) {
      return new GrowthProfile.DailyTask(
          lowestCapability,
          "今天只练：" + capabilityLabel(lowestCapability),
          "这是当前四项能力中最需要优先巩固的一项。",
          essayType,
          "/pages/tutor/index?type=" + essayType + "&focus=" + lowestCapability,
          10
      );
    }

    return new GrowthProfile.DailyTask(
        "foundation",
        "先完成第一次正式批改",
        "有了第一篇真实报告，系统才能为你建立个人成长起点。",
        essayType,
        "/pages/write/index?mode=grade&type=" + essayType,
        10
    );
  }

  private String findLowestCapability(
      Map<String, List<GrowthProfile.CapabilityPoint>> capabilityTrends
  ) {
    String lowestCode = "";
    double lowestValue = Double.POSITIVE_INFINITY;
    for (Map.Entry<String, List<GrowthProfile.CapabilityPoint>> entry : capabilityTrends.entrySet()) {
      List<GrowthProfile.CapabilityPoint> points = entry.getValue();
      if (points.isEmpty()) {
        continue;
      }
      double latest = points.get(points.size() - 1).percent();
      if (latest < lowestValue) {
        lowestValue = latest;
        lowestCode = entry.getKey();
      }
    }
    return lowestCode;
  }

  private Optional<ScoreValue> parseScore(String scoreText) {
    Matcher matcher = SCORE_PATTERN.matcher(TextUtils.trimToEmpty(scoreText));
    if (!matcher.find()) {
      return Optional.empty();
    }
    double value = Double.parseDouble(matcher.group(1));
    double max = Double.parseDouble(matcher.group(2));
    if (!Double.isFinite(value) || !Double.isFinite(max) || max <= 0 || value < 0 || value > max) {
      return Optional.empty();
    }
    return Optional.of(new ScoreValue(value, max));
  }

  private String buildGrowthErrorEvidence(AppState.GradeAnalysis analysis) {
    if (analysis == null || analysis.sentenceDiagnostics == null) {
      return "";
    }
    return analysis.sentenceDiagnostics.stream()
        .filter(this::isTrustedErrorCorrection)
        .map(this::growthErrorEvidence)
        .collect(Collectors.joining("\n"));
  }

  private boolean isTrustedErrorCorrection(AppState.SentenceDiagnosis diagnosis) {
    return diagnosis != null
        && "ERROR_CORRECTION".equals(diagnosis.kind)
        && !diagnosis.legacyInferred
        && TRUSTED_ERROR_TYPES.contains(diagnosis.errorType);
  }

  private String growthErrorEvidence(AppState.SentenceDiagnosis diagnosis) {
    return switch (diagnosis.errorType) {
      case "GRAMMAR" -> "语法";
      case "TENSE" -> "时态";
      case "ARTICLE" -> "冠词";
      case "SPELLING" -> "拼写";
      case "WORD_CHOICE" -> "搭配";
      case "PUNCTUATION" -> "标点";
      case "CONTENT" -> "内容";
      default -> "";
    };
  }

  private String abbreviateEvidence(String evidence) {
    String normalized = TextUtils.trimToEmpty(evidence).replaceAll("\\s+", " ");
    return normalized.length() <= 120 ? normalized : normalized.substring(0, 120) + "…";
  }

  private String normalizeEssayType(String essayType) {
    return "continuation".equalsIgnoreCase(essayType) ? "continuation" : "application";
  }

  private String capabilityLabel(String code) {
    return switch (code) {
      case "content" -> "内容";
      case "language" -> "语言";
      case "structure" -> "结构";
      case "vocabulary" -> "词汇";
      default -> code;
    };
  }

  private String formatNumber(double value) {
    if (Math.abs(value - Math.rint(value)) < 0.000001) {
      return String.valueOf((long) Math.rint(value));
    }
    return String.valueOf(roundOneDecimal(value));
  }

  private double roundOneDecimal(double value) {
    return Math.round(value * 10.0) / 10.0;
  }

  private static ErrorRule rule(String code, String label, String expression) {
    return new ErrorRule(code, label, Pattern.compile(expression, Pattern.CASE_INSENSITIVE));
  }

  private record ScoreValue(double value, double max) {
  }

  private record ErrorRule(String code, String label, Pattern pattern) {
  }
}
