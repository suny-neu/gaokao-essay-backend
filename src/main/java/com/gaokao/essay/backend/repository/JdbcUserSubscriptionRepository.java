package com.gaokao.essay.backend.repository;

import com.gaokao.essay.backend.model.UserSubscription;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Primary
@ConditionalOnBean(JdbcTemplate.class)
public class JdbcUserSubscriptionRepository implements UserSubscriptionRepository {

  private final JdbcTemplate jdbcTemplate;

  public JdbcUserSubscriptionRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public Optional<UserSubscription> findByUserId(String userId) {
    List<UserSubscription> results = jdbcTemplate.query(
        """
        SELECT user_id, plan_code, plan_name, status, started_at, expires_at,
               auto_renew, provider, provider_reference, updated_at
        FROM user_subscription
        WHERE user_id = ?
        LIMIT 1
        """,
        (resultSet, rowNum) -> mapRow(resultSet),
        userId
    );
    return results.stream().findFirst();
  }

  @Override
  public UserSubscription save(UserSubscription subscription) {
    int updatedRows = jdbcTemplate.update(
        """
        UPDATE user_subscription
        SET plan_code = ?, plan_name = ?, status = ?, started_at = ?, expires_at = ?,
            auto_renew = ?, provider = ?, provider_reference = ?, updated_at = ?
        WHERE user_id = ?
        """,
        subscription.planCode(),
        subscription.planName(),
        subscription.status(),
        Timestamp.from(subscription.startedAt()),
        toTimestamp(subscription.expiresAt()),
        subscription.autoRenew(),
        subscription.provider(),
        subscription.providerReference(),
        Timestamp.from(subscription.updatedAt()),
        subscription.userId()
    );

    if (updatedRows == 0) {
      jdbcTemplate.update(
          """
          INSERT INTO user_subscription (
              user_id, plan_code, plan_name, status, started_at, expires_at,
              auto_renew, provider, provider_reference, updated_at
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          """,
          subscription.userId(),
          subscription.planCode(),
          subscription.planName(),
          subscription.status(),
          Timestamp.from(subscription.startedAt()),
          toTimestamp(subscription.expiresAt()),
          subscription.autoRenew(),
          subscription.provider(),
          subscription.providerReference(),
          Timestamp.from(subscription.updatedAt())
      );
    }

    return subscription;
  }

  @Override
  public UserSubscription savePreservingActiveFounderLifetime(UserSubscription subscription, Instant now) {
    if (isFounderLifetime(subscription)) {
      return saveFounderLifetime(subscription);
    }

    if (updateUnlessActiveFounderLifetime(subscription) > 0) {
      return subscription;
    }

    Optional<UserSubscription> existing = findByUserId(subscription.userId());
    if (existing.isPresent()) {
      return existing.get();
    }

    try {
      insert(subscription);
      return subscription;
    } catch (DuplicateKeyException ignored) {
      if (updateUnlessActiveFounderLifetime(subscription) > 0) {
        return subscription;
      }
      return findByUserId(subscription.userId()).orElseThrow();
    }
  }

  private UserSubscription saveFounderLifetime(UserSubscription subscription) {
    if (update(subscription) == 0) {
      try {
        insert(subscription);
      } catch (DuplicateKeyException ignored) {
        update(subscription);
      }
    }
    return subscription;
  }

  private int updateUnlessActiveFounderLifetime(UserSubscription subscription) {
    return jdbcTemplate.update(
        """
        UPDATE user_subscription
        SET plan_code = ?, plan_name = ?, status = ?, started_at = ?, expires_at = ?,
            auto_renew = ?, provider = ?, provider_reference = ?, updated_at = ?
        WHERE user_id = ?
          AND NOT (plan_code = 'founder_lifetime' AND UPPER(status) = 'ACTIVE' AND expires_at IS NULL)
        """,
        updateArguments(subscription)
    );
  }

  private int update(UserSubscription subscription) {
    return jdbcTemplate.update(
        """
        UPDATE user_subscription
        SET plan_code = ?, plan_name = ?, status = ?, started_at = ?, expires_at = ?,
            auto_renew = ?, provider = ?, provider_reference = ?, updated_at = ?
        WHERE user_id = ?
        """,
        updateArguments(subscription)
    );
  }

  private Object[] updateArguments(UserSubscription subscription) {
    return new Object[] {
        subscription.planCode(), subscription.planName(), subscription.status(), Timestamp.from(subscription.startedAt()),
        toTimestamp(subscription.expiresAt()), subscription.autoRenew(), subscription.provider(), subscription.providerReference(),
        Timestamp.from(subscription.updatedAt()), subscription.userId()
    };
  }

  private void insert(UserSubscription subscription) {
    jdbcTemplate.update(
        """
        INSERT INTO user_subscription (
            user_id, plan_code, plan_name, status, started_at, expires_at,
            auto_renew, provider, provider_reference, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        subscription.userId(), subscription.planCode(), subscription.planName(), subscription.status(),
        Timestamp.from(subscription.startedAt()), toTimestamp(subscription.expiresAt()), subscription.autoRenew(),
        subscription.provider(), subscription.providerReference(), Timestamp.from(subscription.updatedAt())
    );
  }

  private boolean isFounderLifetime(UserSubscription subscription) {
    return "founder_lifetime".equals(subscription.planCode());
  }

  private UserSubscription mapRow(ResultSet resultSet) throws SQLException {
    return new UserSubscription(
        resultSet.getString("user_id"),
        resultSet.getString("plan_code"),
        resultSet.getString("plan_name"),
        resultSet.getString("status"),
        toInstant(resultSet.getTimestamp("started_at")),
        toInstant(resultSet.getTimestamp("expires_at")),
        resultSet.getBoolean("auto_renew"),
        resultSet.getString("provider"),
        resultSet.getString("provider_reference"),
        toInstant(resultSet.getTimestamp("updated_at"))
    );
  }

  private Instant toInstant(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }

  private Timestamp toTimestamp(Instant instant) {
    return instant == null ? null : Timestamp.from(instant);
  }
}
