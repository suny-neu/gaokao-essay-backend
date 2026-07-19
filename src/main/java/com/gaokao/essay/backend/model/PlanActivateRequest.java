package com.gaokao.essay.backend.model;

public class PlanActivateRequest {
  private String planCode;
  private boolean autoRenew;

  public String getPlanCode() {
    return planCode;
  }

  public void setPlanCode(String planCode) {
    this.planCode = planCode;
  }

  public boolean isAutoRenew() {
    return autoRenew;
  }

  public void setAutoRenew(boolean autoRenew) {
    this.autoRenew = autoRenew;
  }
}
