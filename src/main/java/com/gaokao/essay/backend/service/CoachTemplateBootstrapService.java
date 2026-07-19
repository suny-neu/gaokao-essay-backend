package com.gaokao.essay.backend.service;

import com.gaokao.essay.backend.config.GaokaoProperties;
import com.gaokao.essay.backend.model.CoachTemplate;
import com.gaokao.essay.backend.repository.BundledCoachTemplateRepository;
import com.gaokao.essay.backend.repository.CoachTemplateRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnBean(JdbcTemplate.class)
public class CoachTemplateBootstrapService implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(CoachTemplateBootstrapService.class);

  private final GaokaoProperties properties;
  private final CoachTemplateRepository coachTemplateRepository;
  private final BundledCoachTemplateRepository bundledCoachTemplateRepository;

  public CoachTemplateBootstrapService(
      GaokaoProperties properties,
      CoachTemplateRepository coachTemplateRepository,
      BundledCoachTemplateRepository bundledCoachTemplateRepository
  ) {
    this.properties = properties;
    this.coachTemplateRepository = coachTemplateRepository;
    this.bundledCoachTemplateRepository = bundledCoachTemplateRepository;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!properties.getStorage().getDatabase().isEnabled()) {
      return;
    }
    if (coachTemplateRepository.countAll() > 0) {
      return;
    }
    List<CoachTemplate> seeds = bundledCoachTemplateRepository.loadAll();
    coachTemplateRepository.saveAll(seeds);
    log.info("Bootstrapped {} coach template seed(s) into {}.", seeds.size(), properties.getStorage().getDatabase().resolveKind());
  }
}
