package com.gaokao.essay.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

class JdbcStorageConfigTest {

  @Test
  void createsConnectionPoolForDatabaseStorage() {
    GaokaoProperties properties = new GaokaoProperties();
    properties.getStorage().getDatabase().setUrl("jdbc:postgresql://127.0.0.1:5432/gaokao");
    properties.getStorage().getDatabase().setUsername("user");
    properties.getStorage().getDatabase().setPassword("password");

    try (HikariDataSource dataSource = (HikariDataSource) new JdbcStorageConfig().databaseDataSource(properties)) {
      assertThat(dataSource).isInstanceOf(HikariDataSource.class);
    }
  }
}
