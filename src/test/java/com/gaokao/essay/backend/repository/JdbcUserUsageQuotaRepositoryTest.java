package com.gaokao.essay.backend.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcUserUsageQuotaRepositoryTest {

  @Test
  void returnsZeroWhenAnExistingCreditQuotaCannotFitTheFullReward() {
    JdbcTemplate jdbcTemplate = new ExistingFullQuotaJdbcTemplate();
    JdbcUserUsageQuotaRepository repository = new JdbcUserUsageQuotaRepository(jdbcTemplate);

    int granted = repository.grantCredits("user_1", "AD_REWARD_CREDITS", 2, 5);

    assertEquals(0, granted);
  }

  private static final class ExistingFullQuotaJdbcTemplate extends JdbcTemplate {
    @Override
    public int update(String sql, Object... args) {
      if (sql.stripLeading().startsWith("INSERT")) {
        throw new DuplicateKeyException("quota already exists");
      }
      return 0;
    }
  }
}
