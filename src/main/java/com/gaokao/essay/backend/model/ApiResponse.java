package com.gaokao.essay.backend.model;

public class ApiResponse<T> {

  private final boolean success;
  private final String code;
  private final String message;
  private final T data;

  private ApiResponse(boolean success, String code, String message, T data) {
    this.success = success;
    this.code = code;
    this.message = message;
    this.data = data;
  }

  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(true, "OK", "", data);
  }

  public static <T> ApiResponse<T> ok(String code, String message, T data) {
    return new ApiResponse<>(true, code, message, data);
  }

  public static <T> ApiResponse<T> fail(String code, String message) {
    return new ApiResponse<>(false, code, message, null);
  }

  public boolean isSuccess() {
    return success;
  }

  public String getCode() {
    return code;
  }

  public String getMessage() {
    return message;
  }

  public T getData() {
    return data;
  }
}
