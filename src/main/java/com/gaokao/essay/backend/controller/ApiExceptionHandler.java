package com.gaokao.essay.backend.controller;

import com.gaokao.essay.backend.model.ApiException;
import com.gaokao.essay.backend.model.ApiResponse;
import java.util.stream.Collectors;
import javax.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException error) {
    return ResponseEntity.status(error.getStatus())
        .body(ApiResponse.fail(error.getCode(), error.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException error) {
    String message = error.getBindingResult().getFieldErrors().stream()
        .map((item) -> item.getDefaultMessage())
        .filter((item) -> item != null && !item.isBlank())
        .findFirst()
        .orElse("请求参数不合法");
    return ResponseEntity.badRequest()
        .body(ApiResponse.fail("INVALID_REQUEST", message));
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException error) {
    String message = error.getConstraintViolations().stream()
        .map((item) -> item.getMessage())
        .filter((item) -> item != null && !item.isBlank())
        .collect(Collectors.joining("；"));
    return ResponseEntity.badRequest()
        .body(ApiResponse.fail("INVALID_REQUEST", message.isBlank() ? "请求参数不合法" : message));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException error) {
    return ResponseEntity.badRequest()
        .body(ApiResponse.fail("FILE_TOO_LARGE", "上传文件过大，请压缩后重试"));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleException(Exception error) {
    log.error("Unhandled server exception", error);
    return ResponseEntity.status(500)
        .body(ApiResponse.fail("INTERNAL_ERROR", "服务器异常，请稍后再试"));
  }
}
