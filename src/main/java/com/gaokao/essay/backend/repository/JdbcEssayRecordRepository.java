package com.gaokao.essay.backend.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gaokao.essay.backend.store.AppState;
import com.gaokao.essay.backend.util.TextUtils;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@Primary
@ConditionalOnBean(JdbcTemplate.class)
public class JdbcEssayRecordRepository implements EssayRecordRepository {

  private static final String SELECT_COLUMNS = """
      SELECT id, client_request_id, user_id, open_id, mode, essay_type, band, band_label, band_value,
             created_at, content, word_count, score_text, summary, source, task_status,
             prompt_snapshot_json, coach_plan_json, analysis_json
      FROM essay_record
      """;

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public JdbcEssayRecordRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  @Override
  public AppState.EssayRecord save(AppState.EssayRecord record) {
    int updatedRows = updateRecord(record);
    if (updatedRows == 0) {
      try {
        insertRecord(record);
      } catch (DuplicateKeyException ignored) {
        if (!TextUtils.isBlank(record.clientRequestId)) {
          return findByUserIdAndClientRequestId(record.userId, record.clientRequestId)
              .map(existing -> {
                if (record.id.equals(existing.id)) {
                  updateRecord(record);
                  return record;
                }
                return existing;
              })
              .orElseGet(() -> {
                updateRecord(record);
                return record;
              });
        }
        updateRecord(record);
      }
    }
    return record;
  }

  @Override
  public AppState.EssayRecord findOrCreatePendingByClientRequestId(AppState.EssayRecord record) {
    if (TextUtils.isBlank(record.clientRequestId)) {
      return save(record);
    }
    Optional<AppState.EssayRecord> existing = findByUserIdAndClientRequestId(record.userId, record.clientRequestId);
    if (existing.isPresent()) {
      return existing.get();
    }
    try {
      insertRecord(record);
      return record;
    } catch (DuplicateKeyException ignored) {
      return findByUserIdAndClientRequestId(record.userId, record.clientRequestId).orElse(record);
    }
  }

  @Override
  public List<AppState.EssayRecord> findRecentByUserId(
      String userId,
      int offset,
      int limit,
      String mode,
      String essayType,
      String taskStatus
  ) {
    StringBuilder sql = new StringBuilder(SELECT_COLUMNS).append(" WHERE user_id = ?");
    List<Object> params = new ArrayList<>();
    params.add(userId);
    appendFilters(sql, params, mode, essayType, taskStatus);
    sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
    params.add(limit);
    params.add(offset);
    return jdbcTemplate.query(sql.toString(), rowMapper(), params.toArray());
  }

  @Override
  public Optional<AppState.EssayRecord> findByIdAndUserId(String id, String userId) {
    List<AppState.EssayRecord> results = jdbcTemplate.query(
        SELECT_COLUMNS + " WHERE id = ? AND user_id = ? LIMIT 1",
        rowMapper(),
        id,
        userId
    );
    return results.stream().findFirst();
  }

  @Override
  public Optional<AppState.EssayRecord> findByUserIdAndClientRequestId(String userId, String clientRequestId) {
    if (TextUtils.isBlank(clientRequestId)) {
      return Optional.empty();
    }
    List<AppState.EssayRecord> results = jdbcTemplate.query(
        SELECT_COLUMNS + " WHERE user_id = ? AND client_request_id = ? LIMIT 1",
        rowMapper(),
        userId,
        clientRequestId
    );
    return results.stream().findFirst();
  }

  @Override
  public int deleteByIdAndUserId(String id, String userId) {
    return jdbcTemplate.update("DELETE FROM essay_record WHERE id = ? AND user_id = ?", id, userId);
  }

  @Override
  public int deleteByUserId(String userId, String mode, String essayType, String taskStatus) {
    StringBuilder sql = new StringBuilder("DELETE FROM essay_record WHERE user_id = ?");
    List<Object> params = new ArrayList<>();
    params.add(userId);
    appendFilters(sql, params, mode, essayType, taskStatus);
    return jdbcTemplate.update(sql.toString(), params.toArray());
  }

