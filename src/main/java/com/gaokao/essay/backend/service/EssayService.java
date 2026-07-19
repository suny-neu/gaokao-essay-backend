package com.gaokao.essay.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gaokao.essay.backend.model.ApiException;
import com.gaokao.essay.backend.model.AuthenticatedUser;
import com.gaokao.essay.backend.model.EssayTaskRequest;
import com.gaokao.essay.backend.store.AppState;
import com.gaokao.essay.backend.util.TextUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class EssayService {

  private final MembershipService membershipService;
  private final ContentSafetyService contentSafetyService;
  private final HistoryService historyService;
  private final AiGatewayService aiGatewayService;
  private final CoachKnowledgeBaseService coachKnowledgeBaseService;
  private final StudyProfileService studyProfileService;
  private final ObjectMapper objectMapper;

  public EssayService(
      MembershipService membershipService,
      ContentSafetyService contentSafetyService,
      HistoryService historyService,
      AiGatewayService aiGatewayService,
      CoachKnowledgeBaseService coachKnowledgeBaseService,
      StudyProfileService studyProfileService,
      ObjectMapper objectMapper
  ) {
    this.membershipService = membershipService;
    this.contentSafetyService = contentSafetyService;
    this.historyService = historyService;
    this.aiGatewayService = aiGatewayService;
    this.coachKnowledgeBaseService = coachKnowledgeBaseService;
    this.studyProfileService = studyProfileService;
    this.objectMapper = objectMapper;
  }

  public EssayExecution execute(AuthenticatedUser user, EssayTaskRequest request) {
    validateRequest(request);
    contentSafetyService.verifyUserInput(user.openId(), request);
    AppState.EssayRecord pendingRecord = buildPendingRecord(user, request);
    AppState.EssayRecord claimedRecord = historyService.findOrCreatePendingRecord(pendingRecord);
    if (!pendingRecord.id.equals(claimedRecord.id)) {
      return resolveExistingExecution(claimedRecord);
    }

    MembershipService.UsageReservation reservation = null;
    try {
      reservation = membershipService.reserveEssayAccess(user);
      CoachKnowledgeBaseService.CoachGuidance coachGuidance = coachKnowledgeBaseService.prepareKnowledge(request);
      String rawResponse = aiGatewayService.requestJsonText(
          buildSystemPrompt(request, coachGuidance),
          buildUserPrompt(request, coachGuidance)
      );
      populateRecord(claimedRecord, request, coachGuidance, rawResponse);
      claimedRecord.source = coachKnowledgeBaseService.isEnabled() ? "remote+kb" : "remote";
      claimedRecord.taskStatus = "SUCCESS";
      contentSafetyService.verifyOutput(user.openId(), claimedRecord.content);
      if (claimedRecord.analysis != null && !TextUtils.isBlank(claimedRecord.analysis.improvedEssay)) {
        contentSafetyService.verifyOutput(user.openId(), claimedRecord.analysis.improvedEssay);
      }
      if ("grade".equals(claimedRecord.mode)) {
        studyProfileService.enrichGradeRecord(user.userId(), claimedRecord);
      }
      historyService.saveRecord(claimedRecord);
      String streamText = resolveStreamText(claimedRecord);
      return new EssayExecution(claimedRecord, streamText);
    } catch (RuntimeException error) {
      if (reservation != null) {
        membershipService.releaseReservation(reservation);
      }
      markFailedRecord(claimedRecord);
      historyService.saveRecord(claimedRecord);
      throw error;
    }
  }

  private void validateRequest(EssayTaskRequest request) {
    String mode = TextUtils.lower(request.getMode());
    if (!List.of("coach", "grade").contains(mode)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MODE", "不支持的工作模式");
    }
    if (TextUtils.isBlank(request.getClientRequestId())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "REQUEST_ID_REQUIRED", "当前请求缺少 clientRequestId，请刷新页面后重试");
    }
    String essayType = TextUtils.lower(request.getEssayType());
    if (!List.of("application", "continuation").contains(essayType)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ESSAY_TYPE", "不支持的题型");
    }
    if ("grade".equals(mode) && TextUtils.isBlank(request.getDraftText())) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "DRAFT_REQUIRED", "严格批改模式必须提供学生作文");
    }
    if ("coach".equals(mode)) {
      String coachStage = TextUtils.lower(request.getCoachStage());
      String coachMode = TextUtils.lower(request.getCoachMode());
      boolean hasTaskContent = !TextUtils.isBlank(request.getTaskContent());
      boolean hasDraftText = !TextUtils.isBlank(request.getDraftText());

      if ("prewrite".equals(coachStage) && !hasTaskContent) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "TASK_REQUIRED", "写前陪练请先输入题目内容");
      }
      if ("sentence_upgrade".equals(coachMode) && !hasDraftText) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "DRAFT_REQUIRED", "句子升级请先贴上现有句子或草稿");
      }
      if ("postwrite".equals(coachStage) && !hasDraftText) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "DRAFT_REQUIRED", "写后陪练请先贴上你的作文草稿");
      }
      if ("routing".equals(coachMode) && !hasTaskContent && !hasDraftText) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "TASK_REQUIRED", "分流建议至少需要题目或现有草稿");
      }
      if (!hasTaskContent && !hasDraftText) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "TASK_REQUIRED", "请先输入题目内容或现有草稿");
      }
    }
  }

  private AppState.EssayRecord buildPendingRecord(AuthenticatedUser user, EssayTaskRequest request) {
    AppState.EssayRecord record = new AppState.EssayRecord();
    record.id = TextUtils.uid("essay");
    record.clientRequestId = TextUtils.trimToEmpty(request.getClientRequestId());
    record.userId = user.userId();
    record.openId = user.openId();
    record.mode = TextUtils.lower(request.getMode());
    record.essayType = TextUtils.lower(request.getEssayType());
    record.band = TextUtils.trimToEmpty(request.getBand());
    record.bandValue = TextUtils.trimToEmpty(request.getBandValue());
    record.bandLabel = resolveBandLabel(record.band);
    record.createdAt = java.time.Instant.now().toEpochMilli();
    record.source = "remote";
    record.taskStatus = "PROCESSING";
    record.promptSnapshot.taskContent = TextUtils.trimToEmpty(request.getTaskContent());
    record.promptSnapshot.sourceMaterial = TextUtils.trimToEmpty(request.getSourceMaterial());
    record.promptSnapshot.draftText = TextUtils.trimToEmpty(request.getDraftText());
    record.promptSnapshot.requirements = TextUtils.trimToEmpty(request.getRequirements());
    record.summary = TextUtils.summarize(
        !TextUtils.isBlank(record.promptSnapshot.taskContent) ? record.promptSnapshot.taskContent : record.promptSnapshot.draftText,
        90
    );
    record.content = "";
    record.scoreText = "";
    record.wordCount = 0;
    return record;
  }

  private void populateRecord(
      AppState.EssayRecord record,
      EssayTaskRequest request,
      CoachKnowledgeBaseService.CoachGuidance coachGuidance,
      String rawResponse
  ) {
    if ("grade".equals(record.mode)) {
      populateGradeRecord(record, rawResponse, request);
    } else {
      populateCoachRecord(record, rawResponse, request, coachGuidance);
    }
  }

  private EssayExecution resolveExistingExecution(AppState.EssayRecord record) {
    if ("SUCCESS".equalsIgnoreCase(record.taskStatus)) {
      return new EssayExecution(record, resolveStreamText(record));
    }
    if ("PROCESSING".equalsIgnoreCase(record.taskStatus)) {
      throw new ApiException(HttpStatus.CONFLICT, "REQUEST_IN_PROGRESS", "相同请求正在处理中，请不要重复提交");
    }
    throw new ApiException(HttpStatus.CONFLICT, "REQUEST_ALREADY_FAILED", "上一条相同请求已失败，请重新提交一次");
  }

  private void markFailedRecord(AppState.EssayRecord record) {
    record.taskStatus = "FAILED";
    if (TextUtils.isBlank(record.content)) {
      record.content = "请求失败，请重新提交一次。";
    }
  }

  private void populateCoachRecord(
      AppState.EssayRecord record,
      String rawResponse,
      EssayTaskRequest request,
      CoachKnowledgeBaseService.CoachGuidance coachGuidance
  ) {
    JsonNode root = parseJsonNode(rawResponse);
    if (!isValidCoachPayload(root)) {
      JsonNode repaired = tryRepairCoachPayload(rawResponse, request, coachGuidance);
      if (isValidCoachPayload(repaired) || repaired != null) {
        root = repaired;
      }
    }

    AppState.CoachPlan parsedPlan = parseCoachPlan(root == null ? null : root.path("coachPlan"));
    AppState.CoachPlan effectivePlan = mergeCoachPlan(parsedPlan, coachGuidance.getFallbackPlan());
    record.coachPlan = effectivePlan;

    String content = root == null ? "" : root.path("content").asText("");
    if (TextUtils.isBlank(content)) {
      content = buildCoachMarkdown(effectivePlan);
    }
    record.content = content.trim();
    record.wordCount = root == null
        ? TextUtils.countEnglishWords(record.content)
        : root.path("wordCount").asInt(TextUtils.countEnglishWords(record.content));
    record.scoreText = root == null ? "" : root.path("scoreText").asText("");
  }

  private void populateGradeRecord(
      AppState.EssayRecord record,
      String rawResponse,
      EssayTaskRequest request
  ) {
    JsonNode root = parseJsonNode(rawResponse);
    if (!isValidGradePayload(root)) {
      JsonNode repaired = tryRepairGradePayload(rawResponse, request);
      if (isValidGradePayload(repaired)) {
        root = repaired;
      }
    }
    if (root == null) {
      record.content = rawResponse.trim();
      record.wordCount = TextUtils.countEnglishWords(record.content);
      record.scoreText = "待人工复核";
      return;
    }

    JsonNode analysisNode = root.path("analysis");
    AppState.GradeAnalysis analysis = new AppState.GradeAnalysis();
    analysis.typeJudgment = analysisNode.path("typeJudgment").asText("");
    analysis.wordCountRisk = analysisNode.path("wordCountRisk").asText("");
    analysis.alignmentDiagnosis = analysisNode.path("alignmentDiagnosis").asText("");
    analysis.languageFitnessDiagnosis = analysisNode.path("languageFitnessDiagnosis").asText("");
    analysis.flowDiagnosis = analysisNode.path("flowDiagnosis").asText("");
    analysis.machineRiskDiagnosis = analysisNode.path("machineRiskDiagnosis").asText("");
    analysis.contentDiagnosis = analysisNode.path("contentDiagnosis").asText("");
    analysis.structureDiagnosis = firstNonBlank(
        analysisNode.path("structureDiagnosis").asText(""),
        analysis.alignmentDiagnosis
    );
    analysis.languageDiagnosis = firstNonBlank(
        analysisNode.path("languageDiagnosis").asText(""),
        analysis.languageFitnessDiagnosis
    );
    analysis.highlightDiagnosis = analysisNode.path("highlightDiagnosis").asText("");
    analysis.lossPointDiagnosis = analysisNode.path("lossPointDiagnosis").asText("");
    analysis.overallComment = analysisNode.path("overallComment").asText("");
    analysis.secondDraftGuidance = analysisNode.path("secondDraftGuidance").asText("");
    analysis.improvedEssay = analysisNode.path("improvedEssay").asText("");
    analysis.sentenceDiagnostics = readSentenceDiagnostics(analysisNode.path("sentenceDiagnostics"));
    if (analysisNode.has("weaknessProfile")) {
      AppState.WeaknessProfile profile = new AppState.WeaknessProfile();
      profile.headline = analysisNode.path("weaknessProfile").path("headline").asText("");
      profile.nextFocus = analysisNode.path("weaknessProfile").path("nextFocus").asText("");
      profile.sampleSize = analysisNode.path("weaknessProfile").path("sampleSize").asInt(1);
      JsonNode tagsNode = analysisNode.path("weaknessProfile").path("tags");
      if (tagsNode.isArray()) {
        for (JsonNode item : tagsNode) {
          AppState.WeaknessTag tag = new AppState.WeaknessTag();
          tag.code = item.path("code").asText("");
          tag.label = item.path("label").asText("");
          tag.hitCount = item.path("hitCount").asInt(1);
          profile.tags.add(tag);
        }
      }
      analysis.weaknessProfile = profile;
    }
    record.analysis = analysis;
    record.content = firstNonBlank(root.path("content").asText(""), rawResponse.trim());
    record.wordCount = root.path("wordCount").asInt(TextUtils.countEnglishWords(
        !TextUtils.isBlank(analysis.improvedEssay) ? analysis.improvedEssay : record.content
    ));
    record.scoreText = root.path("scoreText").asText("");
  }

  private JsonNode parseJsonNode(String rawResponse) {
    try {
      String jsonText = TextUtils.extractJsonObject(rawResponse);
      return objectMapper.readTree(jsonText);
    } catch (IOException error) {
      return null;
    }
  }

  private JsonNode tryRepairCoachPayload(
      String rawResponse,
      EssayTaskRequest request,
      CoachKnowledgeBaseService.CoachGuidance coachGuidance
  ) {
    try {
      String repaired = aiGatewayService.requestJsonText(
          "你是高考英语作文陪练结果修复器。你的唯一任务是把已有结果整理成严格 JSON。"
              + "不得改题型，不得改用户意图，不得输出 JSON 之外的任何内容。",
          buildCoachRepairPrompt(rawResponse, request, coachGuidance)
      );
      return parseJsonNode(repaired);
    } catch (RuntimeException error) {
      return null;
    }
  }

  private JsonNode tryRepairGradePayload(String rawResponse, EssayTaskRequest request) {
    try {
      String repaired = aiGatewayService.requestJsonText(
          "你是高考英语作文批改结果修复器。你的唯一任务是把已有批改结果整理成严格 JSON。"
              + "不得改题型，不得乱换结论，不得输出 JSON 之外的任何内容。",
          buildGradeRepairPrompt(rawResponse, request)
      );
      return parseJsonNode(repaired);
    } catch (RuntimeException error) {
      return null;
    }
  }

  private String buildCoachRepairPrompt(
      String rawResponse,
      EssayTaskRequest request,
      CoachKnowledgeBaseService.CoachGuidance coachGuidance
  ) {
    AppState.CoachPlan fallbackPlan = coachGuidance.getFallbackPlan();
    List<String> lines = new ArrayList<>();
    lines.add("请把下面这段原始输出整理成严格 JSON。");
    lines.add("题型：" + TextUtils.trimToEmpty(request.getEssayType()));
    lines.add("目标档次：" + TextUtils.trimToEmpty(request.getBandValue()));
    lines.add("如果原文里缺槽位，请优先用下面的知识库骨架补齐，不要另起炉灶。");
    lines.add("templateId: " + fallbackPlan.templateId);
    lines.add("scenario: " + fallbackPlan.scenario);
    lines.add("taskPurpose: " + fallbackPlan.taskPurpose);
    lines.add("stage: " + fallbackPlan.stage);
    lines.add("coachingMode: " + fallbackPlan.coachingMode);
    lines.add("typeJudgment: " + fallbackPlan.typeJudgment);
    lines.add("identityTone: " + fallbackPlan.identityTone);
    lines.add("officialLogic: " + fallbackPlan.officialLogic);
    lines.add("opening: " + fallbackPlan.opening);
    lines.add("body: " + fallbackPlan.body);
    lines.add("ending: " + fallbackPlan.ending);
    lines.add("clueReuse: " + fallbackPlan.clueReuse);
    lines.add("emotionalFlow: " + fallbackPlan.emotionalFlow);
    lines.add("secondOpeningBridge: " + fallbackPlan.secondOpeningBridge);
    lines.add("bandRecommendation: " + fallbackPlan.bandRecommendation);
    lines.add("bandReason: " + fallbackPlan.bandReason);
    lines.add("drillFocus: " + fallbackPlan.drillFocus);
    lines.add("successCheck: " + fallbackPlan.successCheck);
    lines.add("routeAction: " + fallbackPlan.routeAction);
    lines.add("routeReason: " + fallbackPlan.routeReason);
    lines.add("writingPriorities: " + String.join(" | ", fallbackPlan.writingPriorities));
    lines.add("drillTasks: " + String.join(" | ", fallbackPlan.drillTasks));
    lines.add("mustInclude: " + String.join(" | ", fallbackPlan.mustInclude));
    lines.add("riskPoints: " + String.join(" | ", fallbackPlan.riskPoints));
    lines.add("suggestedExpressions: " + String.join(" | ", fallbackPlan.suggestedExpressions));
    lines.add("只输出 JSON，结构如下：");
    lines.add("{\"content\":\"...\",\"wordCount\":0,\"scoreText\":\"\",\"coachPlan\":{\"stage\":\"prewrite\",\"coachingMode\":\"outline\",\"typeJudgment\":\"...\",\"identityTone\":\"...\",\"templateId\":\"...\",\"scenario\":\"...\",\"taskPurpose\":\"...\",\"officialLogic\":\"...\",\"opening\":\"...\",\"body\":\"...\",\"ending\":\"...\",\"clueReuse\":\"...\",\"emotionalFlow\":\"...\",\"secondOpeningBridge\":\"...\",\"bandRecommendation\":\"...\",\"bandReason\":\"...\",\"drillFocus\":\"...\",\"successCheck\":\"...\",\"routeAction\":\"continue_coach\",\"routeReason\":\"...\",\"writingPriorities\":[\"...\"],\"drillTasks\":[\"...\"],\"mustInclude\":[\"...\"],\"riskPoints\":[\"...\"],\"suggestedExpressions\":[\"...\"]}}");
    lines.add("原始输出：\n" + rawResponse);
    return String.join("\n\n", lines);
  }

  private String buildGradeRepairPrompt(String rawResponse, EssayTaskRequest request) {
    List<String> lines = new ArrayList<>();
    lines.add("请把下面这段原始批改输出整理成严格 JSON。");
    lines.add("工作模式：严格批改");
    lines.add("题型：" + TextUtils.trimToEmpty(request.getEssayType()));
    lines.add("目标档次：" + TextUtils.trimToEmpty(request.getBandValue()));
    if (!TextUtils.isBlank(request.getTaskContent())) {
      lines.add("题目内容：\n" + request.getTaskContent().trim());
    }
    if (!TextUtils.isBlank(request.getSourceMaterial())) {
      lines.add("补充材料：\n" + request.getSourceMaterial().trim());
    }
    if (!TextUtils.isBlank(request.getDraftText())) {
      lines.add("学生作文：\n" + request.getDraftText().trim());
    }
    lines.add("只输出 JSON，结构如下：");
    lines.add("{\"content\":\"完整批改报告，必须含【第一步】到【第四步】\",\"wordCount\":0,\"scoreText\":\"11分 / 15\","
        + "\"analysis\":{\"typeJudgment\":\"...\",\"wordCountRisk\":\"...\",\"alignmentDiagnosis\":\"...\","
        + "\"machineRiskDiagnosis\":\"...\",\"contentDiagnosis\":\"...\",\"structureDiagnosis\":\"...\","
        + "\"languageFitnessDiagnosis\":\"...\",\"languageDiagnosis\":\"...\",\"flowDiagnosis\":\"...\","
        + "\"highlightDiagnosis\":\"...\",\"lossPointDiagnosis\":\"...\",\"overallComment\":\"...\","
        + "\"sentenceDiagnostics\":[{\"original\":\"...\",\"diagnosis\":\"...\",\"revision\":\"...\"}],"
        + "\"secondDraftGuidance\":\"...\",\"improvedEssay\":\"英文提分稿\",\"weaknessProfile\":{\"headline\":\"...\","
        + "\"nextFocus\":\"...\",\"sampleSize\":1,\"tags\":[{\"code\":\"show\",\"label\":\"Show 不足\",\"hitCount\":1}]}}}");
    lines.add("如果原始输出缺少某些槽位，请根据原始内容和学生原文补齐，但不要另起炉灶。");
    lines.add("原始输出：\n" + rawResponse);
    return String.join("\n\n", lines);
  }

  private boolean isValidCoachPayload(JsonNode root) {
    return root != null && isValidCoachPlanNode(root.path("coachPlan"));
  }

  private boolean isValidGradePayload(JsonNode root) {
    return root != null && isValidGradeAnalysisNode(root.path("analysis"));
  }

  private boolean isValidCoachPlanNode(JsonNode node) {
    return node != null
        && node.isObject()
        && !TextUtils.isBlank(node.path("opening").asText(""))
        && !TextUtils.isBlank(node.path("body").asText(""))
        && !TextUtils.isBlank(node.path("ending").asText(""))
        && node.path("mustInclude").isArray()
        && node.path("mustInclude").size() > 0
        && node.path("riskPoints").isArray()
        && node.path("riskPoints").size() > 0
        && node.path("suggestedExpressions").isArray()
        && node.path("suggestedExpressions").size() > 0;
  }

  private boolean isValidGradeAnalysisNode(JsonNode node) {
    return node != null
        && node.isObject()
        && hasText(node, "typeJudgment")
        && hasText(node, "wordCountRisk")
        && hasText(node, "contentDiagnosis")
        && (hasText(node, "languageFitnessDiagnosis") || hasText(node, "languageDiagnosis"))
        && hasText(node, "machineRiskDiagnosis")
        && hasText(node, "overallComment")
        && node.path("sentenceDiagnostics").isArray()
        && node.path("sentenceDiagnostics").size() > 0;
  }

  private AppState.CoachPlan parseCoachPlan(JsonNode node) {
    if (node == null || !node.isObject()) {
      return null;
    }
    AppState.CoachPlan plan = new AppState.CoachPlan();
    plan.stage = node.path("stage").asText("");
    plan.coachingMode = node.path("coachingMode").asText("");
    plan.typeJudgment = node.path("typeJudgment").asText("");
    plan.identityTone = node.path("identityTone").asText("");
    plan.templateId = node.path("templateId").asText("");
    plan.scenario = node.path("scenario").asText("");
    plan.taskPurpose = node.path("taskPurpose").asText("");
    plan.officialLogic = node.path("officialLogic").asText("");
    plan.opening = node.path("opening").asText("");
    plan.body = node.path("body").asText("");
    plan.ending = node.path("ending").asText("");
    plan.clueReuse = node.path("clueReuse").asText("");
    plan.emotionalFlow = node.path("emotionalFlow").asText("");
    plan.secondOpeningBridge = node.path("secondOpeningBridge").asText("");
    plan.bandRecommendation = node.path("bandRecommendation").asText("");
    plan.bandReason = node.path("bandReason").asText("");
    plan.drillFocus = node.path("drillFocus").asText("");
    plan.successCheck = node.path("successCheck").asText("");
    plan.routeAction = node.path("routeAction").asText("");
    plan.routeReason = node.path("routeReason").asText("");
    plan.writingPriorities = readStringList(node.path("writingPriorities"));
    plan.drillTasks = readStringList(node.path("drillTasks"));
    plan.mustInclude = readStringList(node.path("mustInclude"));
    plan.riskPoints = readStringList(node.path("riskPoints"));
    plan.suggestedExpressions = readStringList(node.path("suggestedExpressions"));
    return plan;
  }

  private List<AppState.SentenceDiagnosis> readSentenceDiagnostics(JsonNode node) {
    if (node == null || !node.isArray()) {
      return List.of();
    }
    List<AppState.SentenceDiagnosis> items = new ArrayList<>();
    for (JsonNode entry : node) {
      AppState.SentenceDiagnosis item = new AppState.SentenceDiagnosis();
      item.original = firstNonBlank(entry.path("original").asText(""), entry.path("source").asText(""));
      item.diagnosis = firstNonBlank(entry.path("diagnosis").asText(""), entry.path("teacherDiagnosis").asText(""));
      item.revision = firstNonBlank(entry.path("revision").asText(""), entry.path("rewrite").asText(""));
      if (!TextUtils.isBlank(item.original) || !TextUtils.isBlank(item.diagnosis) || !TextUtils.isBlank(item.revision)) {
        items.add(item);
      }
    }
    return items;
  }

  private AppState.CoachPlan mergeCoachPlan(AppState.CoachPlan parsed, AppState.CoachPlan fallback) {
    AppState.CoachPlan base = fallback == null ? new AppState.CoachPlan() : fallback;
    if (parsed == null) {
      return base;
    }

    AppState.CoachPlan merged = new AppState.CoachPlan();
    merged.stage = firstNonBlank(parsed.stage, base.stage);
    merged.coachingMode = firstNonBlank(parsed.coachingMode, base.coachingMode);
    merged.typeJudgment = firstNonBlank(parsed.typeJudgment, base.typeJudgment);
    merged.identityTone = firstNonBlank(parsed.identityTone, base.identityTone);
    merged.templateId = firstNonBlank(parsed.templateId, base.templateId);
    merged.scenario = firstNonBlank(parsed.scenario, base.scenario);
    merged.taskPurpose = firstNonBlank(parsed.taskPurpose, base.taskPurpose);
    merged.officialLogic = firstNonBlank(parsed.officialLogic, base.officialLogic);
    merged.opening = firstNonBlank(parsed.opening, base.opening);
    merged.body = firstNonBlank(parsed.body, base.body);
    merged.ending = firstNonBlank(parsed.ending, base.ending);
    merged.clueReuse = firstNonBlank(parsed.clueReuse, base.clueReuse);
    merged.emotionalFlow = firstNonBlank(parsed.emotionalFlow, base.emotionalFlow);
    merged.secondOpeningBridge = firstNonBlank(parsed.secondOpeningBridge, base.secondOpeningBridge);
    merged.bandRecommendation = firstNonBlank(parsed.bandRecommendation, base.bandRecommendation);
    merged.bandReason = firstNonBlank(parsed.bandReason, base.bandReason);
    merged.drillFocus = firstNonBlank(parsed.drillFocus, base.drillFocus);
    merged.successCheck = firstNonBlank(parsed.successCheck, base.successCheck);
    merged.routeAction = firstNonBlank(parsed.routeAction, base.routeAction);
    merged.routeReason = firstNonBlank(parsed.routeReason, base.routeReason);
    merged.writingPriorities = mergeDistinct(parsed.writingPriorities, base.writingPriorities);
    merged.drillTasks = mergeDistinct(parsed.drillTasks, base.drillTasks);
    merged.mustInclude = mergeDistinct(parsed.mustInclude, base.mustInclude);
    merged.riskPoints = mergeDistinct(parsed.riskPoints, base.riskPoints);
    merged.suggestedExpressions = mergeDistinct(parsed.suggestedExpressions, base.suggestedExpressions);
    return merged;
  }

  private List<String> readStringList(JsonNode node) {
    if (node == null || !node.isArray()) {
      return List.of();
    }
    List<String> values = new ArrayList<>();
    for (JsonNode item : node) {
      String value = TextUtils.trimToEmpty(item.asText(""));
      if (!value.isEmpty()) {
        values.add(value);
      }
    }
    return values;
  }

  private List<String> mergeDistinct(List<String> primary, List<String> fallback) {
    LinkedHashSet<String> merged = new LinkedHashSet<>();
    if (primary != null) {
      primary.stream().map(TextUtils::trimToEmpty).filter(item -> !item.isEmpty()).forEach(merged::add);
    }
    if (fallback != null) {
      fallback.stream().map(TextUtils::trimToEmpty).filter(item -> !item.isEmpty()).forEach(merged::add);
    }
    return new ArrayList<>(merged);
  }

  private String firstNonBlank(String preferred, String fallback) {
    return TextUtils.isBlank(preferred) ? TextUtils.trimToEmpty(fallback) : preferred.trim();
  }

  private String buildCoachMarkdown(AppState.CoachPlan plan) {
    StringBuilder builder = new StringBuilder();
    builder.append("### 陪练定位\n");
    if (!TextUtils.isBlank(plan.stage)) {
      builder.append("- 当前阶段：").append(plan.stage).append("\n");
    }
    if (!TextUtils.isBlank(plan.coachingMode)) {
      builder.append("- 当前模式：").append(plan.coachingMode).append("\n");
    }
    if (!TextUtils.isBlank(plan.typeJudgment)) {
      builder.append("- 题型判断：").append(plan.typeJudgment).append("\n");
    }
    if (!TextUtils.isBlank(plan.identityTone)) {
      builder.append("- 身份与语气：").append(plan.identityTone).append("\n");
    }
    if (!TextUtils.isBlank(plan.scenario)) {
      builder.append("- 场景：").append(plan.scenario).append("\n");
    }
    if (!TextUtils.isBlank(plan.taskPurpose)) {
      builder.append("- 任务目的：").append(plan.taskPurpose).append("\n");
    }
    if (!TextUtils.isBlank(plan.officialLogic)) {
      builder.append("- 阅卷抓手：").append(plan.officialLogic).append("\n");
    }

    builder.append("\n### 写作骨架\n");
    builder.append("- 开头怎么起：").append(plan.opening).append("\n");
    builder.append("- 中段怎么承：").append(plan.body).append("\n");
    builder.append("- 结尾怎么收：").append(plan.ending).append("\n");

    if (!plan.writingPriorities.isEmpty()) {
      builder.append("\n### 写作优先级\n");
      for (String item : plan.writingPriorities) {
        builder.append("- ").append(item).append("\n");
      }
    }

    if (!TextUtils.isBlank(plan.clueReuse)
        || !TextUtils.isBlank(plan.emotionalFlow)
        || !TextUtils.isBlank(plan.secondOpeningBridge)) {
      builder.append("\n### 续写抓手\n");
      if (!TextUtils.isBlank(plan.clueReuse)) {
        builder.append("- 线索回收：").append(plan.clueReuse).append("\n");
      }
      if (!TextUtils.isBlank(plan.emotionalFlow)) {
        builder.append("- 情绪推进：").append(plan.emotionalFlow).append("\n");
      }
      if (!TextUtils.isBlank(plan.secondOpeningBridge)) {
        builder.append("- 第二段起句衔接：").append(plan.secondOpeningBridge).append("\n");
      }
    }

    if (!plan.mustInclude.isEmpty()) {
      builder.append("\n### 本题必写\n");
      for (String item : plan.mustInclude) {
        builder.append("- ").append(item).append("\n");
      }
    }

    if (!plan.riskPoints.isEmpty()) {
      builder.append("\n### 易失分点\n");
      for (String item : plan.riskPoints) {
        builder.append("- ").append(item).append("\n");
      }
    }

    if (!plan.suggestedExpressions.isEmpty()) {
      builder.append("\n### 可直接借用表达\n");
      for (String item : plan.suggestedExpressions) {
        builder.append("- ").append(item).append("\n");
      }
    }

    if (!TextUtils.isBlank(plan.bandRecommendation) || !TextUtils.isBlank(plan.bandReason)) {
      builder.append("\n### 目标档次建议\n");
      if (!TextUtils.isBlank(plan.bandRecommendation)) {
        builder.append("- ").append(plan.bandRecommendation).append("\n");
      }
      if (!TextUtils.isBlank(plan.bandReason)) {
        builder.append("- ").append(plan.bandReason).append("\n");
      }
    }

    if (!TextUtils.isBlank(plan.drillFocus) || !plan.drillTasks.isEmpty() || !TextUtils.isBlank(plan.successCheck)) {
      builder.append("\n### 弱点特训\n");
      if (!TextUtils.isBlank(plan.drillFocus)) {
        builder.append("- 训练重点：").append(plan.drillFocus).append("\n");
      }
      for (String item : plan.drillTasks) {
        builder.append("- ").append(item).append("\n");
      }
      if (!TextUtils.isBlank(plan.successCheck)) {
        builder.append("- 达标标准：").append(plan.successCheck).append("\n");
      }
    }

    if (!TextUtils.isBlank(plan.routeReason) || !TextUtils.isBlank(plan.routeAction)) {
      builder.append("\n### 下一步建议\n");
      if (!TextUtils.isBlank(plan.routeReason)) {
        builder.append("- ").append(plan.routeReason).append("\n");
      }
      if (!TextUtils.isBlank(plan.routeAction)) {
        builder.append("- 建议动作：").append(plan.routeAction).append("\n");
      }
    }

    return builder.toString().trim();
  }

  private String resolveStreamText(AppState.EssayRecord record) {
    if (record.analysis != null && !TextUtils.isBlank(record.analysis.improvedEssay)) {
      return record.analysis.improvedEssay;
    }
    return record.content;
  }

  private String buildSystemPrompt(
      EssayTaskRequest request,
      CoachKnowledgeBaseService.CoachGuidance coachGuidance
  ) {
    String mode = TextUtils.lower(request.getMode());
    if ("coach".equals(mode)) {
      return "你是新高考英语作文陪练教练。你必须严格服从用户提供的结构化知识库和命中模板。"
          + "输出必须是中文指导，但示例句和可直接套用表达必须用英文。"
          + "不要直接代写整篇范文。你必须只输出 JSON，结构如下："
          + "{\"content\":\"完整陪练报告，需覆盖审题、骨架、失分点、下一步建议\",\"wordCount\":0,\"scoreText\":\"\","
          + "\"coachPlan\":{\"stage\":\"prewrite\",\"coachingMode\":\"outline\",\"typeJudgment\":\"...\",\"identityTone\":\"...\","
          + "\"templateId\":\"...\",\"scenario\":\"...\",\"taskPurpose\":\"...\",\"officialLogic\":\"...\","
          + "\"opening\":\"...\",\"body\":\"...\",\"ending\":\"...\",\"clueReuse\":\"...\",\"emotionalFlow\":\"...\","
          + "\"secondOpeningBridge\":\"...\",\"bandRecommendation\":\"...\",\"bandReason\":\"...\",\"drillFocus\":\"...\","
          + "\"successCheck\":\"...\",\"routeAction\":\"continue_coach\",\"routeReason\":\"...\","
          + "\"writingPriorities\":[\"...\"],\"drillTasks\":[\"...\"],\"mustInclude\":[\"...\"],"
          + "\"riskPoints\":[\"...\"],\"suggestedExpressions\":[\"...\"]}}";
    }
    return "你是新高考英语阅卷组长。你必须严格遵守用户提供的结构化评分知识库。"
        + "除第四步整篇修改稿与英文原句外，其余诊断一律用中文。"
        + "请严格按阅卷思路批改作文，避免 AI 腔，优先稳分、证据化、保守估分。"
        + "必须只输出 JSON，结构如下："
        + "{\"content\":\"完整批改报告，必须含【第一步：硬性指标初筛】【第二步：分项诊断与保守估分】"
        + "【第三步：逐句无情诊断】【第四步：高分级整篇修改稿】\",\"wordCount\":123,\"scoreText\":\"11分 / 15\",\"analysis\":{"
        + "\"typeJudgment\":\"...\",\"wordCountRisk\":\"...\",\"alignmentDiagnosis\":\"...\",\"machineRiskDiagnosis\":\"...\","
        + "\"contentDiagnosis\":\"...\",\"structureDiagnosis\":\"...\",\"languageFitnessDiagnosis\":\"...\","
        + "\"languageDiagnosis\":\"...\",\"flowDiagnosis\":\"...\",\"highlightDiagnosis\":\"...\",\"lossPointDiagnosis\":\"...\","
        + "\"overallComment\":\"...\",\"sentenceDiagnostics\":[{\"original\":\"...\",\"diagnosis\":\"...\",\"revision\":\"...\"}],"
        + "\"secondDraftGuidance\":\"...\",\"improvedEssay\":\"英文提分稿\",\"weaknessProfile\":{"
        + "\"headline\":\"...\",\"nextFocus\":\"...\",\"sampleSize\":1,\"tags\":[{\"code\":\"show\",\"label\":\"Show 不足\",\"hitCount\":1}]}}}";
  }

  private String buildUserPrompt(
      EssayTaskRequest request,
      CoachKnowledgeBaseService.CoachGuidance coachGuidance
  ) {
    List<String> lines = new ArrayList<>();
    lines.add("工作模式：" + TextUtils.trimToEmpty(request.getMode()));
    lines.add("题型：" + TextUtils.trimToEmpty(request.getEssayType()));
    lines.add("目标档次：" + TextUtils.trimToEmpty(request.getBandValue()));
    if ("coach".equals(TextUtils.lower(request.getMode()))) {
      lines.add("陪练阶段：" + TextUtils.trimToEmpty(coachGuidance.getStage()));
      lines.add("陪练模式：" + TextUtils.trimToEmpty(coachGuidance.getCoachMode()));
    }
    if (!TextUtils.isBlank(request.getTaskContent())) {
      lines.add("题目内容：\n" + request.getTaskContent().trim());
    }
    if (!TextUtils.isBlank(request.getSourceMaterial())) {
      lines.add("补充材料：\n" + request.getSourceMaterial().trim());
    }
    if (!TextUtils.isBlank(request.getDraftText())) {
      lines.add("学生作文：\n" + request.getDraftText().trim());
    }
    if (!TextUtils.isBlank(request.getRequirements())) {
      lines.add("额外要求：\n" + request.getRequirements().trim());
    }
    String mode = TextUtils.lower(request.getMode());
    lines.add("写作约束：");
    lines.add("1. 语言自然，不要 Furthermore/Moreover/In addition/In a nutshell 等机器感连接词。");
    lines.add("2. 尽量使用高中阶段自然高级词汇和真实生活细节。");
    lines.add("3. 续写要回收线索、照应段首句，情绪高潮处允许短句。");
    if ("grade".equals(mode)) {
      lines.add("4. 你只负责批改已有作文，不要从零另写一篇完全不同的新作文。");
      lines.add("5. 必须严格按四个固定部分完成批改：第一步硬性指标初筛；第二步分项诊断与保守估分；第三步逐句无情诊断；第四步高分级整篇修改稿。");
      lines.add("6. 第一步必须覆盖：题型判断、字数核查、切题与结构初筛、模板感与机器感筛查。");
      lines.add("7. 第二步必须单独给出：题型判断、字数与档位风险、要点覆盖与协同性、词汇与语法得体度、隐性衔接与呼吸感、模板感 / 机器感风险、保守估分、一句总评。");
      lines.add("8. 第三步必须挑出 2 到 3 句最典型失分句，分别给出原句、诊断、提分改法。");
      lines.add("9. 所有判断都必须基于学生作文里的具体文本证据。不要直接说 AI 写的，只能说存在明显模板化 / 机器感风险，并说明证据。");
      lines.add("10. 整篇提分稿默认保留学生原有核心意思、结构和情节，不要大幅魔改。");
      lines.add("11. 如果是读后续写但原文材料或两段段首句不完整，必须明确说明只能做有限诊断。");
      lines.add("12. 批改时要指出内容、结构、语言、亮点、失分点，并给二稿提升建议和整篇提分稿。");
    } else {
      lines.add("4. 陪练时要严格贴合知识库骨架，输出可直接下笔的中文指导，不要直接代写整篇范文。");
      lines.add("5. 先判断用户所处阶段是写前、写中还是写后，再按当前陪练模式给最轻最有用的指导。");
      lines.add("6. 应用文优先看任务要求、身份语气和要点顺序；读后续写优先看线索、动作链和第二段段首句衔接。");
      lines.add("7. 结果里必须给出下一步建议：继续陪练、直接下笔，或转去严格批改。");
      if ("sentence_upgrade".equals(coachGuidance.getCoachMode())) {
        lines.add("8. 当前是句子升级模式：优先处理用户已有句子或草稿，去模板感，调语气和节奏，不要整篇另写。");
      }
      if ("routing".equals(coachGuidance.getCoachMode())) {
        lines.add("8. 当前是分流建议模式：请明确判断下一步最值钱的动作，并说明理由。");
      }
    }
    String knowledgeContext = coachKnowledgeBaseService.buildPromptContext(request, coachGuidance);
    if (!TextUtils.isBlank(knowledgeContext)) {
      lines.add(knowledgeContext);
    }
    return String.join("\n\n", lines);
  }

  private boolean hasText(JsonNode node, String fieldName) {
    return node != null && !TextUtils.isBlank(node.path(fieldName).asText(""));
  }

  private String resolveBandLabel(String band) {
    if ("band1".equalsIgnoreCase(band)) {
      return "进阶";
    }
    if ("band2".equalsIgnoreCase(band)) {
      return "学霸";
    }
    if ("band3".equalsIgnoreCase(band)) {
      return "满分";
    }
    return "";
  }

  public static class EssayExecution {
    private final AppState.EssayRecord record;
    private final String streamText;

    public EssayExecution(AppState.EssayRecord record, String streamText) {
      this.record = record;
      this.streamText = streamText;
    }

    public AppState.EssayRecord getRecord() {
      return record;
    }

    public String getStreamText() {
      return streamText;
    }
  }
}
