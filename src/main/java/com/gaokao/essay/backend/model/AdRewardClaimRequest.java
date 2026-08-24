package com.gaokao.essay.backend.model;

import javax.validation.constraints.Size;

public class AdRewardClaimRequest {
  @Size(max = 256, message = "广告奖励凭证长度不合法")
  private String nonce;

  public String getNonce() {
    return nonce;
  }

  public void setNonce(String nonce) {
    this.nonce = nonce;
  }
}
