package com.gaokao.essay.backend.repository;

import com.gaokao.essay.backend.store.AppState;
import java.util.List;
import java.util.Optional;

public interface EssayRecordRepository {

  AppState.EssayRecord save(AppState.EssayRecord record);

  AppState.EssayRecord findOrCreatePendingByClientRequestId(AppState.EssayRecord record);

  List<AppState.EssayRecord> findRecentByUserId(
      String userId,
      int offset,
      int limit,
      String mode,
      String essayType,
      String taskStatus
  );

  Optional<AppState.EssayRecord> findByIdAndUserId(String id, String userId);

  Optional<AppState.EssayRecord> findByUserIdAndClientRequestId(String userId, String clientRequestId);

  int deleteByIdAndUserId(String id, String userId);

  int deleteByUserId(String userId, String mode, String essayType, String taskStatus);
}
