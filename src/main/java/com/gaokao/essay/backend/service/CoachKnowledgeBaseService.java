package com.gaokao.essay.backend.service;

import com.gaokao.essay.backend.config.GaokaoProperties;
import com.gaokao.essay.backend.model.CoachTemplate;
import com.gaokao.essay.backend.model.EssayTaskRequest;
import com.gaokao.essay.backend.repository.CoachTemplateRepository;
import com.gaokao.essay.backend.store.AppState;
import com.gaokao.essay.backend.util.TextUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class CoachKnowledgeBaseService {

  private static final Pattern CHINESE_POINT = Pattern.compile("(?:（|\\()(\\d+)(?:）|\\))\\s*([^\\n]+)");
  private static final Pattern NORMAL_POINT = Pattern.compile("(?m)^\\s*\\d+[\\.、]\\s*([^\\n]+)");

  private final GaokaoProperties properties;
  private final CoachTemplateRepository coachTemplateRepository;

  public CoachKnowledgeBaseService(
      GaokaoProperties properties,
      CoachTemplateRepository coachTemplateRepository
  ) {
    this.properties = properties;
    this.coachTemplateRepository = coachTemplateRepository;
  }

  public boolean isEnabled() {
    return properties.getKnowledge().isEnabled();
  }

  public CoachGuidance prepareKnowledge(EssayTaskRequest request) {
    if (request == null) {
      String stage = "prewrite";
      String coachMode = "outline";
      return new CoachGuidance(
          List.of(),
          List.of(),
          List.of(),
          null,
          stage,
          coachMode,
          buildGenericPlan(null, "", stage, coachMode, List.of(), List.of())
      );
    }

    String essayType = TextUtils.lower(request.getEssayType());
    String stage = resolveCoachStage(request);
    String coachMode = resolveCoachMode(request, stage);
    List<String> points = extractPoints(request.getTaskContent());
    List<String> starters = extractLikelyStarters(request);
    List<CoachTemplate> matchedTemplates = isEnabled() ? matchTemplates(request, essayType) : List.of();
    CoachTemplate primaryTemplate = matchedTemplates.isEmpty() ? null : matchedTemplates.get(0);
    AppState.CoachPlan fallbackPlan = primaryTemplate != null
        ? buildPlanFromTemplate(primaryTemplate, request, essayType, stage, coachMode, points, starters)
        : buildGenericPlan(request, essayType, stage, coachMode, points, starters);
    return new CoachGuidance(points, starters, matchedTemplates, primaryTemplate, stage, coachMode, fallbackPlan);
  }

  public String buildPromptContext(EssayTaskRequest request, CoachGuidance guidance) {
    if (request == null || guidance == null) {
      return "";
    }

    String mode = TextUtils.lower(request.getMode());
    String essayType = TextUtils.lower(request.getEssayType());
    AppState.CoachPlan fallbackPlan = guidance.getFallbackPlan();
    List<String> lines = new ArrayList<>();
    lines.add("【结构化陪练知识库约束】");
    lines.add("- 本次输出必须先服从写作骨架，再追求表达亮点。");
    lines.add("- 当前陪练阶段：" + guidance.getStage());
    lines.add("- 当前陪练模式：" + guidance.getCoachMode());
    lines.add("- 你不能自由发散，必须按照 coachPlan 槽位返回：stage / coachingMode / opening / body / ending / mustInclude / riskPoints / suggestedExpressions / routeAction / routeReason。");

    appendSentenceModeRules(lines, guidance.getCoachMode());

    if ("application".equals(essayType)) {
      appendApplicationRules(lines, request, guidance);
    } else if ("continuation".equals(essayType)) {
      appendContinuationRules(lines, request, guidance);
    }

    if (guidance.getPrimaryTemplate() != null) {
      appendMatchedTemplate(lines, guidance.getPrimaryTemplate(), fallbackPlan);
    } else {
      lines.add("- 当前未命中专属模板，先按通用高分骨架组织内容。");
    }

    if ("grade".equals(mode)) {
      appendGradeRules(lines);
    } else {
      appendCoachRules(lines, fallbackPlan);
    }

    return String.join("\n", lines);
  }

  private List<CoachTemplate> matchTemplates(EssayTaskRequest request, String essayType) {
    if (TextUtils.isBlank(essayType)) {
      return List.of();
    }

    List<CoachTemplate> templates = coachTemplateRepository.findEnabledByEssayType(essayType);
    if (templates.isEmpty()) {
      return List.of();
    }

    String corpus = TextUtils.lower(String.join("\n",
        TextUtils.trimToEmpty(request.getTaskContent()),
        TextUtils.trimToEmpty(request.getSourceMaterial()),
        TextUtils.trimToEmpty(request.getRequirements()),
        TextUtils.trimToEmpty(request.getDraftText())
    ));

    List<ScoredTemplate> scoredTemplates = new ArrayList<>();
    for (CoachTemplate template : templates) {
      int score = scoreTemplate(template, corpus);
      scoredTemplates.add(new ScoredTemplate(template, score));
    }

    scoredTemplates.sort(Comparator
        .comparingInt(ScoredTemplate::score).reversed()
        .thenComparingInt(item -> item.template().getSortOrder())
        .thenComparing(item -> item.template().getId()));

    List<CoachTemplate> matched = scoredTemplates.stream()
        .filter(item -> item.score() > 0)
        .limit(3)
        .map(ScoredTemplate::template)
        .toList();

    if (!matched.isEmpty()) {
      return matched;
    }

    return templates.stream()
        .sorted(Comparator.comparingInt(CoachTemplate::getSortOrder).thenComparing(CoachTemplate::getId))
        .limit(3)
        .toList();
  }

  private int scoreTemplate(CoachTemplate template, String corpus) {
    int score = 0;
    if (containsNormalized(corpus, template.getScenario())) {
      score += 8;
    }
    if (containsNormalized(corpus, template.getTaskPurpose())) {
      score += 6;
    }
    for (String keyword : template.getTriggerKeywords()) {
      if (containsNormalized(corpus, keyword)) {
        score += 5;
      }
    }
    for (String point : template.getMustInclude()) {
      if (containsNormalized(corpus, point)) {
        score += 2;
      }
    }
    return score;
  }

  private boolean containsNormalized(String corpus, String candidate) {
    String normalizedCandidate = TextUtils.lower(candidate);
    return !TextUtils.isBlank(normalizedCandidate) && corpus.contains(normalizedCandidate);
  }

  private AppState.CoachPlan buildPlanFromTemplate(
      CoachTemplate template,
      EssayTaskRequest request,
      String essayType,
      String stage,
      String coachMode,
      List<String> points,
      List<String> starters
  ) {
    AppState.CoachPlan plan = new AppState.CoachPlan();
    boolean hasDraft = request != null && !TextUtils.isBlank(request.getDraftText());
    plan.stage = stage;
    plan.coachingMode = coachMode;
    plan.typeJudgment = resolveTypeJudgment(essayType);
    plan.identityTone = defaultIdentityTone(essayType);
    plan.templateId = template.getId();
    plan.scenario = template.getScenario();
    plan.taskPurpose = template.getTaskPurpose();
    plan.officialLogic = template.getOfficialLogic();
    plan.opening = firstNonBlank(template.getOpeningStrategy(), defaultOpening(essayType, starters));
    plan.body = firstNonBlank(template.getBodyStrategy(), defaultBody(essayType, points));
    plan.ending = firstNonBlank(template.getEndingStrategy(), defaultEnding(essayType));
    plan.clueReuse = defaultClueReuse(essayType);
    plan.emotionalFlow = defaultEmotionalFlow(essayType);
    plan.secondOpeningBridge = defaultSecondOpeningBridge(essayType);
    plan.bandRecommendation = defaultBandRecommendation(request);
    plan.bandReason = defaultBandReason(request);
    plan.drillFocus = defaultDrillFocus(essayType, coachMode, stage);
    plan.successCheck = defaultSuccessCheck(essayType, coachMode);
    plan.routeAction = defaultRouteAction(stage, coachMode, hasDraft);
    plan.routeReason = defaultRouteReason(stage, coachMode, hasDraft);
    plan.writingPriorities = defaultWritingPriorities(essayType, coachMode, stage, points);
    plan.drillTasks = defaultDrillTasks(essayType, coachMode, stage, points);
    plan.mustInclude = mergeDistinct(template.getMustInclude(), points);
    plan.riskPoints = withFallback(template.getRiskPoints(), defaultRiskPoints(essayType));
    plan.suggestedExpressions = withFallback(template.getUsefulExpressions(), defaultExpressions(essayType));
    return plan;
  }

  private AppState.CoachPlan buildGenericPlan(
      EssayTaskRequest request,
      String essayType,
      String stage,
      String coachMode,
      List<String> points,
      List<String> starters
  ) {
    AppState.CoachPlan plan = new AppState.CoachPlan();
    boolean hasDraft = request != null && !TextUtils.isBlank(request.getDraftText());
    plan.stage = stage;
    plan.coachingMode = coachMode;
    plan.typeJudgment = resolveTypeJudgment(essayType);
    plan.identityTone = defaultIdentityTone(essayType);
    plan.templateId = "generic-" + (TextUtils.isBlank(essayType) ? "essay" : essayType);
    plan.scenario = "通用高频场景";
    plan.taskPurpose = "先稳住骨架和得分点";
    plan.officialLogic = "先完成任务，再做细节润色，避免模板腔和漏要点。";
    plan.opening = defaultOpening(essayType, starters);
    plan.body = defaultBody(essayType, points);
    plan.ending = defaultEnding(essayType);
    plan.clueReuse = defaultClueReuse(essayType);
    plan.emotionalFlow = defaultEmotionalFlow(essayType);
    plan.secondOpeningBridge = defaultSecondOpeningBridge(essayType);
    plan.bandRecommendation = defaultBandRecommendation(request);
    plan.bandReason = defaultBandReason(request);
    plan.drillFocus = defaultDrillFocus(essayType, coachMode, stage);
    plan.successCheck = defaultSuccessCheck(essayType, coachMode);
    plan.routeAction = defaultRouteAction(stage, coachMode, hasDraft);
    plan.routeReason = defaultRouteReason(stage, coachMode, hasDraft);
    plan.writingPriorities = defaultWritingPriorities(essayType, coachMode, stage, points);
    plan.drillTasks = defaultDrillTasks(essayType, coachMode, stage, points);
    plan.mustInclude = points.isEmpty() ? defaultMustInclude(essayType) : mergeDistinct(points, defaultMustInclude(essayType));
    plan.riskPoints = defaultRiskPoints(essayType);
    plan.suggestedExpressions = defaultExpressions(essayType);
    return plan;
  }

  private void appendApplicationRules(List<String> lines, EssayTaskRequest request, CoachGuidance guidance) {
    lines.add("- 应用文固定骨架：身份与写信目的 -> 按题面要点顺排推进 -> 礼貌收束。");
    lines.add("- 阅卷关键：语气得体、要点完整、细节真实，不要空泛拔高。");
    if (!guidance.getPoints().isEmpty()) {
      lines.add("- 本题要点拆解：");
      for (int index = 0; index < guidance.getPoints().size(); index++) {
        lines.add("  " + (index + 1) + ". " + guidance.getPoints().get(index));
      }
    }
    if (TextUtils.isBlank(request.getRequirements())) {
      lines.add("- 若题面未写明额外文风，默认保持高考现场自然得体语气。");
    }
    lines.add("- 高频失分：套模板开头、漏写行动安排、结尾只喊口号。");
  }

  private void appendContinuationRules(List<String> lines, EssayTaskRequest request, CoachGuidance guidance) {
    lines.add("- 续写固定推进：第一段先承接冲突和动作，第二段回收线索并完成关系或情绪收束。");
    lines.add("- 阅卷关键：段首句衔接自然、线索有借有还、情绪通过动作和环境外显。");
    if (!guidance.getLikelyStarters().isEmpty()) {
      lines.add("- 段首句提醒：");
      for (String starter : guidance.getLikelyStarters()) {
        lines.add("  - " + starter);
      }
    }
    lines.add("- 高频失分：一上来就总结升华、两段失衡、只写感受不写动作。");
  }

  private void appendMatchedTemplate(List<String> lines, CoachTemplate template, AppState.CoachPlan fallbackPlan) {
    lines.add("- 命中模板场景：" + template.getScenario() + " / " + template.getTaskPurpose());
    lines.add("- 官方阅卷逻辑：" + fallbackPlan.officialLogic);
    lines.add("- 开头怎么起：" + fallbackPlan.opening);
    lines.add("- 中段怎么承：" + fallbackPlan.body);
    lines.add("- 结尾怎么收：" + fallbackPlan.ending);
    if (!fallbackPlan.mustInclude.isEmpty()) {
      lines.add("- 必写点：");
      for (String item : fallbackPlan.mustInclude) {
        lines.add("  - " + item);
      }
    }
    if (!fallbackPlan.riskPoints.isEmpty()) {
      lines.add("- 易失分点：");
      for (String item : fallbackPlan.riskPoints) {
        lines.add("  - " + item);
      }
    }
  }

  private void appendCoachRules(List<String> lines, AppState.CoachPlan fallbackPlan) {
    lines.add("- 陪练输出目标：先帮考生拆题、列骨架、卡住失分点，不要直接堆满漂亮句子。");
    lines.add("- 结果 JSON 必须包含 content、wordCount、scoreText、coachPlan 四个字段。");
    lines.add("- coachPlan 必须完整返回以下槽位：");
    lines.add("  - stage / coachingMode / typeJudgment / identityTone");
    lines.add("  - opening / body / ending");
    lines.add("  - writingPriorities / mustInclude / riskPoints / suggestedExpressions");
    lines.add("  - bandRecommendation / bandReason");
    lines.add("  - drillFocus / drillTasks / successCheck");
    lines.add("  - routeAction / routeReason");
    if (!TextUtils.isBlank(fallbackPlan.secondOpeningBridge)) {
      lines.add("  - clueReuse / emotionalFlow / secondOpeningBridge");
    }
    lines.add("- 当前默认骨架：");
    lines.add("  - opening: " + fallbackPlan.opening);
    lines.add("  - body: " + fallbackPlan.body);
    lines.add("  - ending: " + fallbackPlan.ending);
    lines.add("  - mustInclude: 至少 3 条");
    lines.add("  - riskPoints: 至少 2 条");
    lines.add("  - suggestedExpressions: 至少 3 条英文表达");
  }

  private void appendGradeRules(List<String> lines) {
    lines.add("- 批改维度固定为：内容、结构、语言、亮点、失分点。");
    lines.add("- 必须给出二稿提升方向，不能只说哪里错。");
    lines.add("- 若学生作文存在 AI 腔、模板腔、Tell 而不 Show，需要明确点名。");
  }

  private void appendSentenceModeRules(List<String> lines, String coachMode) {
    if ("sentence_correction".equals(coachMode)) {
      lines.add("- 当前是检查错误模式：只指出必须修改的语法、拼写或用词错误。句子正确时必须明确说“未发现真实错误”。");
      lines.add("- 不得把可选升级说成错误；如需补充更自然、更正式的表达，只能另列为可选改进。");
      return;
    }
    if ("sentence_upgrade".equals(coachMode)) {
      lines.add("- 当前是升级表达模式：原句正确时，提供更自然、更正式的可选改进。");
      lines.add("- 所有改动都必须说明为可选改进，而非错误。");
    }
  }

  private List<String> extractPoints(String taskContent) {
    if (TextUtils.isBlank(taskContent)) {
      return List.of();
    }
    Set<String> points = new LinkedHashSet<>();
    Matcher chineseMatcher = CHINESE_POINT.matcher(taskContent);
    while (chineseMatcher.find()) {
      points.add(cleanLine(chineseMatcher.group(2)));
    }
    Matcher normalMatcher = NORMAL_POINT.matcher(taskContent);
    while (normalMatcher.find()) {
      points.add(cleanLine(normalMatcher.group(1)));
    }
    return points.stream().filter(item -> !item.isBlank()).limit(6).toList();
  }

  private List<String> extractLikelyStarters(EssayTaskRequest request) {
    String corpus = String.join("\n",
        TextUtils.trimToEmpty(request.getTaskContent()),
        TextUtils.trimToEmpty(request.getSourceMaterial())
    );
    if (TextUtils.isBlank(corpus)) {
      return List.of();
    }
    List<String> lines = new ArrayList<>();
    for (String line : corpus.replace("\r", "").split("\n")) {
      String normalized = cleanLine(line);
      int wordCount = normalized.isBlank() ? 0 : normalized.split("\\s+").length;
      if (normalized.matches(".*[A-Za-z].*") && wordCount >= 4 && wordCount <= 20) {
        lines.add(normalized);
      }
    }
    if (lines.size() <= 2) {
      return lines;
    }
    return lines.subList(Math.max(0, lines.size() - 2), lines.size());
  }

  private String cleanLine(String text) {
    return TextUtils.trimToEmpty(text).replaceAll("\\s+", " ");
  }

  private String defaultOpening(String essayType, List<String> starters) {
    if ("continuation".equals(essayType) && !starters.isEmpty()) {
      return "先紧扣段首句推进动作，别急着抒情，先把人物当下反应写出来。";
    }
    if ("application".equals(essayType)) {
      return "第一句直接交代身份和写信目的，再用一句自然过渡到本题任务。";
    }
    return "开头先交代当下任务，不绕远路。";
  }

  private String defaultBody(String essayType, List<String> points) {
    if ("continuation".equals(essayType)) {
      return "中段围绕动作链推进，至少回收一个前文线索，再让情绪通过细节外显。";
    }
    if (!points.isEmpty()) {
      return "中段按题面顺序推进要点，每个点都落到具体动作或安排上。";
    }
    return "中段按任务顺序推进信息，不要堆空话。";
  }

  private String defaultEnding(String essayType) {
    if ("continuation".equals(essayType)) {
      return "结尾完成关系或情绪收束，让前文伏笔有回声。";
    }
    return "结尾礼貌收束，并补一句后续配合或期待。";
  }

  private List<String> defaultMustInclude(String essayType) {
    if ("continuation".equals(essayType)) {
      return List.of("第一段先承接冲突", "第二段完成回收和收束", "情绪通过动作和环境表现");
    }
    return List.of("身份和写作目的清楚", "题面要点逐一回应", "结尾有礼貌并带行动感");
  }

  private List<String> defaultRiskPoints(String essayType) {
    if ("continuation".equals(essayType)) {
      return List.of("不要跳过动作直接抒情", "不要忘记回收前文线索", "两段字数不要严重失衡");
    }
    return List.of("不要套空泛模板开头", "不要漏写任务安排", "不要把结尾写成空洞升华");
  }

  private List<String> defaultExpressions(String essayType) {
    if ("continuation".equals(essayType)) {
      return List.of("A thought suddenly struck me.", "I froze for a second.", "The memory came rushing back.");
    }
    return List.of("I'm writing to...", "I'd be more than happy to...", "I would really appreciate it if...");
  }

  private String resolveCoachStage(EssayTaskRequest request) {
    String explicit = normalizeCoachStage(request == null ? "" : request.getCoachStage());
    if (!TextUtils.isBlank(explicit)) {
      return explicit;
    }
    if (request != null && !TextUtils.isBlank(request.getDraftText())) {
      return "postwrite";
    }
    return "prewrite";
  }

  private String resolveCoachMode(EssayTaskRequest request, String stage) {
    String explicit = normalizeCoachMode(request == null ? "" : request.getCoachMode());
    if (!TextUtils.isBlank(explicit)) {
      return explicit;
    }
    if ("postwrite".equals(stage)) {
      return "routing";
    }
    if ("drafting".equals(stage) && request != null && !TextUtils.isBlank(request.getDraftText())) {
      return "sentence_upgrade";
    }
    return "outline";
  }

  private String normalizeCoachStage(String stage) {
    String normalized = TextUtils.lower(stage);
    return List.of("prewrite", "drafting", "postwrite").contains(normalized) ? normalized : "";
  }

  private String normalizeCoachMode(String coachMode) {
    String normalized = TextUtils.lower(coachMode);
    return List.of("prompt_analysis", "outline", "sentence_correction", "sentence_upgrade", "weakness_drill", "routing").contains(normalized)
        ? normalized
        : "";
  }

  private String resolveTypeJudgment(String essayType) {
    if ("continuation".equals(essayType)) {
      return "读后续写：先看原文贴合、段首句承接和线索回收，再看表达有没有呼吸感。";
    }
    return "应用文：先看任务完成、身份对象和语气得体，再看细节是否真实顺手。";
  }

  private String defaultIdentityTone(String essayType) {
    if ("continuation".equals(essayType)) {
      return "人物反应要顺着原文走，情绪尽量藏在动作、停顿、视线和环境里。";
    }
    return "先把身份、对象和语气立住，像真实考场里在对人说话，而不是背模板。";
  }

  private String defaultClueReuse(String essayType) {
    if ("continuation".equals(essayType)) {
      return "至少回收前文里已经出现的一个细节，比如表情、动作、提醒、道具或一句话。";
    }
    return "";
  }

  private String defaultEmotionalFlow(String essayType) {
    if ("continuation".equals(essayType)) {
      return "情绪先跟着动作走，再通过停顿、手部反应、目光和环境一点点露出来。";
    }
    return "";
  }

  private String defaultSecondOpeningBridge(String essayType) {
    if ("continuation".equals(essayType)) {
      return "第一段末句要自然把第二段段首句垫出来，最好形成因果或递进，不要硬转。";
    }
    return "";
  }

  private String defaultBandRecommendation(EssayTaskRequest request) {
    String bandValue = request == null ? "" : TextUtils.trimToEmpty(request.getBandValue());
    if (TextUtils.isBlank(bandValue) && request != null) {
      bandValue = resolveBandValue(request.getBand());
    }
    if (TextUtils.isBlank(bandValue)) {
      bandValue = "学霸版";
    }
    return "这一轮先按" + bandValue + "的标准练。";
  }

  private String defaultBandReason(EssayTaskRequest request) {
    String bandValue = request == null ? "" : TextUtils.trimToEmpty(request.getBandValue());
    if (TextUtils.isBlank(bandValue) && request != null) {
      bandValue = resolveBandValue(request.getBand());
    }
    if (bandValue.contains("进阶")) {
      return "先把任务完成、语气得体和基本顺序稳住，不急着硬堆长句。";
    }
    if (bandValue.contains("满分")) {
      return "这一档更看重骨架稳、线索闭环和表达自然，不是单纯堆高级词。";
    }
    return "这一档最适合把细节、衔接和句子呼吸感一起往上提。";
  }

  private List<String> defaultWritingPriorities(
      String essayType,
      String coachMode,
      String stage,
      List<String> points
  ) {
    if ("sentence_correction".equals(coachMode)) {
      return List.of("只检查必须修改的语法、拼写或用词问题", "把真实错误和可选升级分开说明", "没有真实错误时明确给出结论");
    }
    if ("continuation".equals(essayType)) {
      if ("prompt_analysis".equals(coachMode)) {
        return List.of("先看原文冲突和人物当下处境", "先分清两段各自要完成什么", "先圈出可回收的线索");
      }
      if ("sentence_upgrade".equals(coachMode)) {
        return List.of("先把抽象情绪改成动作反应", "先保证动作链不断裂", "先让长短句错开");
      }
      if ("weakness_drill".equals(coachMode)) {
        return List.of("只盯一个薄弱点", "优先练线索回收或段首句衔接", "练完立刻能塞回原文");
      }
      return List.of("第一段先承接动作和冲突", "第二段完成回收与收束", "第二段段首句前要有自然垫话");
    }

    if ("prompt_analysis".equals(coachMode)) {
      return List.of("先看清身份和写作目的", points.isEmpty() ? "先拆出题面必写点" : "先按题面顺序列要点", "先确定语气别写飘");
    }
    if ("sentence_upgrade".equals(coachMode)) {
      return List.of("先去掉模板化开头", "先把句子调成短长结合", "先补一个真实生活细节");
    }
    if ("weakness_drill".equals(coachMode)) {
      return List.of("只盯开头、结尾或语气其中一个", "不求整篇一起改", "练完能马上套回同类题");
    }
    if ("postwrite".equals(stage)) {
      return List.of("先看有没有漏要点", "先看语气和身份稳不稳", "先决定是继续陪练还是转严格批改");
    }
    return List.of("开头先交代身份和目的", "中段按题面顺序推进", "结尾礼貌收束并带一点后续安排");
  }

  private String defaultDrillFocus(String essayType, String coachMode, String stage) {
    if ("routing".equals(coachMode)) {
      return "先判断你现在最该继续陪练、直接下笔，还是切去严格批改。";
    }
    if ("sentence_correction".equals(coachMode)) {
      return "这一轮只检查必须修改的错误；原句正确时，明确告诉学生未发现真实错误。";
    }
    if ("sentence_upgrade".equals(coachMode)) {
      return "这一轮只做句子升级：去模板感、调语气、加一点自然呼吸感。";
    }
    if ("weakness_drill".equals(coachMode)) {
      return "这一轮只练一个薄弱点，不追求一次把整篇都改完。";
    }
    if ("continuation".equals(essayType)) {
      return "这一轮重点把两段推进、线索回收和第二段段首句衔接走顺。";
    }
    if ("postwrite".equals(stage)) {
      return "这一轮重点先做轻诊断，判断你的草稿最容易丢分的地方。";
    }
    return "这一轮重点先把题意、骨架和得分顺序拆清楚。";
  }

  private List<String> defaultDrillTasks(
      String essayType,
      String coachMode,
      String stage,
      List<String> points
  ) {
    if ("sentence_correction".equals(coachMode)) {
      return List.of("逐项检查语法、拼写和用词", "只改必须修改的地方", "若无错误，写出“未发现真实错误”");
    }
    if ("continuation".equals(essayType)) {
      if ("prompt_analysis".equals(coachMode)) {
        return List.of("用一句话说清原文的核心冲突", "分别写出第一段和第二段各要完成什么", "圈出 1 到 2 个要回收的线索");
      }
      if ("sentence_upgrade".equals(coachMode)) {
        return List.of("挑两句最平的情绪句，改成动作 + 停顿", "把一个过长的句子拆开，让节奏更顺", "在第一段末句补一层自然垫话");
      }
      if ("weakness_drill".equals(coachMode)) {
        return List.of("只练第一段末句到第二段段首句的过渡", "只练一个线索回收点", "只练一处 Show，不求铺满全文");
      }
      return List.of("先列第一段 3 步动作链", "再列第二段回收哪个线索", "最后补一句给第二段段首句垫话");
    }

    if ("prompt_analysis".equals(coachMode)) {
      return List.of("拆出身份、对象和写作目的", points.isEmpty() ? "列出题面 2 到 3 个必写点" : "把题面要点改写成自己的短提示", "标出最容易写空的一点");
    }
    if ("sentence_upgrade".equals(coachMode)) {
      return List.of("把一句模板腔开头改自然", "把一句太满的长句拆开", "补一个真实动作或细节");
    }
    if ("weakness_drill".equals(coachMode)) {
      return List.of("先写 1 句更自然的开头", "再写 1 句有行动感的结尾", "最后检查语气有没有太硬或太空");
    }
    if ("postwrite".equals(stage)) {
      return List.of("先圈出有没有漏要点", "再看语气和身份是否跑偏", "最后决定下一步是重写局部还是转严格批改");
    }
    return List.of("先定开头第一句", "再排中段要点顺序", "最后写收尾动作句");
  }

  private String defaultSuccessCheck(String essayType, String coachMode) {
    if ("routing".equals(coachMode)) {
      return "你能清楚知道下一步该继续陪练、直接下笔，还是去严格批改。";
    }
    if ("sentence_correction".equals(coachMode)) {
      return "你能区分必须修改的真实错误和可选的表达升级。";
    }
    if ("sentence_upgrade".equals(coachMode)) {
      return "改完后，句子读起来更像考场里自然写出来的，而不是背模板。";
    }
    if ("continuation".equals(essayType)) {
      return "你能说清两段各写什么、回收什么，以及第二段段首句为什么接得住。";
    }
    return "你能在 30 秒内说清开头、中段、结尾各写什么，而且不漏题面要点。";
  }

  private String defaultRouteAction(String stage, String coachMode, boolean hasDraft) {
    if ((hasDraft && "postwrite".equals(stage)) || ("routing".equals(coachMode) && hasDraft)) {
      return "switch_to_grade";
    }
    if ("routing".equals(coachMode) || "sentence_correction".equals(coachMode) || "sentence_upgrade".equals(coachMode)) {
      return "write_now";
    }
    return "continue_coach";
  }

  private String defaultRouteReason(String stage, String coachMode, boolean hasDraft) {
    if ((hasDraft && "postwrite".equals(stage)) || ("routing".equals(coachMode) && hasDraft)) {
      return "你已经有草稿了，下一步最值钱的是切到严格批改，按阅卷维度把真正失分点揪出来。";
    }
    if ("routing".equals(coachMode)) {
      return "题意和方向已经够了，现在最值钱的是先把第一版写出来，再决定要不要精修。";
    }
    if ("sentence_correction".equals(coachMode)) {
      return "先分清有没有必须修改的错误；确认后再决定要不要做可选表达升级。";
    }
    if ("sentence_upgrade".equals(coachMode)) {
      return "句子顺了以后，不要继续停在局部打磨，直接把这一版往下写更划算。";
    }
    return "骨架还可以再练一轮，先把路径走稳，再进下一步会更省分。";
  }

  private String resolveBandValue(String band) {
    String normalized = TextUtils.lower(band);
    if ("band1".equals(normalized)) {
      return "进阶版";
    }
    if ("band2".equals(normalized)) {
      return "学霸版";
    }
    if ("band3".equals(normalized)) {
      return "满分压轴版";
    }
    return "";
  }

  private String firstNonBlank(String preferred, String fallback) {
    return TextUtils.isBlank(preferred) ? fallback : preferred;
  }

  private List<String> withFallback(List<String> preferred, List<String> fallback) {
    return preferred == null || preferred.isEmpty() ? new ArrayList<>(fallback) : new ArrayList<>(preferred);
  }

  private List<String> mergeDistinct(List<String> primary, List<String> secondary) {
    LinkedHashSet<String> merged = new LinkedHashSet<>();
    if (primary != null) {
      primary.stream().map(this::cleanLine).filter(item -> !item.isBlank()).forEach(merged::add);
    }
    if (secondary != null) {
      secondary.stream().map(this::cleanLine).filter(item -> !item.isBlank()).forEach(merged::add);
    }
    return new ArrayList<>(merged);
  }

  public static class CoachGuidance {
    private final List<String> points;
    private final List<String> likelyStarters;
    private final List<CoachTemplate> matchedTemplates;
    private final CoachTemplate primaryTemplate;
    private final String stage;
    private final String coachMode;
    private final AppState.CoachPlan fallbackPlan;

    public CoachGuidance(
        List<String> points,
        List<String> likelyStarters,
        List<CoachTemplate> matchedTemplates,
        CoachTemplate primaryTemplate,
        String stage,
        String coachMode,
        AppState.CoachPlan fallbackPlan
    ) {
      this.points = points == null ? List.of() : List.copyOf(points);
      this.likelyStarters = likelyStarters == null ? List.of() : List.copyOf(likelyStarters);
      this.matchedTemplates = matchedTemplates == null ? List.of() : List.copyOf(matchedTemplates);
      this.primaryTemplate = primaryTemplate;
      this.stage = TextUtils.trimToEmpty(stage);
      this.coachMode = TextUtils.trimToEmpty(coachMode);
      this.fallbackPlan = fallbackPlan == null ? new AppState.CoachPlan() : fallbackPlan;
    }

    public List<String> getPoints() {
      return points;
    }

    public List<String> getLikelyStarters() {
      return likelyStarters;
    }

    public List<CoachTemplate> getMatchedTemplates() {
      return matchedTemplates;
    }

    public CoachTemplate getPrimaryTemplate() {
      return primaryTemplate;
    }

    public String getStage() {
      return stage;
    }

    public String getCoachMode() {
      return coachMode;
    }

    public AppState.CoachPlan getFallbackPlan() {
      return fallbackPlan;
    }
  }

  private record ScoredTemplate(CoachTemplate template, int score) {
  }
}
