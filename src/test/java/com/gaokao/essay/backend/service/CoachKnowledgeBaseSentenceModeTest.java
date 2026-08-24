package com.gaokao.essay.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gaokao.essay.backend.config.GaokaoProperties;
import com.gaokao.essay.backend.model.EssayTaskRequest;
import com.gaokao.essay.backend.repository.CoachTemplateRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CoachKnowledgeBaseSentenceModeTest {

  @Test
  void correctionModeKeepsOnlyMandatoryCorrectionsInItsKnowledgeGuidance() {
    CoachKnowledgeBaseService service = service();
    EssayTaskRequest request = sentenceRequest("sentence_correction");

    CoachKnowledgeBaseService.CoachGuidance guidance = service.prepareKnowledge(request);

    assertThat(guidance.getCoachMode()).isEqualTo("sentence_correction");
    assertThat(service.buildPromptContext(request, guidance))
        .contains("未发现真实错误")
        .contains("不得把可选升级说成错误");
  }

  @Test
  void upgradeModeDescribesChangesAsOptionalImprovements() {
    CoachKnowledgeBaseService service = service();
    EssayTaskRequest request = sentenceRequest("sentence_upgrade");

    CoachKnowledgeBaseService.CoachGuidance guidance = service.prepareKnowledge(request);

    assertThat(service.buildPromptContext(request, guidance))
        .contains("可选改进")
        .contains("而非错误");
  }

  private CoachKnowledgeBaseService service() {
    GaokaoProperties properties = new GaokaoProperties();
    properties.getKnowledge().setEnabled(false);
    return new CoachKnowledgeBaseService(properties, Mockito.mock(CoachTemplateRepository.class));
  }

  private EssayTaskRequest sentenceRequest(String coachMode) {
    EssayTaskRequest request = new EssayTaskRequest();
    request.setMode("coach");
    request.setEssayType("application");
    request.setCoachStage("drafting");
    request.setCoachMode(coachMode);
    request.setBandValue("学霸版");
    request.setDraftText("I am glad.");
    return request;
  }
}
