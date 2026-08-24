package com.gaokao.essay.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gaokao.essay.backend.model.ApiException;
import com.gaokao.essay.backend.model.AuthenticatedUser;
import com.gaokao.essay.backend.model.ModelEssayResult;
import com.gaokao.essay.backend.store.AppState;
import com.gaokao.essay.backend.util.TextUtils;
import java.time.Clock;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ModelEssayService {

  private final HistoryService historyService;
  private final AiGatewayService aiGatewayService;
  private final MembershipService membershipService;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public ModelEssayService(
      HistoryService historyService,
      AiGatewayService aiGatewayService,
      MembershipService membershipService,
      ObjectMapper objectMapper,
      Clock clock
  ) {
    this.historyService = historyService;
    this.aiGatewayService = aiGatewayService;
    this.membershipService = membershipService;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  public ModelEssayResult getOrGenerate(AuthenticatedUser user, String recordId, boolean regenerate) {
    AppState.EssayRecord record = historyService.getRecord(user, TextUtils.trimToEmpty(recordId));
    requireEligibleRecord(record);

    if (!regenerate && isComplete(record.analysis.modelEssay)) {
      return record.analysis.modelEssay;
    }

    MembershipService.UsageReservation reservation = null;
    try {
      if (regenerate) {
        reservation = membershipService.reserveEssayAccess(user);
      }
      ModelEssayResult result = requestResult(record);
      result.targetBand = TextUtils.isBlank(result.targetBand) ? "高分提升版" : result.targetBand.trim();
      result.generatedAt = clock.millis();
      record.analysis.modelEssay = result;
      historyService.saveRecord(record);
      return result;
    } catch (RuntimeException error) {
      if (reservation != null) {
        membershipService.releaseReservation(reservation);
      }
      throw error;
    }
  }

  private void requireEligibleRecord(AppState.EssayRecord record) {
    if (record == null
        || !"grade".equals(record.mode)
        || !"SUCCESS".equals(record.taskStatus)
        || record.analysis == null
        || record.promptSnapshot == null
        || TextUtils.isBlank(record.promptSnapshot.draftText)) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "MODEL_ESSAY_NOT_AVAILABLE",
          "仅支持为已完成的严格批改生成同题范文"
      );
    }
  }

  private ModelEssayResult requestResult(AppState.EssayRecord record) {
    String raw = aiGatewayService.requestJsonText(buildSystemPrompt(), buildUserPrompt(record));
    ModelEssayResult parsed = parse(raw);
    if (isComplete(parsed)) {
      return parsed;
    }

    String repaired = aiGatewayService.requestJsonText(
        "你是 JSON 修复器。只把已有同题范文结果整理成指定 JSON，不增删核心内容，不输出 JSON 之外的文字。",
        buildRepairPrompt(raw)
    );
    parsed = parse(repaired);
    if (isComplete(parsed)) {
      return parsed;
    }
    throw new ApiException(
        HttpStatus.BAD_GATEWAY,
        "MODEL_ESSAY_INVALID_RESPONSE",
        "范文结果格式异常，请稍后重试"
    );
  }

  private ModelEssayResult parse(String raw) {
    try {
      String json = TextUtils.extractJsonObject(TextUtils.trimToEmpty(raw));
      if (TextUtils.isBlank(json)) {
        return null;
      }
      return objectMapper.readValue(json, ModelEssayResult.class);
    } catch (JsonProcessingException | IllegalArgumentException error) {
      return null;
    }
  }

  private boolean isComplete(ModelEssayResult result) {
    return result != null
        && !TextUtils.isBlank(result.modelEssay)
        && result.paragraphInsights != null
        && result.expressionComparisons != null
        && result.reusableExpressions != null;
  }

  private String buildSystemPrompt() {
    return """
        你是高考英语写作教练。请基于学生已经完成的作文和严格批改结果，生成一篇同题高分提升版范文，并帮助学生理解为什么更好。
        不得改变题型和题意，不得虚构题面要求，不得输出所谓唯一标准答案。
        只输出严格 JSON，不要 Markdown，不要代码围栏。
        JSON 字段必须为：targetBand、modelEssay、paragraphInsights、expressionComparisons、reusableExpressions。
        paragraphInsights 每项包含 title、purpose、keyExpression；expressionComparisons 每项包含 original、recommended、reason。
        """;
  }

  private String buildUserPrompt(AppState.EssayRecord record) {
    AppState.PromptSnapshot prompt = record.promptSnapshot;
    AppState.GradeAnalysis analysis = record.analysis;
    return String.join("\n", List.of(
        "题型：" + TextUtils.trimToEmpty(record.essayType),
        "目标档位：高分提升版",
        "题目：" + TextUtils.trimToEmpty(prompt.taskContent),
        "续写原文：" + TextUtils.trimToEmpty(prompt.sourceMaterial),
        "题目要求：" + TextUtils.trimToEmpty(prompt.requirements),
        "学生原文：" + TextUtils.trimToEmpty(prompt.draftText),
        "总体诊断：" + TextUtils.trimToEmpty(analysis.overallComment),
        "内容诊断：" + TextUtils.trimToEmpty(analysis.contentDiagnosis),
        "结构诊断：" + TextUtils.trimToEmpty(analysis.structureDiagnosis),
        "语言诊断：" + TextUtils.trimToEmpty(analysis.languageDiagnosis),
        "失分点：" + TextUtils.trimToEmpty(analysis.lossPointDiagnosis),
        "生成 3 至 5 个段落得分点、3 至 5 组表达对比和 3 至 6 个可复用表达。"
    ));
  }

  private String buildRepairPrompt(String raw) {
    return """
        请整理为以下结构：
        {"targetBand":"高分提升版","modelEssay":"...","paragraphInsights":[{"title":"...","purpose":"...","keyExpression":"..."}],"expressionComparisons":[{"original":"...","recommended":"...","reason":"..."}],"reusableExpressions":["..."]}
        原始结果：
        """ + TextUtils.trimToEmpty(raw);
  }
}
