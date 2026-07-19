package com.gaokao.essay.backend.repository;

import com.gaokao.essay.backend.model.UserUsageQuota;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Primary
@ConditionalOnBean(JdbcTemplate.class)
public class JdbcUserUsageQuotaRepository implements UserUsageQuotaRepository {

  private final JdbcTemplate jdbcTemplate;

  public JdbcUserUsageQuotaRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public Optional<UserUsageQuota> findByUserIdAndQuotaType(String userId, String quotaType) {
    List<UserUsageQuota> results = jdbcTemplate.query(
        """
        SELECT user_id, quota_type, used_count, limit_count, updated_at
        FROM user_usage_quota
        WHERE user_id = ? AND quota_type = ?
        LIMIT 1
        """,
        (resultSet, rowNum) -> mapRow(resultSet),
        userId,
        quotaType
    );
    return results.stream().findFirst();
  }

  @Override
  public boolean tryConsume(String userId, String quotaType, int limitCount) {
    if (tryUpdateExistingQuota(userId, quotaType, limitCount)) {
      return true;
    }
    try {
      int insertedRows = jdbcTemplate.update(
          """
          INSERT INTO user_usage_quota (user_id, quota_type, used_count, limit_count, updated_at)
          VALUES (?, ?, 1, ?, CURRENT_TIMESTAMP)
          """,
          userId,
          quotaType,
          limitCount
      );
      return insertedRows > 0;
    } catch (DuplicateKeyException ignored) {
      return tryUpdateExistingQuota(userId, quotaType, limitCount);
    }
  }

  @Override
  public void release(String userId, String quotaType) {
    jdbcTemplate.update(
        """
        UPDATE user_usage_quota
        SET used_count = CASE WHEN used_count > 0 THEN used_count - 1 ELSE 0 END,
            updated_at = CURRENT_TIMESTAMP
        WHERE user_id = ? AND quota_type = ?
        """,
        userId,
        quotaType
    );
  }

  private boolean tryUpdateExistingQuota(String userId, String quotaType, int limitCount) {
    int updatedRows = jdbcTemplate.update(
        """
        UPDATE user_usage_quota
        SET used_count = used_count + 1,
            limit_count = ?,
            updated_at = CURRENT_TIMESTAMP
        WHERE user_id = ? AND quota_type = ? AND used_count < ?
        """,
        limitCount,
        userId,
        quotaType,
        limitCount
    );
    return updatedRows > 0;
  }

  private UserUsageQuota mapRow(ResultSet resultSet) throws SQLException {
    return new UserUsageQuota(
        resultSet.getString("user_id"),
        resultSet.getString("quota_type"),
        resultSet.getInt("used_count"),
        resultSet.getInt("limit_count"),
        toInstant(resultSet.getTimestamp("updated_at"))
    );
  }

  private Instant toInstant(Timestamp timestamp) {
    return timestamp == null ? Instant.now() : timestamp.toInstant();
  }
}
