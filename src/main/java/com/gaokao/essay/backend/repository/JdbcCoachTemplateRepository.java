package com.gaokao.essay.backend.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gaokao.essay.backend.model.CoachTemplate;
import com.gaokao.essay.backend.util.TextUtils;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Primary
@ConditionalOnBean(JdbcTemplate.class)
public class JdbcCoachTemplateRepository implements CoachTemplateRepository {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public JdbcCoachTemplateRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  @Override
  public List<CoachTemplate> findEnabledByEssayType(String essayType) {
    return jdbcTemplate.query(
        """
        SELECT id, essay_type, scenario, task_purpose, official_logic, opening_strategy,
               body_strategy, ending_strategy, must_include_json, risk_points_json,
               useful_expressions_json, trigger_keywords_json, enabled, sort_order, updated_at
        FROM coach_template
        WHERE enabled = TRUE AND essay_type = ?
        ORDER BY sort_order ASC, id ASC
        """,
        (resultSet, rowNum) -> mapRow(resultSet),
        essayType
    );
  }

  @Override
  public int countAll() {
    Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM coach_template", Integer.class);
    return count == null ? 0 : count;
  }

  @Override
  public void saveAll(List<CoachTemplate> templates) {
    for (CoachTemplate template : templates) {
      int updatedRows = jdbcTemplate.update(
          """
          UPDATE coach_template
          SET essay_type = ?, scenario = ?, task_purpose = ?, official_logic = ?,
              opening_strategy = ?, body_strategy = ?, ending_strategy = ?,
              must_include_json = ?, risk_points_json = ?, useful_expressions_json = ?,
              trigger_keywords_json = ?, enabled = ?, sort_order = ?, updated_at = ?
          WHERE id = ?
          """,
          template.getEssayType(),
          template.getScenario(),
          template.getTaskPurpose(),
          template.getOfficialLogic(),
          template.getOpeningStrategy(),
          template.getBodyStrategy(),
          template.getEndingStrategy(),
          writeJson(template.getMustInclude()),
          writeJson(template.getRiskPoints()),
          writeJson(template.getUsefulExpressions()),
          writeJson(template.getTriggerKeywords()),
          template.isEnabled(),
          template.getSortOrder(),
          Timestamp.from(template.getUpdatedAt()),
          template.getId()
      );
      if (updatedRows > 0) {
        continue;
      }
      try {
        jdbcTemplate.update(
            """
            INSERT INTO coach_template (
                id, essay_type, scenario, task_purpose, official_logic, opening_strategy,
                body_strategy, ending_strategy, must_include_json, risk_points_json,
                useful_expressions_json, trigger_keywords_json, enabled, sort_order, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            template.getId(),
            template.getEssayType(),
            template.getScenario(),
            template.getTaskPurpose(),
            template.getOfficialLogic(),
            template.getOpeningStrategy(),
            template.getBodyStrategy(),
            template.getEndingStrategy(),
            writeJson(template.getMustInclude()),
            writeJson(template.getRiskPoints()),
            writeJson(template.getUsefulExpressions()),
            writeJson(template.getTriggerKeywords()),
            template.isEnabled(),
            template.getSortOrder(),
            Timestamp.from(template.getUpdatedAt())
        );
      } catch (DuplicateKeyException ignored) {
        jdbcTemplate.update(
            """
            UPDATE coach_template
            SET essay_type = ?, scenario = ?, task_purpose = ?, official_logic = ?,
                opening_strategy = ?, body_strategy = ?, ending_strategy = ?,
                must_include_json = ?, risk_points_json = ?, useful_expressions_json = ?,
                trigger_keywords_json = ?, enabled = ?, sort_order = ?, updated_at = ?
            WHERE id = ?
            """,
            template.getEssayType(),
            template.getScenario(),
            template.getTaskPurpose(),
            template.getOfficialLogic(),
            template.getOpeningStrategy(),
            template.getBodyStrategy(),
            template.getEndingStrategy(),
            writeJson(template.getMustInclude()),
            writeJson(template.getRiskPoints()),
            writeJson(template.getUsefulExpressions()),
            writeJson(template.getTriggerKeywords()),
            template.isEnabled(),
            template.getSortOrder(),
            Timestamp.from(template.getUpdatedAt()),
            template.getId()
        );
      }
    }
  }

  private CoachTemplate mapRow(ResultSet resultSet) throws SQLException {
    CoachTemplate template = new CoachTemplate();
    template.setId(resultSet.getString("id"));
    template.setEssayType(resultSet.getString("essay_type"));
    template.setScenario(resultSet.getString("scenario"));
    template.setTaskPurpose(resultSet.getString("task_purpose"));
    template.setOfficialLogic(resultSet.getString("official_logic"));
    template.setOpeningStrategy(resultSet.getString("opening_strategy"));
    template.setBodyStrategy(resultSet.getString("body_strategy"));
    template.setEndingStrategy(resultSet.getString("ending_strategy"));
    template.setMustInclude(readList(resultSet.getString("must_include_json")));
    template.setRiskPoints(readList(resultSet.getString("risk_points_json")));
    template.setUsefulExpressions(readList(resultSet.getString("useful_expressions_json")));
    template.setTriggerKeywords(readList(resultSet.getString("trigger_keywords_json")));
    template.setEnabled(resultSet.getBoolean("enabled"));
    template.setSortOrder(resultSet.getInt("sort_order"));
    Timestamp updatedAt = resultSet.getTimestamp("updated_at");
    if (updatedAt != null) {
      template.setUpdatedAt(updatedAt.toInstant());
    }
    return template;
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to serialize coach template JSON", exception);
    }
  }

  private List<String> readList(String rawJson) {
    if (TextUtils.isBlank(rawJson)) {
      return List.of();
    }
    try {
      return objectMapper.readValue(rawJson, new TypeReference<List<String>>() {
      });
    } catch (Exception exception) {
      return List.of();
    }
  }
}
