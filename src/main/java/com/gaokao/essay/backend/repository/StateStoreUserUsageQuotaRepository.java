package com.gaokao.essay.backend.repository;

import com.gaokao.essay.backend.model.UserUsageQuota;
import com.gaokao.essay.backend.store.AppState;
import com.gaokao.essay.backend.store.StateStore;
import com.gaokao.essay.backend.util.TextUtils;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class StateStoreUserUsageQuotaRepository implements UserUsageQuotaRepository {

  private final StateStore stateStore;

  public StateStoreUserUsageQuotaRepository(StateStore stateStore) {
    this.stateStore = stateStore;
  }

  @Override
  public Optional<UserUsageQuota> findByUserIdAndQuotaType(String userId, String quotaType) {
    return stateStore.read(state -> {
      AppState.UsageQuotaState quota = state.usageQuotas.get(key(userId, quotaType));
      if (quota == null) {
        return Optional.empty();
      }
      return Optional.of(new UserUsageQuota(
          quota.userId,
          quota.quotaType,
          quota.usedCount,
          quota.limitCount,
          parseInstant(quota.updatedAt)
      ));
    });
  }

  @Override
  public boolean tryConsume(String userId, String quotaType, int limitCount) {
    return stateStore.write(state -> {
      String key = key(userId, quotaType);
      AppState.UsageQuotaState quota = state.usageQuotas.get(key);
      if (quota == null) {
        quota = new AppState.UsageQuotaState();
        quota.userId = userId;
        quota.quotaType = quotaType;
        quota.usedCount = 1;
        quota.limitCount = limitCount;
        quota.updatedAt = TextUtils.formatInstant(Instant.now());
        state.usageQuotas.put(key, quota);
        return true;
      }
      if (quota.usedCount >= Math.max(limitCount, 1)) {
        return false;
      }
      quota.usedCount += 1;
      quota.limitCount = limitCount;
      quota.updatedAt = TextUtils.formatInstant(Instant.now());
      return true;
    });
  }

  @Override
  public void release(String userId, String quotaType) {
    releaseCredits(userId, quotaType, 1);
  }

  @Override
  public void releaseCredits(String userId, String quotaType, int amount) {
    int safeAmount = Math.max(amount, 0);
    if (safeAmount <= 0) {
      return;
    }
    stateStore.write(state -> {
      AppState.UsageQuotaState quota = state.usageQuotas.get(key(userId, quotaType));
      if (quota != null) {
        quota.usedCount = Math.max(quota.usedCount - safeAmount, 0);
        quota.updatedAt = TextUtils.formatInstant(Instant.now());
      }
      return null;
    });
  }

  @Override
  public int grantCredits(String userId, String quotaType, int amount, int maxCredits) {
    int safeAmount = Math.min(Math.max(amount, 0), Math.max(maxCredits, 1));
    int safeMax = Math.max(maxCredits, 1);
    return stateStore.write(state -> {
      String key = key(userId, quotaType);
      AppState.UsageQuotaState quota = state.usageQuotas.get(key);
      if (quota == null) {
        quota = new AppState.UsageQuotaState();
        quota.userId = userId;
        quota.quotaType = quotaType;
        quota.usedCount = safeAmount;
        quota.limitCount = safeMax;
        quota.updatedAt = TextUtils.formatInstant(Instant.now());
        state.usageQuotas.put(key, quota);
        return safeAmount;
      }
      if (quota.usedCount + safeAmount > safeMax) {
        return 0;
      }
      quota.usedCount += safeAmount;
      quota.limitCount = safeMax;
      quota.updatedAt = TextUtils.formatInstant(Instant.now());
      return safeAmount;
    });
  }

  @Override
  public boolean consumeCredit(String userId, String quotaType) {
    return stateStore.write(state -> {
      AppState.UsageQuotaState quota = state.usageQuotas.get(key(userId, quotaType));
      if (quota == null || quota.usedCount <= 0) {
        return false;
      }
      quota.usedCount -= 1;
      quota.updatedAt = TextUtils.formatInstant(Instant.now());
      return true;
    });
  }

  private String key(String userId, String quotaType) {
    return userId + "::" + quotaType;
  }

  private Instant parseInstant(String value) {
    return TextUtils.isBlank(value) ? Instant.now() : Instant.parse(value);
  }
}
