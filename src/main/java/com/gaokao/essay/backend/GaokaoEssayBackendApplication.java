package com.gaokao.essay.backend;

import com.gaokao.essay.backend.config.GaokaoProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(GaokaoProperties.class)
public class GaokaoEssayBackendApplication {

  public static void main(String[] args) {
    SpringApplication.run(GaokaoEssayBackendApplication.class, args);
  }
}
