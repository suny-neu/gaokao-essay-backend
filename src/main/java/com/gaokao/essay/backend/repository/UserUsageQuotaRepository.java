package com.gaokao.essay.backend.repository;

import com.gaokao.essay.backend.model.UserUsageQuota;
import java.util.Optional;

public interface UserUsageQuotaRepository {

  Optional<UserUsageQuota> findByUserIdAndQuotaType(String userId, String quotaType);

  boolean tryConsume(String userId, String quotaType, int limitCount);

  void release(String userId, String quotaType);
}
