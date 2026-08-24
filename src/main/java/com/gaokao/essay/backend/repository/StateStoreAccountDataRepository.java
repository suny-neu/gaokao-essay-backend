package com.gaokao.essay.backend.repository;

import com.gaokao.essay.backend.store.AppState;
import com.gaokao.essay.backend.store.StateStore;
import com.gaokao.essay.backend.util.TextUtils;
import org.springframework.stereotype.Repository;

@Repository
public class StateStoreAccountDataRepository implements AccountDataRepository {

  private final StateStore stateStore;

  public StateStoreAccountDataRepository(StateStore stateStore) {
    this.stateStore = stateStore;
  }

  @Override
  public void deletePersonalData(String userId, String openId) {
    stateStore.write(state -> {
      String anonymousId = "deleted_" + TextUtils.sha256(userId).substring(0, 24);
      state.paymentOrders.values().stream()
          .filter(order -> userId.equals(order.userId))
          .forEach(order -> {
            order.userId = anonymousId;
            order.openId = anonymousId;
            order.payloadJson = "";
          });
      state.essays.entrySet().removeIf(entry -> userId.equals(entry.getValue().userId));
      state.userEssayIds.remove(userId);
      state.subscriptions.remove(userId);
      state.usageQuotas.entrySet().removeIf(entry -> userId.equals(entry.getValue().userId));
      state.sessions.entrySet().removeIf(entry -> userId.equals(entry.getValue().userId));
      AppState.UserState user = state.users.get(openId);
      if (user != null && userId.equals(user.userId)) {
        state.users.remove(openId);
      }
      return null;
    });
  }
}
