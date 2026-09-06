package com.gaokao.essay.backend.service;

/** 当用户的作文记录、会员权益或广告额度发生变化时发布，用于让首页 dashboard 缓存立刻失效。 */
public record DashboardInvalidationEvent(String userId) {
}
