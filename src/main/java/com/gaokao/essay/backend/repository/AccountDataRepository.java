package com.gaokao.essay.backend.repository;

public interface AccountDataRepository {

  void deletePersonalData(String userId, String openId);
}
