package com.gaokao.essay.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gaokao.essay.backend.model.ApiException;
import com.gaokao.essay.backend.model.AuthenticatedUser;
import com.gaokao.essay.backend.model.EssayTaskRequest;
import com.gaokao.essay.backend.repository.EssayRecordRepository;
import com.gaokao.essay.backend.store.AppState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class EssayServiceIdempotencyTest {

  @Test
  void shouldReuseExistingSuccessRecordWithoutDoubleChargingQuota() {
    MembershipService membershipService = Mockito.mock(MembershipService.class);
    ContentSafetyService contentSafetyService = Mockito.mock(ContentSafetyService.class);
    AiGatewayService aiGatewayService = Mockito.mock(AiGatewayService.class);
    CoachKnowledgeBaseService coachKnowledgeBaseService = Mockito.mock(CoachKnowledgeBaseService.class);
    StudyProfileService studyProfileService = Mockito.mock(StudyProfileService.class);
    HistoryService historyService = new HistoryService(new InMemoryEssayRecordRepository());
    EssayService essayService = new EssayService(
        membershipService,
        contentSafetyService,
        historyService,
        aiGatewayService,
        coachKnowledgeBaseService,
        studyProfileService,
        new ObjectMapper()
    );

    AuthenticatedUser user = authenticatedUser();
    EssayTaskRequest request = buildCoachRequest("req_same");

    when(membershipService.reserveEssayAccess(user))
        .thenReturn(MembershipService.UsageReservation.trial(user.userId(), List.of("ESSAY_TOTAL")));
    when(coachKnowledgeBaseService.prepareKnowledge(any(EssayTaskRequest.class))).thenReturn(buildCoachGuidance());
    when(coachKnowledgeBaseService.isEnabled()).thenReturn(true);
    when(aiGatewayService.requestJsonText(anyString(), anyString())).thenReturn(validCoachJson());

    EssayService.EssayExecution first = essayService.execute(user, request);
    EssayService.EssayExecution second = essayService.execute(user, request);

    assertEquals(first.getRecord().id, second.getRecord().id);
    assertEquals("req_same", second.getRecord().clientRequestId);
    assertEquals("SUCCESS", second.getRecord().taskStatus);
    verify(membershipService, times(1)).reserveEssayAccess(user);
    verify(aiGatewayService, times(1)).requestJsonText(anyString(), anyString());
  }

  @Test
  void shouldRejectSecondRequestWhileFirstOneIsStillProcessing() {
    MembershipService membershipService = Mockito.mock(MembershipService.class);
    ContentSafetyService contentSafetyService = Mockito.mock(ContentSafetyService.class);
    AiGatewayService aiGatewayService = Mockito.mock(AiGatewayService.class);
    CoachKnowledgeBaseService coachKnowledgeBaseService = Mockito.mock(CoachKnowledgeBaseService.class);
    StudyProfileService studyProfileService = Mockito.mock(StudyProfileService.class);
    InMemoryEssayRecordRepository repository = new InMemoryEssayRecordRepository();
    HistoryService historyService = new HistoryService(repository);
    EssayService essayService = new EssayService(
        membershipService,
        contentSafetyService,
        historyService,
        aiGatewayService,
        coachKnowledgeBaseService,
        studyProfileService,
        new ObjectMapper()
    );

    AuthenticatedUser user = authenticatedUser();
    EssayTaskRequest request = buildCoachRequest("req_processing");
    repository.save(buildProcessingRecord(user.userId(), user.openId(), "req_processing"));

    ApiException error = assertThrows(ApiException.class, () -> essayService.execute(user, request));

    assertEquals("REQUEST_IN_PROGRESS", error.getCode());
    verify(membershipService, never()).reserveEssayAccess(any());
    verify(aiGatewayService, never()).requestJsonText(anyString(), anyString());
  }

  @Test
  void shouldReuseRepairedGradeRecordWithNormalizedExpressionUpgrade() {
    MembershipService membershipService = Mockito.mock(MembershipService.class);
    ContentSafetyService contentSafetyService = Mockito.mock(ContentSafetyService.class);
    AiGatewayService aiGatewayService = Mockito.mock(AiGatewayService.class);
    CoachKnowledgeBaseService coachKnowledgeBaseService = Mockito.mock(CoachKnowledgeBaseService.class);
    StudyProfileService studyProfileService = Mockito.mock(StudyProfileService.class);
    HistoryService historyService = new HistoryService(new InMemoryEssayRecordRepository());
    EssayService essayService = new EssayService(
        membershipService,
        contentSafetyService,
        historyService,
        aiGatewayService,
        coachKnowledgeBaseService,
        studyProfileService,
        new ObjectMapper()
    );

    AuthenticatedUser user = authenticatedUser();
    EssayTaskRequest request = buildGradeRequest("req_grade_repair");
    when(membershipService.reserveEssayAccess(user))
        .thenReturn(MembershipService.UsageReservation.trial(user.userId(), List.of("ESSAY_TOTAL")));
    when(aiGatewayService.requestJsonText(anyString(), anyString()))
        .thenReturn("{\"content\":\"缺少批改字段\"}", validRepairedGradeJson());

    EssayService.EssayExecution first = essayService.execute(user, request);
    EssayService.EssayExecution second = essayService.execute(user, request);

    AppState.SentenceDiagnosis diagnosis = second.getRecord().analysis.sentenceDiagnostics.get(0);
    assertEquals(first.getRecord().id, second.getRecord().id);
    assertEquals("EXPRESSION_UPGRADE", diagnosis.kind);
    assertEquals("NONE", diagnosis.errorType);
    assertEquals(false, diagnosis.legacyInferred);
    verify(membershipService, times(1)).reserveEssayAccess(user);
    verify(aiGatewayService, times(2)).requestJsonText(anyString(), anyString());

    ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
    verify(aiGatewayService, times(2)).requestJsonText(anyString(), promptCaptor.capture());
    String initialPrompt = promptCaptor.getAllValues().get(0);
    String repairPrompt = promptCaptor.getAllValues().get(1);
    assertTrue(initialPrompt.contains("重复单调"));
    assertTrue(initialPrompt.contains("简单句堆砌"));
    assertTrue(initialPrompt.contains("中式英语"));
    assertTrue(initialPrompt.contains("实际词数"));
    assertTrue(initialPrompt.contains("所有高置信度真实错误"));
    assertTrue(initialPrompt.contains("TENSE"));
    assertTrue(repairPrompt.contains("EXPRESSION_UPGRADE"));
    assertTrue(repairPrompt.contains("NONE"));
    assertTrue(repairPrompt.contains("正确句子不能标为错误"));
    assertTrue(repairPrompt.contains("重复单调"));
    assertTrue(repairPrompt.contains("简单句堆砌"));
    assertTrue(repairPrompt.contains("中式英语"));
    assertTrue(repairPrompt.contains("实际词数"));
    assertTrue(repairPrompt.contains("所有高置信度真实错误"));
    assertTrue(repairPrompt.contains("TENSE"));
  }

  @Test
  void shouldMoveInvalidSentenceCorrectionToContentDiagnosis() {
    MembershipService membershipService = Mockito.mock(MembershipService.class);
    ContentSafetyService contentSafetyService = Mockito.mock(ContentSafetyService.class);
    AiGatewayService aiGatewayService = Mockito.mock(AiGatewayService.class);
    CoachKnowledgeBaseService coachKnowledgeBaseService = Mockito.mock(CoachKnowledgeBaseService.class);
    StudyProfileService studyProfileService = Mockito.mock(StudyProfileService.class);
    HistoryService historyService = new HistoryService(new InMemoryEssayRecordRepository());
    EssayService essayService = new EssayService(
        membershipService,
        contentSafetyService,
        historyService,
        aiGatewayService,
        coachKnowledgeBaseService,
        studyProfileService,
        new ObjectMapper()
    );

    AuthenticatedUser user = authenticatedUser();
    EssayTaskRequest request = buildGradeRequest("req_invalid_sentence_pair");
    when(membershipService.reserveEssayAccess(user))
        .thenReturn(MembershipService.UsageReservation.trial(user.userId(), List.of("ESSAY_TOTAL")));
    when(aiGatewayService.requestJsonText(anyString(), anyString()))
        .thenReturn(invalidSentencePairGradeJson());

    AppState.GradeAnalysis analysis = essayService.execute(user, request).getRecord().analysis;

    assertEquals(0, analysis.sentenceDiagnostics.size());
    assertTrue(analysis.contentDiagnosis.contains("文章基本切题"));
    assertTrue(analysis.contentDiagnosis.contains("补充活动中的具体任务和收获"));
  }

  @Test
  void shouldAllowOnlyOneLiveExecutionUnderConcurrentDuplicateRequests() throws Exception {
    MembershipService membershipService = Mockito.mock(MembershipService.class);
    ContentSafetyService contentSafetyService = Mockito.mock(ContentSafetyService.class);
    AiGatewayService aiGatewayService = Mockito.mock(AiGatewayService.class);
    CoachKnowledgeBaseService coachKnowledgeBaseService = Mockito.mock(CoachKnowledgeBaseService.class);
    StudyProfileService studyProfileService = Mockito.mock(StudyProfileService.class);
    InMemoryEssayRecordRepository repository = new InMemoryEssayRecordRepository();
    HistoryService historyService = new HistoryService(repository);
    EssayService essayService = new EssayService(
        membershipService,
        contentSafetyService,
        historyService,
        aiGatewayService,
        coachKnowledgeBaseService,
        studyProfileService,
        new ObjectMapper()
    );

    AuthenticatedUser user = authenticatedUser();
    AtomicInteger aiCalls = new AtomicInteger();
    when(membershipService.reserveEssayAccess(user))
        .thenReturn(MembershipService.UsageReservation.trial(user.userId(), List.of("ESSAY_TOTAL")));
    when(coachKnowledgeBaseService.prepareKnowledge(any(EssayTaskRequest.class))).thenReturn(buildCoachGuidance());
    when(coachKnowledgeBaseService.isEnabled()).thenReturn(false);
    when(aiGatewayService.requestJsonText(anyString(), anyString())).thenAnswer(invocation -> {
      aiCalls.incrementAndGet();
      Thread.sleep(150);
      return validCoachJson();
    });

    ExecutorService executorService = Executors.newFixedThreadPool(6);
    CountDownLatch ready = new CountDownLatch(6);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<String>> futures = new ArrayList<>();
      for (int index = 0; index < 6; index++) {
        futures.add(executorService.submit(() -> {
          ready.countDown();
          start.await(5, TimeUnit.SECONDS);
          try {
            essayService.execute(user, buildCoachRequest("req_concurrent"));
            return "SUCCESS";
          } catch (ApiException error) {
            return error.getCode();
          }
        }));
      }

      ready.await(5, TimeUnit.SECONDS);
      start.countDown();

      List<String> results = new ArrayList<>();
      for (Future<String> future : futures) {
        results.add(future.get(5, TimeUnit.SECONDS));
      }

      long successCount = results.stream().filter("SUCCESS"::equals).count();
      long processingCount = results.stream().filter("REQUEST_IN_PROGRESS"::equals).count();
      assertEquals(1L, successCount);
      assertEquals(5L, processingCount);
      assertEquals(1, aiCalls.get());
      verify(membershipService, times(1)).reserveEssayAccess(user);
    } finally {
      executorService.shutdownNow();
    }
  }

  private AuthenticatedUser authenticatedUser() {
    Instant now = Instant.now();
    return new AuthenticatedUser("user_test", "open_test", now, now.plusSeconds(3600));
  }

  private EssayTaskRequest buildCoachRequest(String clientRequestId) {
    EssayTaskRequest request = new EssayTaskRequest();
    request.setClientRequestId(clientRequestId);
    request.setMode("coach");
    request.setEssayType("application");
    request.setCoachStage("prewrite");
    request.setCoachMode("outline");
    request.setBand("band2");
    request.setBandValue("学霸");
    request.setTaskContent("假定你是李华，请给外教写邮件，建议配图并自荐承担画图工作。");
    request.setSourceMaterial("");
    request.setDraftText("");
    request.setRequirements("语气自然");
    return request;
  }

  private EssayTaskRequest buildGradeRequest(String clientRequestId) {
    EssayTaskRequest request = new EssayTaskRequest();
    request.setClientRequestId(clientRequestId);
    request.setMode("grade");
    request.setEssayType("application");
    request.setBand("band2");
    request.setBandValue("学霸");
    request.setTaskContent("假定你是李华，请给外教写邮件。");
    request.setDraftText("I am very happy to write to you.");
    request.setRequirements("语气自然");
    return request;
  }

  private CoachKnowledgeBaseService.CoachGuidance buildCoachGuidance() {
    AppState.CoachPlan plan = new AppState.CoachPlan();
    plan.stage = "prewrite";
    plan.coachingMode = "outline";
    plan.typeJudgment = "应用文";
    plan.identityTone = "礼貌自然";
    plan.templateId = "application-mail";
    plan.scenario = "邮件";
    plan.taskPurpose = "建议信";
    plan.officialLogic = "先点明目的，再顺排要点，最后礼貌收束。";
    plan.opening = "先表明来意。";
    plan.body = "按题面顺序写建议和自荐理由。";
    plan.ending = "礼貌收束并表达期待。";
    plan.mustInclude = List.of("建议配图", "自荐画图");
    plan.riskPoints = List.of("漏掉自荐", "语气过硬");
    plan.suggestedExpressions = List.of("I'd like to", "I can help with");
    return new CoachKnowledgeBaseService.CoachGuidance(
        List.of("建议配图", "自荐画图"),
        List.of(),
        List.of(),
        null,
        "prewrite",
        "outline",
        plan
    );
  }

  private String validCoachJson() {
    return """
        {
          "content":"### 陪练定位\\n- 当前阶段：prewrite",
          "wordCount":12,
          "scoreText":"",
          "coachPlan":{
            "stage":"prewrite",
            "coachingMode":"outline",
            "typeJudgment":"应用文",
            "identityTone":"礼貌自然",
            "templateId":"application-mail",
            "scenario":"邮件",
            "taskPurpose":"建议信",
            "officialLogic":"先点明目的，再顺排要点，最后礼貌收束。",
            "opening":"先表明来意。",
            "body":"按题面顺序写建议和自荐理由。",
            "ending":"礼貌收束并表达期待。",
            "clueReuse":"",
            "emotionalFlow":"",
            "secondOpeningBridge":"",
            "bandRecommendation":"学霸版",
            "bandReason":"当前题目更适合先稳住得体度。",
            "drillFocus":"先把两条要点写完整。",
            "successCheck":"两条要点都落在正文里。",
            "routeAction":"continue_coach",
            "routeReason":"先列提纲再下笔更稳。",
            "writingPriorities":["先写来意","再写两条正文"],
            "drillTasks":["写一个开头句","补两个正文点"],
            "mustInclude":["建议配图","自荐画图"],
            "riskPoints":["漏掉自荐","语气过硬"],
            "suggestedExpressions":["I'd like to","I can help with"]
          }
        }
        """;
  }

  private String validRepairedGradeJson() {
    return """
        {
          "content":"### 批改报告",
          "wordCount":8,
          "scoreText":"11分 / 15",
          "analysis":{
            "typeJudgment":"应用文",
            "wordCountRisk":"字数适中",
            "alignmentDiagnosis":"切题",
            "machineRiskDiagnosis":"无明显机器感",
            "contentDiagnosis":"要点完整",
            "structureDiagnosis":"结构清楚",
            "languageFitnessDiagnosis":"表达基本正确",
            "languageDiagnosis":"表达基本正确",
            "flowDiagnosis":"衔接自然",
            "highlightDiagnosis":"语气得体",
            "lossPointDiagnosis":"无硬性错误",
            "overallComment":"表达可更自然",
            "sentenceDiagnostics":[{
              "kind":"EXPRESSION_UPGRADE",
              "errorType":"GRAMMAR",
              "original":"I am very happy to write to you.",
              "diagnosis":"表达可以更自然",
              "revision":"I am delighted to write to you."
            }],
            "scoreDimensions":[
              {"code":"content","label":"内容","score":4,"maxScore":5},
              {"code":"language","label":"语言","score":4,"maxScore":5},
              {"code":"structure","label":"结构","score":2,"maxScore":3},
              {"code":"vocabulary","label":"词汇","score":1,"maxScore":2}
            ],
            "secondDraftGuidance":"保持原意",
            "improvedEssay":"I am delighted to write to you.",
            "weaknessProfile":{"headline":"","nextFocus":"","sampleSize":1,"tags":[]}
          }
        }
        """;
  }

  private String invalidSentencePairGradeJson() {
    return """
        {
          "content":"### 批改报告",
          "wordCount":8,
          "scoreText":"11分 / 15",
          "analysis":{
            "typeJudgment":"应用文",
            "wordCountRisk":"字数适中",
            "contentDiagnosis":"文章基本切题。",
            "languageDiagnosis":"表达基本正确",
            "machineRiskDiagnosis":"无明显机器感",
            "overallComment":"内容需要更具体",
            "sentenceDiagnostics":[{
              "kind":"ERROR_CORRECTION",
              "errorType":"CONTENT",
              "original":"",
              "diagnosis":"补充活动中的具体任务和收获。",
              "revision":"增加团队合作和所学技能。"
            }],
            "scoreDimensions":[
              {"code":"content","label":"内容","score":4,"maxScore":5},
              {"code":"language","label":"语言","score":4,"maxScore":5},
              {"code":"structure","label":"结构","score":2,"maxScore":3},
              {"code":"vocabulary","label":"词汇","score":1,"maxScore":2}
            ],
            "improvedEssay":"I am delighted to write to you."
          }
        }
        """;
  }

  private AppState.EssayRecord buildProcessingRecord(String userId, String openId, String clientRequestId) {
    AppState.EssayRecord record = new AppState.EssayRecord();
    record.id = "essay_processing";
    record.clientRequestId = clientRequestId;
    record.userId = userId;
    record.openId = openId;
    record.mode = "coach";
    record.essayType = "application";
    record.band = "band2";
    record.bandLabel = "学霸";
    record.bandValue = "学霸";
    record.createdAt = Instant.now().toEpochMilli();
    record.taskStatus = "PROCESSING";
    record.summary = "processing";
    return record;
  }

  private static class InMemoryEssayRecordRepository implements EssayRecordRepository {
    private final Map<String, AppState.EssayRecord> records = new LinkedHashMap<>();

    @Override
    public synchronized AppState.EssayRecord save(AppState.EssayRecord record) {
      records.put(record.id, record);
      return record;
    }

    @Override
    public synchronized AppState.EssayRecord findOrCreatePendingByClientRequestId(AppState.EssayRecord record) {
      return findByUserIdAndClientRequestId(record.userId, record.clientRequestId).orElseGet(() -> save(record));
    }

    @Override
    public synchronized List<AppState.EssayRecord> findRecentByUserId(
        String userId,
        int offset,
        int limit,
        String mode,
        String essayType,
        String taskStatus
    ) {
      return new ArrayList<>(records.values()).stream()
          .filter(record -> userId.equals(record.userId))
          .toList();
    }

    @Override
    public synchronized Optional<AppState.EssayRecord> findByIdAndUserId(String id, String userId) {
      AppState.EssayRecord record = records.get(id);
      if (record == null || !userId.equals(record.userId)) {
        return Optional.empty();
      }
      return Optional.of(record);
    }

    @Override
    public synchronized Optional<AppState.EssayRecord> findByUserIdAndClientRequestId(String userId, String clientRequestId) {
      return records.values().stream()
          .filter(record -> userId.equals(record.userId))
          .filter(record -> clientRequestId.equals(record.clientRequestId))
          .findFirst();
    }

    @Override
    public synchronized int deleteByIdAndUserId(String id, String userId) {
      return records.remove(id) == null ? 0 : 1;
    }

    @Override
    public synchronized int deleteByUserId(String userId, String mode, String essayType, String taskStatus) {
      int size = records.size();
      records.entrySet().removeIf(entry -> userId.equals(entry.getValue().userId));
      return size - records.size();
    }
  }
}
