package com.gaokao.essay.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gaokao.essay.backend.model.GrowthProfile;
import com.gaokao.essay.backend.repository.EssayRecordRepository;
import com.gaokao.essay.backend.store.AppState;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class GrowthProfileServiceTest {

  private final EssayRecordRepository repository = Mockito.mock(EssayRecordRepository.class);
  private final GrowthProfileService service = new GrowthProfileService(repository);

  @Test
  void keepsApplicationAndContinuationTrendsSeparate() {
    Mockito.when(repository.findRecentByUserId("user-1", 0, 50, "grade", null, "SUCCESS"))
        .thenReturn(List.of(
            record("continuation", "12分 / 25", 2000L, 4, 5, "续写衔接自然"),
            record("application", "9分 / 15", 1000L, 3, 5, "内容要点完整")
        ));

    GrowthProfile profile = service.load("user-1", "application");

    assertThat(profile.totalFormalGrades()).isEqualTo(2);
    assertThat(profile.profiles().get("application").scoreTrend()).hasSize(1);
    assertThat(profile.profiles().get("continuation").scoreTrend()).hasSize(1);
  }

  @Test
  void normalizesCapabilityPercentagesAndBuildsComparison() {
    Mockito.when(repository.findRecentByUserId("user-1", 0, 50, "grade", null, "SUCCESS"))
        .thenReturn(List.of(
            record("application", "11分 / 15", 2000L, 4, 5, "语法准确"),
            record("application", "9分 / 15", 1000L, 3, 5, "语法错误")
        ));

    GrowthProfile.EssayTypeProfile application = service.load("user-1", "application")
        .profiles().get("application");

    assertThat(application.capabilityTrends().get("content"))
        .extracting(GrowthProfile.CapabilityPoint::percent)
        .containsExactly(60.0, 80.0);
    assertThat(application.comparison().headline()).contains("提高");
  }

  @Test
  void repeatedProblemBecomesTheDailyTask() {
    AppState.EssayRecord latest = record(
        "application", "9分 / 15", 2000L, 3, 5, "时态和主谓一致仍有语法错误"
    );
    latest.analysis.sentenceDiagnostics = List.of(
        sentenceDiagnosis("ERROR_CORRECTION", "GRAMMAR", false, "时态错误")
    );
    AppState.EssayRecord previous = record(
        "application", "8分 / 15", 1000L, 3, 5, "语法错误，注意时态"
    );
    previous.analysis.sentenceDiagnostics = List.of(
        sentenceDiagnosis("ERROR_CORRECTION", "GRAMMAR", false, "主谓一致错误")
    );
    Mockito.when(repository.findRecentByUserId("user-1", 0, 50, "grade", null, "SUCCESS"))
        .thenReturn(List.of(latest, previous));

    GrowthProfile profile = service.load("user-1", "application");

    assertThat(profile.recentErrors().get(0).status()).isEqualTo("REPEATED");
    assertThat(profile.dailyTask().code()).isEqualTo("grammar_accuracy");
    assertThat(profile.dailyTask().minutes()).isEqualTo(10);
  }

  @Test
  void oneRecordIsOnlyAStartingPoint() {
    Mockito.when(repository.findRecentByUserId("user-1", 0, 50, "grade", null, "SUCCESS"))
        .thenReturn(List.of(record("application", "9分 / 15", 1000L, 3, 5, "")));

    GrowthProfile.EssayTypeProfile application = service.load("user-1", "application")
        .profiles().get("application");

    assertThat(application.state()).isEqualTo("STARTING_POINT");
    assertThat(application.comparison().headline()).isEqualTo("这是你的应用文成长起点");
  }

  @Test
  void countsOnlyExplicitErrorCorrectionsAndNeverAssignsUpgradesAsDailyTasks() {
    AppState.EssayRecord formalRecord = record(
        "application",
        "9分 / 15",
        1000L,
        3,
        5,
        "这处模板化表达可以更自然"
    );
    formalRecord.analysis.sentenceDiagnostics = List.of(
        sentenceDiagnosis("ERROR_CORRECTION", "GRAMMAR", false, "时态错误"),
        sentenceDiagnosis("EXPRESSION_UPGRADE", "NONE", false, "避免模板化，表达更自然")
    );
    Mockito.when(repository.findRecentByUserId("user-1", 0, 50, "grade", null, "SUCCESS"))
        .thenReturn(List.of(formalRecord));

    GrowthProfile profile = service.load("user-1", "application");

    assertThat(profile.recentErrors())
        .extracting(GrowthProfile.ErrorItem::code)
        .containsExactly("grammar_accuracy");
    assertThat(profile.dailyTask().code()).isNotEqualTo("language_naturalness");
  }

  @Test
  void excludesLegacyInferredCorrectionsFromGrowthErrors() {
    AppState.EssayRecord formalRecord = record(
        "application",
        "9分 / 15",
        1000L,
        3,
        5,
        "语法错误"
    );
    formalRecord.analysis.sentenceDiagnostics = List.of(
        sentenceDiagnosis("ERROR_CORRECTION", "GRAMMAR", true, "语法错误")
    );
    Mockito.when(repository.findRecentByUserId("user-1", 0, 50, "grade", null, "SUCCESS"))
        .thenReturn(List.of(formalRecord));

    assertThat(service.load("user-1", "application").recentErrors()).isEmpty();
  }

  @Test
  void repeatedPunctuationCorrectionsAppearInRecentErrorsAndDriveTheDailyTask() {
    AppState.EssayRecord latest = record("application", "9分 / 15", 2000L, 3, 5, "");
    latest.analysis.sentenceDiagnostics = List.of(
        sentenceDiagnosis("ERROR_CORRECTION", "PUNCTUATION", false, "逗号使用错误")
    );
    AppState.EssayRecord previous = record("application", "8分 / 15", 1000L, 3, 5, "");
    previous.analysis.sentenceDiagnostics = List.of(
        sentenceDiagnosis("ERROR_CORRECTION", "PUNCTUATION", false, "标点错误")
    );
    Mockito.when(repository.findRecentByUserId("user-1", 0, 50, "grade", null, "SUCCESS"))
        .thenReturn(List.of(latest, previous));

    GrowthProfile profile = service.load("user-1", "application");

    assertThat(profile.recentErrors())
        .extracting(GrowthProfile.ErrorItem::code)
        .containsExactly("grammar_accuracy");
    assertThat(profile.dailyTask().code()).isEqualTo("grammar_accuracy");
  }

  @Test
  void contentCorrectionAppearsInRecentErrors() {
    AppState.EssayRecord formalRecord = record("application", "9分 / 15", 1000L, 3, 5, "");
    formalRecord.analysis.sentenceDiagnostics = List.of(
        sentenceDiagnosis("ERROR_CORRECTION", "CONTENT", false, "遗漏活动时间")
    );
    Mockito.when(repository.findRecentByUserId("user-1", 0, 50, "grade", null, "SUCCESS"))
        .thenReturn(List.of(formalRecord));

    assertThat(service.load("user-1", "application").recentErrors())
        .extracting(GrowthProfile.ErrorItem::code)
        .containsExactly("task_completion");
  }

  @Test
  void contentCorrectionBecomesMasteredAfterThreeCleanFormalGrades() {
    AppState.EssayRecord initial = record("application", "8分 / 15", 1000L, 3, 5, "");
    initial.analysis.sentenceDiagnostics = List.of(
        sentenceDiagnosis("ERROR_CORRECTION", "CONTENT", false, "遗漏活动时间")
    );
    Mockito.when(repository.findRecentByUserId("user-1", 0, 50, "grade", null, "SUCCESS"))
        .thenReturn(List.of(
            record("application", "10分 / 15", 4000L, 3, 5, ""),
            record("application", "9分 / 15", 3000L, 3, 5, ""),
            record("application", "9分 / 15", 2000L, 3, 5, ""),
            initial
        ));

    assertThat(service.load("user-1", "application").masteryItems())
        .extracting(GrowthProfile.MasteryItem::code)
        .containsExactly("task_completion");
  }

  private AppState.EssayRecord record(
      String essayType,
      String scoreText,
      long createdAt,
      double contentScore,
      double contentMax,
      String diagnosis
  ) {
    AppState.EssayRecord record = new AppState.EssayRecord();
    record.id = essayType + "-" + createdAt;
    record.userId = "user-1";
    record.mode = "grade";
    record.taskStatus = "SUCCESS";
    record.essayType = essayType;
    record.scoreText = scoreText;
    record.createdAt = createdAt;
    record.analysis = new AppState.GradeAnalysis();
    record.analysis.contentDiagnosis = diagnosis;
    record.analysis.languageDiagnosis = diagnosis;
    record.analysis.scoreDimensions = List.of(
        dimension("content", "内容", contentScore, contentMax),
        dimension("language", "语言", 3, 5),
        dimension("structure", "结构", 2, 3),
        dimension("vocabulary", "词汇", 1, 2)
    );
    return record;
  }

  private AppState.ScoreDimension dimension(String code, String label, double score, double maxScore) {
    AppState.ScoreDimension dimension = new AppState.ScoreDimension();
    dimension.code = code;
    dimension.label = label;
    dimension.score = score;
    dimension.maxScore = maxScore;
    return dimension;
  }

  private AppState.SentenceDiagnosis sentenceDiagnosis(
      String kind,
      String errorType,
      boolean legacyInferred,
      String diagnosis
  ) {
    AppState.SentenceDiagnosis item = new AppState.SentenceDiagnosis();
    item.kind = kind;
    item.errorType = errorType;
    item.legacyInferred = legacyInferred;
    item.diagnosis = diagnosis;
    return item;
  }
}
