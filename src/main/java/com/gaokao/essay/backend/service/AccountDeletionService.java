package com.gaokao.essay.backend.service;

import com.gaokao.essay.backend.model.ApiException;
import com.gaokao.essay.backend.model.AuthenticatedUser;
import com.gaokao.essay.backend.repository.AccountDataRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AccountDeletionService {

  private final AccountDataRepository accountDataRepository;

  public AccountDeletionService(AccountDataRepository accountDataRepository) {
    this.accountDataRepository = accountDataRepository;
  }

  public void deleteAccount(AuthenticatedUser user, String confirmation) {
    if (!"DELETE".equals(confirmation)) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "ACCOUNT_DELETE_CONFIRMATION_REQUIRED",
          "注销账户前需要明确确认"
      );
    }
    accountDataRepository.deletePersonalData(user.userId(), user.openId());
  }
}
