package com.gaokao.essay.backend.service;

import com.gaokao.essay.backend.model.AuthenticatedUser;
import com.gaokao.essay.backend.model.GrowthProfile;
import com.gaokao.essay.backend.repository.EssayRecordRepository;
import com.gaokao.essay.backend.store.AppState;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

  private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
  private static final Pattern SCORE_PATTERN =
      Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:分)?\\s*/\\s*(\\d+(?:\\.\\d+)?)");

  private final MembershipService membershipService;
  private final GrowthProfileService growthProfileService;
  private final EssayRecordRepository essayRecordRepository;
  private final Clock clock;

  @Autowired
  public DashboardService(
      MembershipService membershipService,
      GrowthProfileService growthProfileService,
      EssayRecordRepository essayRecordRepository
  ) {
    this(membershipService, growthProfileService, essayRecordRepository, Clock.systemUTC());
  }

  DashboardService(
      MembershipService membershipService,
      GrowthProfileService growthProfileService,
      EssayRecordRepository essayRecordRepository,
      Clock clock
  ) {
    this.membershipService = membershipService;
    this.growthProfileService = growthProfileService;
    this.essayRecordRepository = essayRecordRepository;
    this.clock = clock;
  }

  public Map<String, Object> build(AuthenticatedUser user, String requestedEssayType) {
    String essayType = normalizeEssayType(requestedEssayType);
    List<AppState.EssayRecord> records = essayRecordRepository.findRecentByUserId(
        user.userId(),
        0,
        100,
        null,
        null,
        "SUCCESS"
    );
    GrowthProfile growth = growthProfileService.load(user.userId(), essayType);

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("essayType", essayType);
    data.put("generatedAt", clock.instant().toEpochMilli());
    data.put("entitlement", membershipService.getEntitlement(user));
    data.put("growth", growth);
    data.put("weekly", buildWeeklyMetric(records, essayType));
    data.put("streak", buildStreak(records));
    return data;
  }

  private Map<String, Object> buildWeeklyMetric(
      List<AppState.EssayRecord> records,
      String essayType
  ) {
    LocalDate today = LocalDate.ofInstant(clock.instant(), SHANGHAI);
    LocalDate currentStart = today.minusDays(6);
    LocalDate previousStart = today.minusDays(13);
    LocalDate previousEnd = today.minusDays(7);

    OptionalDouble currentAverage = averageScore(records, essayType, currentStart, today);
    OptionalDouble previousAverage = averageScore(records, essayType, previousStart, previousEnd);

    Map<String, Object> metric = new LinkedHashMap<>();
    if (currentAverage.isEmpty() || previousAverage.isEmpty()) {
      metric.put("delta", 0.0);
      metric.put("label", "等待更多记录");
      metric.put("status", "PENDING");
      return metric;
    }

    double delta = roundOneDecimal(currentAverage.getAsDouble() - previousAverage.getAsDouble());
    String status = delta > 0.4 ? "IMPROVED" : delta < -0.4 ? "DECLINED" : "STABLE";
    metric.put("delta", delta);
    metric.put("label", formatDelta(delta));
    metric.put("status", status);
    return metric;
  }

  private OptionalDouble averageScore(
      List<AppState.EssayRecord> records,
      String essayType,
      LocalDate start,
      LocalDate end
  ) {
    return records.stream()
        .filter(record -> record != null && "grade".equalsIgnoreCase(record.mode))
        .filter(record -> essayType.equalsIgnoreCase(record.essayType))
        .filter(record -> {
          LocalDate date = recordDate(record);
          return date != null && !date.isBefore(start) && !date.isAfter(end);
        })
        .mapToDouble(record -> parseScorePercent(record.scoreText).orElse(Double.NaN))
        .filter(value -> !Double.isNaN(value))
        .average();
  }

  private Map<String, Object> buildStreak(List<AppState.EssayRecord> records) {
    Set<LocalDate> practiceDates = new TreeSet<>();
    for (AppState.EssayRecord record : records) {
      if (record == null || !("grade".equalsIgnoreCase(record.mode) || "coach".equalsIgnoreCase(record.mode))) {
        continue;
      }
      LocalDate date = recordDate(record);
      if (date != null) {
        practiceDates.add(date);
      }
    }

    LocalDate today = LocalDate.ofInstant(clock.instant(), SHANGHAI);
    LocalDate cursor = practiceDates.contains(today) ? today : today.minusDays(1);
    int days = 0;
    while (practiceDates.contains(cursor)) {
      days += 1;
      cursor = cursor.minusDays(1);
    }

    Map<String, Object> streak = new LinkedHashMap<>();
    streak.put("days", days);
    streak.put("label", days > 0 ? "已连续学习 " + days + " 天" : "开始第一次练习");
    return streak;
  }

  private OptionalDouble parseScorePercent(String scoreText) {
    Matcher matcher = SCORE_PATTERN.matcher(scoreText == null ? "" : scoreText);
    if (!matcher.find()) {
      return OptionalDouble.empty();
    }
    try {
      double score = Double.parseDouble(matcher.group(1));
      double maxScore = Double.parseDouble(matcher.group(2));
      return maxScore > 0 ? OptionalDouble.of(score / maxScore * 100.0) : OptionalDouble.empty();
    } catch (NumberFormatException ignored) {
      return OptionalDouble.empty();
    }
  }

  private LocalDate recordDate(AppState.EssayRecord record) {
    if (record.createdAt <= 0) {
      return null;
    }
    return LocalDate.ofInstant(Instant.ofEpochMilli(record.createdAt), SHANGHAI);
  }

  private String normalizeEssayType(String essayType) {
    return "continuation".equalsIgnoreCase(essayType) ? "continuation" : "application";
  }

  private double roundOneDecimal(double value) {
    return Math.round(value * 10.0) / 10.0;
  }

  private String formatDelta(double delta) {
    if (Math.abs(delta) < 0.05) {
      return "与上周持平";
    }
    String number = Math.abs(delta % 1.0) < 0.05
        ? String.valueOf((int) Math.abs(delta))
        : String.format(Locale.ROOT, "%.1f", Math.abs(delta));
    return delta > 0 ? "+" + number + "分" : "-" + number + "分";
  }
}
