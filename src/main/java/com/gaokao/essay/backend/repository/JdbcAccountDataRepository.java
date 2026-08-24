package com.gaokao.essay.backend.repository;

import com.gaokao.essay.backend.util.TextUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Primary
@ConditionalOnBean(JdbcTemplate.class)
public class JdbcAccountDataRepository implements AccountDataRepository {

  private final JdbcTemplate jdbcTemplate;

  public JdbcAccountDataRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  @Transactional
  public void deletePersonalData(String userId, String openId) {
    String anonymousId = "deleted_" + TextUtils.sha256(userId).substring(0, 24);
    jdbcTemplate.update(
        "UPDATE payment_order SET user_id = ?, open_id = ?, payload_json = NULL WHERE user_id = ?",
        anonymousId,
        anonymousId,
        userId
    );
    jdbcTemplate.update("DELETE FROM essay_record WHERE user_id = ?", userId);
    jdbcTemplate.update("DELETE FROM user_usage_quota WHERE user_id = ?", userId);
    jdbcTemplate.update("DELETE FROM user_subscription WHERE user_id = ?", userId);
    jdbcTemplate.update("DELETE FROM user_binding WHERE user_id = ? AND open_id = ?", userId, openId);
  }
}
