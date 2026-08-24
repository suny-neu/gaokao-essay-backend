package com.gaokao.essay.backend.store;

import com.gaokao.essay.backend.model.ModelEssayResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AppState {
  public Map<String, UserState> users = new LinkedHashMap<>();
  public Map<String, EssayRecord> essays = new LinkedHashMap<>();
  public Map<String, List<String>> userEssayIds = new LinkedHashMap<>();
  public Map<String, SubscriptionState> subscriptions = new LinkedHashMap<>();
  public Map<String, UsageQuotaState> usageQuotas = new LinkedHashMap<>();
  public Map<String, PaymentOrderState> paymentOrders = new LinkedHashMap<>();
  public Map<String, SessionToken> sessions = new LinkedHashMap<>();

  public static class UserState {
    public String userId = "";
    public String openId = "";
    public String createdAt = "";
    public String lastLoginAt = "";
    public SubscriptionState subscription = new SubscriptionState();
  }

  public static class SubscriptionState {
    public boolean active = false;
    public String status = "INACTIVE";
    public String planCode = "";
    public String planName = "";
    public String startedAt = "";
    public String expiresAt = "";
    public boolean autoRenew = false;
    public String provider = "";
    public String providerReference = "";
    public String updatedAt = "";
  }

  public static class UsageQuotaState {
    public String userId = "";
    public String quotaType = "";
    public int usedCount = 0;
    public int limitCount = 0;
    public String updatedAt = "";
  }

  public static class SessionToken {
    public String token = "";
    public String userId = "";
    public String openId = "";
    public long expiresAtEpochSeconds = 0L;
  }

  public static class PaymentOrderState {
    public String outTradeNo = "";
    public String orderId = "";
    public String userId = "";
    public String openId = "";
    public String planCode = "";
    public String planName = "";
    public int amountFen = 0;
    public String currency = "CNY";
    public String status = "CREATED";
    public boolean autoRenew = false;
    public String description = "";
    public String prepayId = "";
    public String transactionId = "";
    public String provider = "wechatpay";
    public String providerReference = "";
    public String payloadJson = "";
    public String paidAt = "";
    public String createdAt = "";
    public String updatedAt = "";
  }

  public static class EssayRecord {
    public String id = "";
    public String clientRequestId = "";
    public String userId = "";
    public String openId = "";
    public String mode = "";
    public String essayType = "";
    public String band = "";
    public String bandLabel = "";
    public String bandValue = "";
    public long createdAt = 0L;
    public String content = "";
    public int wordCount = 0;
    public String scoreText = "";
    public String summary = "";
    public String source = "remote";
    public String taskStatus = "SUCCESS";
    public PromptSnapshot promptSnapshot = new PromptSnapshot();
    public CoachPlan coachPlan = null;
    public GradeAnalysis analysis = null;
  }

  public static class PromptSnapshot {
    public String taskContent = "";
    public String sourceMaterial = "";
    public String draftText = "";
    public String requirements = "";
  }

  public static class GradeAnalysis {
    public String typeJudgment = "";
    public String wordCountRisk = "";
    public String alignmentDiagnosis = "";
    public String languageFitnessDiagnosis = "";
    public String flowDiagnosis = "";
    public String machineRiskDiagnosis = "";
    public String contentDiagnosis = "";
    public String structureDiagnosis = "";
    public String languageDiagnosis = "";
    public String highlightDiagnosis = "";
    public String lossPointDiagnosis = "";
    public String overallComment = "";
    public String secondDraftGuidance = "";
    public String improvedEssay = "";
    public List<ScoreDimension> scoreDimensions = new ArrayList<>();
    public List<SentenceDiagnosis> sentenceDiagnostics = new ArrayList<>();
    public WeaknessProfile weaknessProfile = null;
    public ModelEssayResult modelEssay = null;
  }

  public static class ScoreDimension {
    public String code = "";
    public String label = "";
    public double score = 0.0;
    public double maxScore = 0.0;
  }

  public static class SentenceDiagnosis {
    public String kind = "";
    public String errorType = "";
    public boolean legacyInferred = false;
    public String original = "";
    public String diagnosis = "";
    public String revision = "";
  }

  public static class CoachPlan {
    public String stage = "";
    public String coachingMode = "";
    public String typeJudgment = "";
    public String identityTone = "";
    public String templateId = "";
    public String scenario = "";
    public String taskPurpose = "";
    public String officialLogic = "";
    public String opening = "";
    public String body = "";
    public String ending = "";
    public String clueReuse = "";
    public String emotionalFlow = "";
    public String secondOpeningBridge = "";
    public String bandRecommendation = "";
    public String bandReason = "";
    public String drillFocus = "";
    public String successCheck = "";
    public String routeAction = "";
    public String routeReason = "";
    public List<String> writingPriorities = new ArrayList<>();
    public List<String> drillTasks = new ArrayList<>();
    public List<String> mustInclude = new ArrayList<>();
    public List<String> riskPoints = new ArrayList<>();
    public List<String> suggestedExpressions = new ArrayList<>();
  }

  public static class WeaknessProfile {
    public String headline = "";
    public String nextFocus = "";
    public int sampleSize = 1;
    public List<WeaknessTag> tags = new ArrayList<>();
  }

  public static class WeaknessTag {
    public String code = "";
    public String label = "";
    public int hitCount = 1;
  }
}
