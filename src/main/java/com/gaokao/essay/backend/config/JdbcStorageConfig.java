package com.gaokao.essay.backend.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
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
    HikariConfig config = new HikariConfig();
    config.setDriverClassName(database.getDriverClassName());
    config.setJdbcUrl(database.getUrl());
    config.setUsername(database.getUsername());
    config.setPassword(database.getPassword());
    config.setMaximumPoolSize(8);
    config.setMinimumIdle(2);
    // 远程数据库（云数据库 / 连接池中间层）常会杀空闲连接，保活避免每次请求都付出重连握手代价
    config.setKeepaliveTime(30000);
    config.setMaxLifetime(240000);
    config.setIdleTimeout(120000);
    config.setConnectionTimeout(10000);
    config.setValidationTimeout(3000);
    config.setInitializationFailTimeout(-1);
    return new HikariDataSource(config);
  }

  @Bean
  @ConditionalOnBean(DataSource.class)
  public JdbcTemplate jdbcTemplate(DataSource dataSource) {
    return new JdbcTemplate(dataSource);
  }
}
