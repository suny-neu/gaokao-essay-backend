package com.gaokao.essay.backend.repository;

import com.gaokao.essay.backend.store.AppState;
import com.gaokao.essay.backend.store.StateStore;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

@Repository
public class StateStoreEssayRecordRepository implements EssayRecordRepository {

  private final StateStore stateStore;

  public StateStoreEssayRecordRepository(StateStore stateStore) {
    this.stateStore = stateStore;
  }

  @Override
  public AppState.EssayRecord save(AppState.EssayRecord record) {
    return stateStore.write(state -> {
      state.essays.put(record.id, record);
      return record;
    });
  }

  @Override
  public AppState.EssayRecord findOrCreatePendingByClientRequestId(AppState.EssayRecord record) {
    if (record == null || record.clientRequestId == null || record.clientRequestId.isBlank()) {
      return save(record);
    }
    return stateStore.write(state -> state.essays.values().stream()
        .filter(item -> Objects.equals(item.userId, record.userId))
        .filter(item -> Objects.equals(item.clientRequestId, record.clientRequestId))
        .findFirst()
        .orElseGet(() -> {
          state.essays.put(record.id, record);
          return record;
        }));
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
    return stateStore.read(state -> state.essays.values().stream()
        .filter(record -> Objects.equals(record.userId, userId))
        .filter(record -> matches(record.mode, mode))
        .filter(record -> matches(record.essayType, essayType))
        .filter(record -> matches(record.taskStatus, taskStatus))
        .sorted(Comparator.comparingLong((AppState.EssayRecord record) -> record.createdAt).reversed())
        .skip(Math.max(offset, 0))
        .limit(Math.max(limit, 0))
        .collect(Collectors.toList()));
  }

  @Override
  public Optional<AppState.EssayRecord> findByIdAndUserId(String id, String userId) {
    return stateStore.read(state -> {
      AppState.EssayRecord record = state.essays.get(id);
      if (record == null || !Objects.equals(record.userId, userId)) {
        return Optional.empty();
      }
      return Optional.of(record);
    });
  }

  @Override
  public Optional<AppState.EssayRecord> findByUserIdAndClientRequestId(String userId, String clientRequestId) {
    return stateStore.read(state -> state.essays.values().stream()
        .filter(record -> Objects.equals(record.userId, userId))
        .filter(record -> Objects.equals(record.clientRequestId, clientRequestId))
        .findFirst());
  }

  @Override
  public int deleteByIdAndUserId(String id, String userId) {
    return stateStore.write(state -> {
      AppState.EssayRecord record = state.essays.get(id);
      if (record == null || !Objects.equals(record.userId, userId)) {
        return 0;
      }
      state.essays.remove(id);
      return 1;
    });
  }

  @Override
  public int deleteByUserId(String userId, String mode, String essayType, String taskStatus) {
    return stateStore.write(state -> {
      List<String> ids = state.essays.values().stream()
          .filter(record -> Objects.equals(record.userId, userId))
          .filter(record -> matches(record.mode, mode))
          .filter(record -> matches(record.essayType, essayType))
          .filter(record -> matches(record.taskStatus, taskStatus))
          .map(record -> record.id)
          .toList();
      ids.forEach(state.essays::remove);
      return ids.size();
    });
  }

  private boolean matches(String value, String expected) {
    if (expected == null || expected.isBlank() || "all".equalsIgnoreCase(expected)) {
      return true;
    }
    return expected.equalsIgnoreCase(value);
  }
}
