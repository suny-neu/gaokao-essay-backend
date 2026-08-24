package com.gaokao.essay.backend.repository;

import com.gaokao.essay.backend.model.UserSubscription;
import com.gaokao.essay.backend.store.AppState;
import com.gaokao.essay.backend.store.StateStore;
import com.gaokao.essay.backend.util.TextUtils;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class StateStoreUserSubscriptionRepository implements UserSubscriptionRepository {

  private final StateStore stateStore;

  public StateStoreUserSubscriptionRepository(StateStore stateStore) {
    this.stateStore = stateStore;
  }

  @Override
  public Optional<UserSubscription> findByUserId(String userId) {
    return stateStore.read(state -> {
      AppState.SubscriptionState snapshot = state.subscriptions.get(userId);
      if (snapshot == null) {
        return Optional.empty();
      }
      return Optional.of(new UserSubscription(
          userId,
          snapshot.planCode,
          snapshot.planName,
          snapshot.status,
          parseInstant(snapshot.startedAt),
          parseNullableInstant(snapshot.expiresAt),
          snapshot.autoRenew,
          snapshot.provider,
          snapshot.providerReference,
          parseInstant(snapshot.updatedAt)
      ));
    });
  }

  @Override
  public UserSubscription save(UserSubscription subscription) {
    stateStore.write(state -> {
      AppState.SubscriptionState snapshot = new AppState.SubscriptionState();
      snapshot.active = subscription.isActiveAt(Instant.now());
      snapshot.status = subscription.status();
      snapshot.planCode = subscription.planCode();
      snapshot.planName = subscription.planName();
      snapshot.startedAt = formatNullable(subscription.startedAt());
      snapshot.expiresAt = formatNullable(subscription.expiresAt());
      snapshot.autoRenew = subscription.autoRenew();
      snapshot.provider = subscription.provider();
      snapshot.providerReference = subscription.providerReference();
      snapshot.updatedAt = formatNullable(subscription.updatedAt());
      state.subscriptions.put(subscription.userId(), snapshot);
      return null;
    });
    return subscription;
  }

  @Override
  public UserSubscription savePreservingActiveFounderLifetime(UserSubscription subscription, Instant now) {
    return stateStore.write(state -> {
      AppState.SubscriptionState current = state.subscriptions.get(subscription.userId());
      if (current != null) {
        UserSubscription existing = new UserSubscription(
            subscription.userId(), current.planCode, current.planName, current.status,
            parseInstant(current.startedAt), parseNullableInstant(current.expiresAt), current.autoRenew,
            current.provider, current.providerReference, parseInstant(current.updatedAt)
        );
        if ("founder_lifetime".equals(existing.planCode()) && existing.isActiveAt(now)) {
          return existing;
        }
      }
      AppState.SubscriptionState snapshot = new AppState.SubscriptionState();
      snapshot.active = subscription.isActiveAt(now);
      snapshot.status = subscription.status();
      snapshot.planCode = subscription.planCode();
      snapshot.planName = subscription.planName();
      snapshot.startedAt = formatNullable(subscription.startedAt());
      snapshot.expiresAt = formatNullable(subscription.expiresAt());
      snapshot.autoRenew = subscription.autoRenew();
      snapshot.provider = subscription.provider();
      snapshot.providerReference = subscription.providerReference();
      snapshot.updatedAt = formatNullable(subscription.updatedAt());
      state.subscriptions.put(subscription.userId(), snapshot);
      return subscription;
    });
  }

  private Instant parseInstant(String value) {
    return TextUtils.isBlank(value) ? Instant.now() : Instant.parse(value);
  }

  private Instant parseNullableInstant(String value) {
    return TextUtils.isBlank(value) ? null : Instant.parse(value);
  }

  private String formatNullable(Instant instant) {
    return instant == null ? "" : TextUtils.formatInstant(instant);
  }
}
