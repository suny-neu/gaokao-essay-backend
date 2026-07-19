package com.gaokao.essay.backend.model;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class AuthLoginRequest {
  @NotBlank(message = "wx.login code 不能为空")
  @Size(max = 128, message = "wx.login code 长度不合法")
  private String code;

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }
}
