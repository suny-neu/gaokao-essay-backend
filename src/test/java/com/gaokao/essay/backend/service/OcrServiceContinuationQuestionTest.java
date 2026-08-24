package com.gaokao.essay.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gaokao.essay.backend.config.GaokaoProperties;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class OcrServiceContinuationQuestionTest {

  @Mock
  private AiGatewayService aiGatewayService;

  @Mock
  private ContentSafetyService contentSafetyService;

  private OcrService ocrService;

  @BeforeEach
  void setUp() {
    ocrService = new OcrService(aiGatewayService, contentSafetyService, new GaokaoProperties());
  }

  @Test
  void returnsStructuredContinuationQuestionFieldsForQuestionScene() {
    String ocrText = "A boy found an injured bird and took it home.\n"
        + "Paragraph 1: The next morning, the bird opened its eyes.\n"
        + "Paragraph 2: Seeing the bird fly away, the boy smiled.";
    when(aiGatewayService.requestVisionOcr(anyString(), anyString(), anyString())).thenReturn(ocrText);
    when(aiGatewayService.getVisionProviderLabel()).thenReturn("vision-test");
    MockMultipartFile image = new MockMultipartFile(
        "file",
        "question.png",
        "image/png",
        new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00}
    );

    Map<String, Object> result = ocrService.extractText("openid-test", image, "question");

    assertThat(result.get("scene")).isEqualTo("question");
    assertThat(result.get("sourceMaterial")).isEqualTo("A boy found an injured bird and took it home.");
    assertThat(result.get("paragraphOneStarter")).isEqualTo("The next morning, the bird opened its eyes.");
    assertThat(result.get("paragraphTwoStarter")).isEqualTo("Seeing the bird fly away, the boy smiled.");
    verify(contentSafetyService).verifyOcrText("openid-test", ocrText);
  }
}
