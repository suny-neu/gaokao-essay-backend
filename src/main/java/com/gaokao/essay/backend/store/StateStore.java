package com.gaokao.essay.backend.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gaokao.essay.backend.config.GaokaoProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class StateStore {

  private static final Logger log = LoggerFactory.getLogger(StateStore.class);

  private final ObjectMapper objectMapper;
  private final GaokaoProperties properties;
  private final Path stateFile;
  private AppState state = new AppState();

  public StateStore(ObjectMapper objectMapper, GaokaoProperties properties) {
    this.objectMapper = objectMapper;
    this.properties = properties;
    this.stateFile = Paths.get(properties.getStorage().getStateFile()).toAbsolutePath().normalize();
  }

  @PostConstruct
  public synchronized void init() {
    try {
      Files.createDirectories(stateFile.getParent());
      if (Files.exists(stateFile)) {
        this.state = objectMapper.readValue(stateFile.toFile(), AppState.class);
      } else {
        persist();
      }
    } catch (IOException error) {
      log.warn("Failed to initialize state file {}, using empty state", stateFile, error);
      this.state = new AppState();
    }
  }

  public synchronized <T> T read(Function<AppState, T> reader) {
    return reader.apply(state);
  }

  public synchronized <T> T write(Function<AppState, T> writer) {
    T result = writer.apply(state);
    persist();
    return result;
  }

  private void persist() {
    try {
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(stateFile.toFile(), state);
    } catch (IOException error) {
      throw new IllegalStateException("Failed to persist runtime state", error);
    }
  }

  public Path getStateFile() {
    return stateFile;
  }

  public GaokaoProperties getProperties() {
    return properties;
  }
}
