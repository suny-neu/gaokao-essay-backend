package com.gaokao.essay.backend.repository;

import com.gaokao.essay.backend.model.UserBinding;
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
public class JdbcUserBindingRepository implements UserBindingRepository {

  private final JdbcTemplate jdbcTemplate;

  public JdbcUserBindingRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public Optional<UserBinding> findByOpenId(String openId) {
    List<UserBinding> results = jdbcTemplate.query(
        """
        SELECT user_id, open_id, created_at, last_login_at
        FROM user_binding
        WHERE open_id = ?
        LIMIT 1
        """,
        (resultSet, rowNum) -> mapRow(resultSet),
        openId
    );
    return results.stream().findFirst();
  }

  @Override
  public UserBinding save(UserBinding binding) {
    int updatedRows = jdbcTemplate.update(
        """
        UPDATE user_binding
        SET user_id = ?, last_login_at = ?
        WHERE open_id = ?
        """,
        binding.userId(),
        Timestamp.from(binding.lastLoginAt()),
        binding.openId()
    );

    if (updatedRows == 0) {
      try {
        jdbcTemplate.update(
            """
            INSERT INTO user_binding (user_id, open_id, created_at, last_login_at)
            VALUES (?, ?, ?, ?)
            """,
            binding.userId(),
            binding.openId(),
            Timestamp.from(binding.createdAt()),
            Timestamp.from(binding.lastLoginAt())
        );
      } catch (DuplicateKeyException ignored) {
        jdbcTemplate.update(
            """
            UPDATE user_binding
            SET last_login_at = ?
            WHERE open_id = ?
            """,
            Timestamp.from(binding.lastLoginAt()),
            binding.openId()
        );
        return findByOpenId(binding.openId()).orElse(binding);
      }
    }

    return binding;
  }

  private UserBinding mapRow(ResultSet resultSet) throws SQLException {
    return new UserBinding(
        resultSet.getString("user_id"),
        resultSet.getString("open_id"),
        toInstant(resultSet.getTimestamp("created_at")),
        toInstant(resultSet.getTimestamp("last_login_at"))
    );
  }

  private Instant toInstant(Timestamp timestamp) {
    return timestamp == null ? Instant.now() : timestamp.toInstant();
  }
}
