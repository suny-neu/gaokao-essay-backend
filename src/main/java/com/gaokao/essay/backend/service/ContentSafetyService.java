package com.gaokao.essay.backend.service;

import com.gaokao.essay.backend.model.EssayTaskRequest;
import org.springframework.stereotype.Service;

@Service
public class ContentSafetyService {

  private final WechatService wechatService;

  public ContentSafetyService(WechatService wechatService) {
    this.wechatService = wechatService;
  }

  public void verifyUserInput(String openId, EssayTaskRequest request) {
    wechatService.checkMessageSecurity(openId, request.getTaskContent(), "题目内容");
    wechatService.checkMessageSecurity(openId, request.getSourceMaterial(), "补充材料");
    wechatService.checkMessageSecurity(openId, request.getDraftText(), "学生作文");
    wechatService.checkMessageSecurity(openId, request.getRequirements(), "补充要求");
  }

  public void verifyOutput(String openId, String output) {
    wechatService.checkMessageSecurity(openId, output, "模型输出");
  }

  public void verifyOcrText(String openId, String text) {
    wechatService.checkMessageSecurity(openId, text, "OCR 识别文本");
  }
}
