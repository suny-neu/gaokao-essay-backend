package com.gaokao.essay.backend.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gaokao.essay.backend.model.CoachTemplate;
import com.gaokao.essay.backend.util.TextUtils;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;

@Repository
public class BundledCoachTemplateRepository implements CoachTemplateRepository {

  private final List<CoachTemplate> templates;

  public BundledCoachTemplateRepository(ObjectMapper objectMapper) {
    this.templates = loadTemplates(objectMapper);
  }

  @Override
  public List<CoachTemplate> findEnabledByEssayType(String essayType) {
    String normalizedType = TextUtils.lower(essayType);
    return templates.stream()
        .filter(CoachTemplate::isEnabled)
        .filter(item -> normalizedType.equals(TextUtils.lower(item.getEssayType())))
        .sorted(Comparator.comparingInt(CoachTemplate::getSortOrder))
        .collect(Collectors.toList());
  }

  @Override
  public int countAll() {
    return templates.size();
  }

  @Override
  public void saveAll(List<CoachTemplate> ignored) {
    // Bundled fallback repository is read-only by design.
  }

  public List<CoachTemplate> loadAll() {
    return templates;
  }

  private List<CoachTemplate> loadTemplates(ObjectMapper objectMapper) {
    try {
      ClassPathResource resource = new ClassPathResource("coach-template-seeds.json");
      try (InputStream inputStream = resource.getInputStream()) {
        return objectMapper.readValue(inputStream, new TypeReference<List<CoachTemplate>>() {
        });
      }
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to load bundled coach template seeds", exception);
    }
  }
}
