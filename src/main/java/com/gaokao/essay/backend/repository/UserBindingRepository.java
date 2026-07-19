package com.gaokao.essay.backend.repository;

import com.gaokao.essay.backend.model.UserBinding;
import java.util.Optional;

public interface UserBindingRepository {

  Optional<UserBinding> findByOpenId(String openId);

  UserBinding save(UserBinding binding);
}
