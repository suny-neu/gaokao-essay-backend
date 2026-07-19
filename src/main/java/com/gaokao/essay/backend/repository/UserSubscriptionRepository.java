package com.gaokao.essay.backend.repository;

import com.gaokao.essay.backend.model.UserSubscription;
import java.util.Optional;

public interface UserSubscriptionRepository {

  Optional<UserSubscription> findByUserId(String userId);

  UserSubscription save(UserSubscription subscription);
}
