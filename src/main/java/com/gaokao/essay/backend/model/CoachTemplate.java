package com.gaokao.essay.backend.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class CoachTemplate {

  private String id = "";
  private String essayType = "";
  private String scenario = "";
  private String taskPurpose = "";
  private String officialLogic = "";
  private String openingStrategy = "";
  private String bodyStrategy = "";
  private String endingStrategy = "";
  private List<String> mustInclude = new ArrayList<>();
  private List<String> riskPoints = new ArrayList<>();
  private List<String> usefulExpressions = new ArrayList<>();
  private List<String> triggerKeywords = new ArrayList<>();
  private boolean enabled = true;
  private int sortOrder;
  private Instant updatedAt = Instant.now();

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getEssayType() {
    return essayType;
  }

  public void setEssayType(String essayType) {
    this.essayType = essayType;
  }

  public String getScenario() {
    return scenario;
  }

  public void setScenario(String scenario) {
    this.scenario = scenario;
  }

  public String getTaskPurpose() {
    return taskPurpose;
  }

  public void setTaskPurpose(String taskPurpose) {
    this.taskPurpose = taskPurpose;
  }

  public String getOfficialLogic() {
    return officialLogic;
  }

  public void setOfficialLogic(String officialLogic) {
    this.officialLogic = officialLogic;
  }

  public String getOpeningStrategy() {
    return openingStrategy;
  }

  public void setOpeningStrategy(String openingStrategy) {
    this.openingStrategy = openingStrategy;
  }

  public String getBodyStrategy() {
    return bodyStrategy;
  }

  public void setBodyStrategy(String bodyStrategy) {
    this.bodyStrategy = bodyStrategy;
  }

  public String getEndingStrategy() {
    return endingStrategy;
  }

  public void setEndingStrategy(String endingStrategy) {
    this.endingStrategy = endingStrategy;
  }

  public List<String> getMustInclude() {
    return mustInclude;
  }

  public void setMustInclude(List<String> mustInclude) {
    this.mustInclude = mustInclude == null ? new ArrayList<>() : new ArrayList<>(mustInclude);
  }

  public List<String> getRiskPoints() {
    return riskPoints;
  }

  public void setRiskPoints(List<String> riskPoints) {
    this.riskPoints = riskPoints == null ? new ArrayList<>() : new ArrayList<>(riskPoints);
  }

  public List<String> getUsefulExpressions() {
    return usefulExpressions;
  }

  public void setUsefulExpressions(List<String> usefulExpressions) {
    this.usefulExpressions = usefulExpressions == null ? new ArrayList<>() : new ArrayList<>(usefulExpressions);
  }

  public List<String> getTriggerKeywords() {
    return triggerKeywords;
  }

  public void setTriggerKeywords(List<String> triggerKeywords) {
    this.triggerKeywords = triggerKeywords == null ? new ArrayList<>() : new ArrayList<>(triggerKeywords);
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public void setSortOrder(int sortOrder) {
    this.sortOrder = sortOrder;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt == null ? Instant.now() : updatedAt;
  }
}
