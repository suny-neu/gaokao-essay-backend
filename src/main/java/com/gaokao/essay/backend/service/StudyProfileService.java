package com.gaokao.essay.backend.service;

import com.gaokao.essay.backend.repository.EssayRecordRepository;
import com.gaokao.essay.backend.store.AppState;
import com.gaokao.essay.backend.util.TextUtils;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class StudyProfileService {

  private static final DateTimeFormatter DISPLAY_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Shanghai"));

  private static final List<WeaknessRule> WEAKNESS_RULES = List.of(
      createRule("task_completion", "内容要点", "要点|遗漏|任务|信息不全|偏题|跑题|覆盖不全"),
      createRule("structure_flow", "结构推进", "结构|衔接|过渡|段落|逻辑|推进|段首句|呼应"),
      createRule("language_naturalness", "语言自然度", "模板|机器|AI腔|学术腔|生硬|不自然|套话"),
      createRule("grammar_accuracy", "语法准确度", "语法|时态|主谓一致|拼写|冠词|搭配"),
      createRule("show_not_tell", "细节外显", "show|tell|动作|细节|外显|空泛|情绪词"),
      createRule("word_count", "字数控制", "字数|词数|超标|不足|降档"),
      createRule("tone_identity", "语气与身份", "语气|身份|称呼|格式|礼貌"),
      createRule("continuation_alignment", "续写协同", "协同|线索|回收|首句|前文|闭环")
  );

  private final EssayRecordRepository essayRecordRepository;

  public StudyProfileService(EssayRecordRepository essayRecordRepository) {
    this.essayRecordRepository = essayRecordRepository;
  }

  public Map<String, Object> buildStudyProfile(String userId) {
    List<AppState.EssayRecord> gradeHistory = loadRecentGradeRecords(userId);
    AppState.EssayRecord latestGradeRecord = gradeHistory.isEmpty() ? null : gradeHistory.get(0);
    List<AppState.EssayRecord> analysisHistory = extractAnalysisHistory(gradeHistory);

    if (analysisHistory.isEmpty()) {
      return latestGradeRecord == null
          ? buildEmptyProfile()
          : buildPendingProfile(latestGradeRecord);
    }

    CountSummary countSummary = countEssayTypes(analysisHistory);
    AppState.WeaknessProfile weaknessProfile = buildWeaknessProfile(analysisHistory);
    String dominantEssayType =
        countSummary.applicationCount >= countSummary.continuationCount ? "application" : "continuation";

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("ready", !weaknessProfile.tags.isEmpty());
    data.put("title", "最近 " + analysisHistory.size() + " 篇批改");
    data.put("headline", weaknessProfile.headline);
    data.put("nextFocus", weaknessProfile.nextFocus);
    data.put("tags", weaknessProfile.tags);
    data.put("sampleSize", analysisHistory.size());
    data.put("badgeText", analysisHistory.size() + " 篇样本");
    data.put("applicationCount", countSummary.applicationCount);
    data.put("continuationCount", countSummary.continuationCount);
    data.put("latestScoreText", latestGradeRecord == null ? "" : TextUtils.trimToEmpty(latestGradeRecord.scoreText));
    data.put("lastUpdatedText", latestGradeRecord == null ? "" : formatTime(latestGradeRecord.createdAt));
    data.put("primaryActionLabel", "继续提分");
    data.put("secondaryActionLabel", latestGradeRecord == null ? "去严格批改" : "查看最近批改");
    data.put("primaryActionKind", "continue_grade");
    data.put("secondaryActionKind", latestGradeRecord == null ? "continue_grade" : "view_latest_grade");
    data.put("suggestedEssayType", dominantEssayType);
    return data;
  }

  public void enrichGradeRecord(String userId, AppState.EssayRecord currentRecord) {
    if (currentRecord == null || currentRecord.analysis == null) {
      return;
    }
    List<AppState.EssayRecord> history = mergeAndSort(loadRecentGradeRecords(userId), currentRecord);
    List<AppState.EssayRecord> analysisHistory = extractAnalysisHistory(history);
    currentRecord.analysis.weaknessProfile = buildWeaknessProfile(analysisHistory);
  }

  private List<AppState.EssayRecord> loadRecentGradeRecords(String userId) {
    return essayRecordRepository.findRecentByUserId(userId, 0, 30, "grade", null, "SUCCESS");
  }

  private List<AppState.EssayRecord> mergeAndSort(
      List<AppState.EssayRecord> history,
      AppState.EssayRecord currentRecord
  ) {
    List<AppState.EssayRecord> merged = new ArrayList<>();
    if (currentRecord != null) {
      merged.add(currentRecord);
    }
    for (AppState.EssayRecord record : history) {
      if (record == null) {
        continue;
      }
      if (currentRecord != null && Objects.equals(currentRecord.id, record.id)) {
        continue;
      }
      merged.add(record);
    }
    merged.sort(Comparator.comparingLong((AppState.EssayRecord item) -> item.createdAt).reversed());
    return merged;
  }

  private List<AppState.EssayRecord> extractAnalysisHistory(List<AppState.EssayRecord> gradeHistory) {
    return gradeHistory.stream()
        .filter(Objects::nonNull)
        .filter(item -> item.analysis != null)
        .collect(Collectors.toList());
  }

  private Map<String, Object> buildEmptyProfile() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("ready", false);
    data.put("title", "你的提分档案还没开始");
    data.put("headline", "先做 1 到 2 次严格批改，系统才看得出你总是在哪里丢分。");
    data.put("nextFocus", "先用“严格批改”喂进应用文或续写原文，系统才有素材判断你的稳定弱项。");
    data.put("tags", List.of());
    data.put("sampleSize", 0);
    data.put("badgeText", "待建立");
    data.put("applicationCount", 0);
    data.put("continuationCount", 0);
    data.put("latestScoreText", "");
    data.put("lastUpdatedText", "");
    data.put("primaryActionLabel", "先批应用文");
    data.put("secondaryActionLabel", "先批续写");
    data.put("primaryActionKind", "grade_application");
    data.put("secondaryActionKind", "grade_continuation");
    data.put("suggestedEssayType", "application");
    return data;
  }

  private Map<String, Object> buildPendingProfile(AppState.EssayRecord latestGradeRecord) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("ready", false);
    data.put("title", "提分档案正在建立");
    data.put("headline", "已经检测到批改记录，再积累 1 到 2 篇，首页就会形成更稳定的弱项画像。");
    data.put("nextFocus", "先用“严格批改”喂进应用文或续写原文，系统才有素材判断你的稳定弱项。");
    data.put("tags", List.of());
    data.put("sampleSize", 0);
    data.put("badgeText", "待建立");
    data.put("applicationCount", 0);
    data.put("continuationCount", 0);
    data.put("latestScoreText", TextUtils.trimToEmpty(latestGradeRecord.scoreText));
    data.put("lastUpdatedText", formatTime(latestGradeRecord.createdAt));
    data.put("primaryActionLabel", "继续做严格批改");
    data.put("secondaryActionLabel", "查看最近批改");
    data.put("primaryActionKind", "continue_grade");
    data.put("secondaryActionKind", "view_latest_grade");
    data.put(
        "suggestedEssayType",
        TextUtils.isBlank(latestGradeRecord.essayType) ? "application" : latestGradeRecord.essayType
    );
    return data;
  }

  private AppState.WeaknessProfile buildWeaknessProfile(List<AppState.EssayRecord> analysisHistory) {
    AppState.WeaknessProfile profile = new AppState.WeaknessProfile();
    if (analysisHistory.isEmpty()) {
      profile.headline = "先做 1 到 2 次严格批改，系统才看得出你总是在哪里丢分。";
      profile.nextFocus = "先用“严格批改”喂进应用文或续写原文，系统才有素材判断你的稳定弱项。";
      profile.sampleSize = 0;
      return profile;
    }

    Map<String, AppState.WeaknessTag> counters = new LinkedHashMap<>();
    for (WeaknessRule rule : WEAKNESS_RULES) {
      AppState.WeaknessTag tag = new AppState.WeaknessTag();
      tag.code = rule.code();
      tag.label = rule.label();
      tag.hitCount = 0;
      counters.put(tag.code, tag);
    }

    for (AppState.EssayRecord item : analysisHistory) {
      String evidence = buildEvidence(item.analysis);
      for (WeaknessRule rule : WEAKNESS_RULES) {
        if (rule.matches(evidence)) {
          counters.get(rule.code()).hitCount += 1;
        }
      }
    }

    List<AppState.WeaknessTag> tags = counters.values().stream()
        .filter(tag -> tag.hitCount > 0)
        .sorted(Comparator.comparingInt((AppState.WeaknessTag tag) -> tag.hitCount).reversed())
        .limit(3)
        .collect(Collectors.toCollection(ArrayList::new));

    profile.tags = tags;
    profile.sampleSize = analysisHistory.size();
    profile.headline = tags.isEmpty()
        ? "目前还没识别出稳定重复的问题。"
        : "你最常丢分在" + joinTagLabels(tags) + "。";
    profile.nextFocus = buildNextFocus(tags);
    return profile;
  }

  private String buildEvidence(AppState.GradeAnalysis analysis) {
    List<String> parts = new ArrayList<>();
    addIfPresent(parts, analysis.contentDiagnosis);
    addIfPresent(parts, analysis.structureDiagnosis);
    addIfPresent(parts, analysis.languageDiagnosis);
    addIfPresent(parts, analysis.lossPointDiagnosis);
    addIfPresent(parts, analysis.secondDraftGuidance);
    return String.join("\n", parts);
  }

  private void addIfPresent(List<String> parts, String value) {
    if (!TextUtils.isBlank(value)) {
      parts.add(value.trim());
    }
  }

  private CountSummary countEssayTypes(List<AppState.EssayRecord> analysisHistory) {
    int applicationCount = 0;
    int continuationCount = 0;
    for (AppState.EssayRecord item : analysisHistory) {
      if ("application".equalsIgnoreCase(item.essayType)) {
        applicationCount += 1;
      }
      if ("continuation".equalsIgnoreCase(item.essayType)) {
        continuationCount += 1;
      }
    }
    return new CountSummary(applicationCount, continuationCount);
  }

  private String joinTagLabels(List<AppState.WeaknessTag> tags) {
    return tags.stream().map(tag -> tag.label).collect(Collectors.joining("、"));
  }

  private String buildNextFocus(List<AppState.WeaknessTag> tags) {
    if (tags.isEmpty()) {
      return "继续做 1 到 2 篇同题型批改，画像会更稳定。";
    }
    if (tags.size() == 1) {
      return "下一篇先只盯住“" + tags.get(0).label + "”，把这一个点改稳，再追求句子更花。";
    }
    return "下一篇优先按“" + tags.get(0).label + " -> " + tags.get(1).label + "”的顺序改，不要一上来整篇重写。";
  }

  private String formatTime(long createdAtEpochMillis) {
    if (createdAtEpochMillis <= 0) {
      return "";
    }
    return DISPLAY_TIME_FORMATTER.format(Instant.ofEpochMilli(createdAtEpochMillis));
  }

  private static WeaknessRule createRule(String code, String label, String expression) {
    return new WeaknessRule(code, label, Pattern.compile(expression, Pattern.CASE_INSENSITIVE));
  }

  private record CountSummary(int applicationCount, int continuationCount) {
  }

  private record WeaknessRule(String code, String label, Pattern pattern) {
    private boolean matches(String evidence) {
      return pattern.matcher(evidence == null ? "" : evidence).find();
    }
  }
}
