package com.gaokao.essay.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gaokao.essay.backend.model.ApiException;
import com.gaokao.essay.backend.model.AuthenticatedUser;
import com.gaokao.essay.backend.model.EssayTaskRequest;
import com.gaokao.essay.backend.store.AppState;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class EssayServiceSentenceModeTest {

  @Test
  void correctionModeReachesTheCoachWithNoInventedErrorGuidance() {
    MembershipService membershipService = Mockito.mock(MembershipService.class);
    ContentSafetyService contentSafetyService = Mockito.mock(ContentSafetyService.class);
    HistoryService historyService = Mockito.mock(HistoryService.class);
    AiGatewayService aiGatewayService = Mockito.mock(AiGatewayService.class);
    CoachKnowledgeBaseService coachKnowledgeBaseService = Mockito.mock(CoachKnowledgeBaseService.class);
    StudyProfileService studyProfileService = Mockito.mock(StudyProfileService.class);
    EssayService essayService = new EssayService(
        membershipService,
        contentSafetyService,
        historyService,
        aiGatewayService,
        coachKnowledgeBaseService,
        studyProfileService,
        new ObjectMapper()
    );
    AuthenticatedUser user = user();
    EssayTaskRequest request = sentenceRequest("sentence_correction");

    when(historyService.findOrCreatePendingRecord(any())).thenAnswer(call -> call.getArgument(0));
    MembershipService.UsageReservation reservation = MembershipService.UsageReservation.trial(
        user.userId(),
        List.of("AD_REWARD_CREDITS")
    );
    when(membershipService.reserveEssayAccess(user)).thenReturn(reservation);
    when(coachKnowledgeBaseService.prepareKnowledge(request)).thenReturn(guidance("sentence_correction"));
    when(aiGatewayService.requestJsonText(anyString(), anyString())).thenThrow(new IllegalStateException("stop after prompt"));

    assertThatThrownBy(() -> essayService.execute(user, request))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("stop after prompt");

    ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
    verify(aiGatewayService).requestJsonText(anyString(), prompt.capture());
    verify(membershipService).releaseReservation(reservation);
    assertThat(prompt.getValue())
        .contains("未发现真实错误")
        .contains("不得把可选升级说成错误");
  }

  @Test
  void rejectsUnknownCoachModeBeforeStartingAnyWork() {
    MembershipService membershipService = Mockito.mock(MembershipService.class);
    ContentSafetyService contentSafetyService = Mockito.mock(ContentSafetyService.class);
    HistoryService historyService = Mockito.mock(HistoryService.class);
    AiGatewayService aiGatewayService = Mockito.mock(AiGatewayService.class);
    CoachKnowledgeBaseService coachKnowledgeBaseService = Mockito.mock(CoachKnowledgeBaseService.class);
    StudyProfileService studyProfileService = Mockito.mock(StudyProfileService.class);
    EssayService essayService = new EssayService(
        membershipService,
        contentSafetyService,
        historyService,
        aiGatewayService,
        coachKnowledgeBaseService,
        studyProfileService,
        new ObjectMapper()
    );
    EssayTaskRequest request = sentenceRequest("not_a_coach_mode");

    assertThatThrownBy(() -> essayService.execute(user(), request))
        .isInstanceOf(ApiException.class)
        .extracting(error -> ((ApiException) error).getCode())
        .isEqualTo("INVALID_COACH_MODE");

    verify(contentSafetyService, never()).verifyUserInput(anyString(), any());
    verify(aiGatewayService, never()).requestJsonText(anyString(), anyString());
  }

  private AuthenticatedUser user() {
    Instant now = Instant.now();
    return new AuthenticatedUser("user_test", "open_test", now, now.plusSeconds(3600));
  }

  private EssayTaskRequest sentenceRequest(String coachMode) {
    EssayTaskRequest request = new EssayTaskRequest();
    request.setClientRequestId("request_sentence_mode");
    request.setMode("coach");
    request.setEssayType("application");
    request.setCoachStage("drafting");
    request.setCoachMode(coachMode);
    request.setBand("band2");
    request.setBandValue("学霸版");
    request.setTaskContent("检查句子");
    request.setDraftText("I am glad.");
    return request;
  }

  private CoachKnowledgeBaseService.CoachGuidance guidance(String coachMode) {
    AppState.CoachPlan plan = new AppState.CoachPlan();
    plan.stage = "drafting";
    plan.coachingMode = coachMode;
    return new CoachKnowledgeBaseService.CoachGuidance(
        List.of(),
        List.of(),
        List.of(),
        null,
        "drafting",
        coachMode,
        plan
    );
  }
}
