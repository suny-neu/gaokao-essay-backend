package com.gaokao.essay.backend.repository;

import com.gaokao.essay.backend.model.UserBinding;
import com.gaokao.essay.backend.store.AppState;
import com.gaokao.essay.backend.store.StateStore;
import com.gaokao.essay.backend.util.TextUtils;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class StateStoreUserBindingRepository implements UserBindingRepository {

  private final StateStore stateStore;

  public StateStoreUserBindingRepository(StateStore stateStore) {
    this.stateStore = stateStore;
  }

  @Override
  public Optional<UserBinding> findByOpenId(String openId) {
    return stateStore.read(state -> {
      AppState.UserState userState = state.users.get(openId);
      if (userState == null || TextUtils.isBlank(userState.userId)) {
        return Optional.empty();
      }
      return Optional.of(new UserBinding(
          userState.userId,
          openId,
          parseInstant(userState.createdAt),
          parseInstant(userState.lastLoginAt)
      ));
    });
  }

  @Override
  public UserBinding save(UserBinding binding) {
    stateStore.write(state -> {
      AppState.UserState userState = state.users.computeIfAbsent(binding.openId(), key -> {
        AppState.UserState user = new AppState.UserState();
        user.openId = key;
        return user;
      });
      userState.userId = binding.userId();
      userState.openId = binding.openId();
      userState.createdAt = TextUtils.formatInstant(binding.createdAt());
      userState.lastLoginAt = TextUtils.formatInstant(binding.lastLoginAt());
      return null;
    });
    return binding;
  }

  private Instant parseInstant(String value) {
    return TextUtils.isBlank(value) ? Instant.now() : Instant.parse(value);
  }
}
