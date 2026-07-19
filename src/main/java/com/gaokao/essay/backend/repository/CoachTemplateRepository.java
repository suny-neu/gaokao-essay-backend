package com.gaokao.essay.backend.repository;

import com.gaokao.essay.backend.model.CoachTemplate;
import java.util.List;

public interface CoachTemplateRepository {

  List<CoachTemplate> findEnabledByEssayType(String essayType);

  int countAll();

  void saveAll(List<CoachTemplate> templates);
}
