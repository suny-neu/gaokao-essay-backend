package com.gaokao.essay.backend.config;

import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.util.StringUtils;

@Configuration
public class JdbcStorageConfig {

  @Bean
  @ConditionalOnProperty(prefix = "gaokao.storage.database", name = "enabled", havingValue = "true")
  public DataSource databaseDataSource(GaokaoProperties properties) {
    GaokaoProperties.Database database = properties.getStorage().getDatabase();
    if (!StringUtils.hasText(database.getUrl())) {
      throw new IllegalStateException("GAOKAO_DATABASE_ENABLED=true 时必须提供 GAOKAO_DATABASE_URL");
    }
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName(database.getDriverClassName());
    dataSource.setUrl(database.getUrl());
    dataSource.setUsername(database.getUsername());
    dataSource.setPassword(database.getPassword());
    return dataSource;
  }

  @Bean
  @ConditionalOnBean(DataSource.class)
  public JdbcTemplate jdbcTemplate(DataSource dataSource) {
    return new JdbcTemplate(dataSource);
  }
}
