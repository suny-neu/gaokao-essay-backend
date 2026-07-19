package com.gaokao.essay.backend.model;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class EssayTaskRequest {
  @NotBlank(message = "clientRequestId 不能为空")
  @Size(max = 128, message = "clientRequestId 长度不合法")
  private String clientRequestId;
  @NotBlank(message = "mode 不能为空")
  @Size(max = 32, message = "mode 长度不合法")
  private String mode;
  @NotBlank(message = "essayType 不能为空")
  @Size(max = 32, message = "essayType 长度不合法")
  private String essayType;
  @Size(max = 32, message = "band 长度不合法")
  private String band;
  @Size(max = 64, message = "bandValue 长度不合法")
  private String bandValue;
  @Size(max = 20000, message = "taskContent 过长")
  private String taskContent;
  @Size(max = 20000, message = "sourceMaterial 过长")
  private String sourceMaterial;
  @Size(max = 20000, message = "draftText 过长")
  private String draftText;
  @Size(max = 8000, message = "requirements 过长")
  private String requirements;
  @Size(max = 32, message = "coachStage 长度不合法")
  private String coachStage;
  @Size(max = 32, message = "coachMode 长度不合法")
  private String coachMode;
  @Size(max = 128, message = "wxCode 长度不合法")
  private String wxCode;
  @Size(max = 128, message = "openId 长度不合法")
  private String openId;

  public String getClientRequestId() {
    return clientRequestId;
  }

  public void setClientRequestId(String clientRequestId) {
    this.clientRequestId = clientRequestId;
  }

  public String getMode() {
    return mode;
  }

  public void setMode(String mode) {
    this.mode = mode;
  }

  public String getEssayType() {
    return essayType;
  }

  public void setEssayType(String essayType) {
    this.essayType = essayType;
  }

  public String getBand() {
    return band;
  }

  public void setBand(String band) {
    this.band = band;
  }

  public String getBandValue() {
    return bandValue;
  }

  public void setBandValue(String bandValue) {
    this.bandValue = bandValue;
  }

  public String getTaskContent() {
    return taskContent;
  }

  public void setTaskContent(String taskContent) {
    this.taskContent = taskContent;
  }

  public String getSourceMaterial() {
    return sourceMaterial;
  }

  public void setSourceMaterial(String sourceMaterial) {
    this.sourceMaterial = sourceMaterial;
  }

  public String getDraftText() {
    return draftText;
  }

  public void setDraftText(String draftText) {
    this.draftText = draftText;
  }

  public String getRequirements() {
    return requirements;
  }

  public void setRequirements(String requirements) {
    this.requirements = requirements;
  }

  public String getCoachStage() {
    return coachStage;
  }

  public void setCoachStage(String coachStage) {
    this.coachStage = coachStage;
  }

  public String getCoachMode() {
    return coachMode;
  }

  public void setCoachMode(String coachMode) {
    this.coachMode = coachMode;
  }

  public String getWxCode() {
    return wxCode;
  }

  public void setWxCode(String wxCode) {
    this.wxCode = wxCode;
  }

  public String getOpenId() {
    return openId;
  }

  public void setOpenId(String openId) {
    this.openId = openId;
  }
}