  private void appendFilters(StringBuilder sql, List<Object> params, String mode, String essayType, String taskStatus) {
    if (!TextUtils.isBlank(mode) && !"all".equalsIgnoreCase(mode)) {
      sql.append(" AND mode = ?");
      params.add(mode);
    }
    if (!TextUtils.isBlank(essayType) && !"all".equalsIgnoreCase(essayType)) {
      sql.append(" AND essay_type = ?");
      params.add(essayType);
    }
    if (!TextUtils.isBlank(taskStatus) && !"all".equalsIgnoreCase(taskStatus)) {
      sql.append(" AND task_status = ?");
      params.add(taskStatus);
    }
  }

  private RowMapper<AppState.EssayRecord> rowMapper() {
    return (resultSet, rowNum) -> mapRow(resultSet);
  }

  private int updateRecord(AppState.EssayRecord record) {
    return jdbcTemplate.update(
        """
        UPDATE essay_record
        SET client_request_id = ?, user_id = ?, open_id = ?, mode = ?, essay_type = ?, band = ?, band_label = ?,
            band_value = ?, created_at = ?, content = ?, word_count = ?, score_text = ?,
            summary = ?, source = ?, task_status = ?, prompt_snapshot_json = ?,
            coach_plan_json = ?, analysis_json = ?
        WHERE id = ?
        """,
        nullable(record.clientRequestId),
        record.userId,
        record.openId,
        record.mode,
        record.essayType,
        record.band,
        record.bandLabel,
        record.bandValue,
        record.createdAt,
        record.content,
        record.wordCount,
        record.scoreText,
        record.summary,
        record.source,
        record.taskStatus,
        writeJson(record.promptSnapshot),
        writeJson(record.coachPlan),
        writeJson(record.analysis),
        record.id
    );
  }

  private void insertRecord(AppState.EssayRecord record) {
    jdbcTemplate.update(
        """
        INSERT INTO essay_record (
            id, client_request_id, user_id, open_id, mode, essay_type, band, band_label, band_value,
            created_at, content, word_count, score_text, summary, source, task_status,
            prompt_snapshot_json, coach_plan_json, analysis_json
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        record.id,
        nullable(record.clientRequestId),
        record.userId,
        record.openId,
        record.mode,
        record.essayType,
        record.band,
        record.bandLabel,
        record.bandValue,
        record.createdAt,
        record.content,
        record.wordCount,
        record.scoreText,
        record.summary,
        record.source,
        record.taskStatus,
        writeJson(record.promptSnapshot),
        writeJson(record.coachPlan),
        writeJson(record.analysis)
    );
  }

  private AppState.EssayRecord mapRow(ResultSet resultSet) throws SQLException {
    AppState.EssayRecord record = new AppState.EssayRecord();
    record.id = resultSet.getString("id");
    record.clientRequestId = resultSet.getString("client_request_id");
    record.userId = resultSet.getString("user_id");
    record.openId = resultSet.getString("open_id");
    record.mode = resultSet.getString("mode");
    record.essayType = resultSet.getString("essay_type");
    record.band = resultSet.getString("band");
    record.bandLabel = resultSet.getString("band_label");
    record.bandValue = resultSet.getString("band_value");
    record.createdAt = resultSet.getLong("created_at");
    record.content = resultSet.getString("content");
    record.wordCount = resultSet.getInt("word_count");
    record.scoreText = resultSet.getString("score_text");
    record.summary = resultSet.getString("summary");
    record.source = resultSet.getString("source");
    record.taskStatus = resultSet.getString("task_status");
    record.promptSnapshot = readJson(resultSet.getString("prompt_snapshot_json"), AppState.PromptSnapshot.class, new AppState.PromptSnapshot());
    record.coachPlan = readJson(resultSet.getString("coach_plan_json"), AppState.CoachPlan.class, null);
    record.analysis = readJson(resultSet.getString("analysis_json"), AppState.GradeAnalysis.class, null);
    return record;
  }

  private String writeJson(Object value) {
    if (value == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to serialize essay record JSON", exception);
    }
  }

  private <T> T readJson(String json, Class<T> type, T fallback) {
    if (TextUtils.isBlank(json)) {
      return fallback;
    }
    try {
      return objectMapper.readValue(json, type);
    } catch (Exception exception) {
      return fallback;
    }
  }

  private String nullable(String value) {
    return TextUtils.isBlank(value) ? null : value.trim();
  }
}
