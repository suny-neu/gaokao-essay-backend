package com.gaokao.essay.backend.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gaokao.essay.backend.model.AuthenticatedUser;
import com.gaokao.essay.backend.repository.EssayRecordRepository;
import com.gaokao.essay.backend.store.AppState;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DashboardServiceTest {

  private final MembershipService membershipService = Mockito.mock(MembershipService.class);
  private final GrowthProfileService growthProfileService = Mockito.mock(GrowthProfileService.class);
  private final EssayRecordRepository essayRecordRepository = Mockito.mock(EssayRecordRepository.class);
  private final DashboardService service = new DashboardService(
      membershipService,
      growthProfileService,
      essayRecordRepository,
      Clock.fixed(Instant.parse("2026-08-29T00:00:00Z"), ZoneOffset.UTC)
  );

  @Test
  void reusesDashboardRecordsWhenBuildingGrowthProfile() {
    List<AppState.EssayRecord> records = List.of(record("grade", "application", "12分 / 15"));
    when(essayRecordRepository.findRecentDashboardByUserId("user-1"))
        .thenReturn(records);
    when(membershipService.getEntitlement(any()))
        .thenReturn(java.util.Map.of("subscriptionActive", false));

    service.build(user(), "application");

    verify(essayRecordRepository).findRecentDashboardByUserId("user-1");
    verify(essayRecordRepository, never()).findRecentByUserId(any(), Mockito.anyInt(), Mockito.anyInt(), any(), any(), any());
    verify(growthProfileService).buildFromRecords(records, "application");
    verify(growthProfileService, never()).load(any(), eq("application"));
  }

  @Test
  void servesCachedDashboardUntilInvalidated() {
    List<AppState.EssayRecord> records = List.of(record("grade", "application", "12分 / 15"));
    when(essayRecordRepository.findRecentDashboardByUserId("user-1")).thenReturn(records);
    when(membershipService.getEntitlement(any()))
        .thenReturn(java.util.Map.of("subscriptionActive", false));

    service.build(user(), "application");
    service.build(user(), "application");
    // 第二次命中缓存：数据库与权益服务只被调用一次
    verify(essayRecordRepository, times(1)).findRecentDashboardByUserId("user-1");
    verify(membershipService, times(1)).getEntitlement(any());

    service.onDashboardInvalidation(new DashboardInvalidationEvent("user-1"));
    service.build(user(), "application");
    // 失效后重新查询
    verify(essayRecordRepository, times(2)).findRecentDashboardByUserId("user-1");
  }

  private AuthenticatedUser user() {
    Instant now = Instant.parse("2026-08-29T00:00:00Z");
    return new AuthenticatedUser("user-1", "open-1", now, now.plusSeconds(3600));
  }

  private AppState.EssayRecord record(String mode, String essayType, String scoreText) {
    AppState.EssayRecord record = new AppState.EssayRecord();
    record.id = "record-1";
    record.mode = mode;
    record.essayType = essayType;
    record.scoreText = scoreText;
    record.createdAt = Instant.parse("2026-08-28T00:00:00Z").toEpochMilli();
    return record;
  }
}
