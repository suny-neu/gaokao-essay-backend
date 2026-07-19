package com.gaokao.essay.backend.service;

import com.gaokao.essay.backend.model.ApiException;
import com.gaokao.essay.backend.model.AuthenticatedUser;
import com.gaokao.essay.backend.repository.EssayRecordRepository;
import com.gaokao.essay.backend.store.AppState;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class HistoryService {

  private final EssayRecordRepository essayRecordRepository;

  public HistoryService(EssayRecordRepository essayRecordRepository) {
    this.essayRecordRepository = essayRecordRepository;
  }

  public AppState.EssayRecord saveRecord(AppState.EssayRecord record) {
    return essayRecordRepository.save(record);
  }

  public AppState.EssayRecord findOrCreatePendingRecord(AppState.EssayRecord record) {
    return essayRecordRepository.findOrCreatePendingByClientRequestId(record);
  }

  public AppState.EssayRecord getRecordByClientRequestId(String userId, String clientRequestId) {
    return essayRecordRepository.findByUserIdAndClientRequestId(userId, clientRequestId).orElse(null);
  }

  public Map<String, Object> listRecords(AuthenticatedUser user, int offset, int limit, String mode, String essayType, String taskStatus) {
    int safeOffset = Math.max(offset, 0);
    int safeLimit = Math.max(Math.min(limit, 20), 1);
    List<AppState.EssayRecord> records = essayRecordRepository.findRecentByUserId(
        user.userId(),
        safeOffset,
        safeLimit + 1,
        mode,
        essayType,
        taskStatus
    );
    boolean hasMore = records.size() > safeLimit;
    List<AppState.EssayRecord> items = hasMore ? records.subList(0, safeLimit) : records;

    Map<String, Object> page = new LinkedHashMap<>();
    page.put("items", items);
    page.put("offset", safeOffset);
    page.put("limit", safeLimit);
    page.put("hasMore", hasMore);
    page.put("nextOffset", safeOffset + items.size());
    return page;
  }

  public AppState.EssayRecord getRecord(AuthenticatedUser user, String id) {
    return essayRecordRepository.findByIdAndUserId(id, user.userId())
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "HISTORY_NOT_FOUND", "未找到对应历史记录"));
  }

  public Map<String, Object> deleteRecord(AuthenticatedUser user, String id) {
    int affectedCount = essayRecordRepository.deleteByIdAndUserId(id, user.userId());
    if (affectedCount == 0) {
      throw new ApiException(HttpStatus.NOT_FOUND, "HISTORY_NOT_FOUND", "未找到对应历史记录");
    }
    return Map.of("affectedCount", affectedCount);
  }

  public Map<String, Object> clearRecords(AuthenticatedUser user, String mode, String essayType, String taskStatus) {
    int affectedCount = essayRecordRepository.deleteByUserId(user.userId(), mode, essayType, taskStatus);
    return Map.of("affectedCount", affectedCount);
  }
}
