package com.gaokao.essay.backend.repository;

import com.gaokao.essay.backend.model.UserSubscription;
import java.time.Instant;
import java.util.Optional;

public interface UserSubscriptionRepository {

  Optional<UserSubscription> findByUserId(String userId);

  UserSubscription save(UserSubscription subscription);

  default UserSubscription savePreservingActiveFounderLifetime(UserSubscription subscription, Instant now) {
    Optional<UserSubscription> existing = findByUserId(subscription.userId());
    if (existing.filter(item -> isActiveFounderLifetime(item, now)).isPresent()) {
      return existing.get();
    }
    return save(subscription);
  }

  private static boolean isActiveFounderLifetime(UserSubscription subscription, Instant now) {
    return "founder_lifetime".equals(subscription.planCode()) && subscription.isActiveAt(now);
  }
}
