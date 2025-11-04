package com.example.echoshotx.notification.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 알림 재시도 스케줄러
 * 전송 실패한 알림을 주기적으로 재시도합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRetryScheduler {

    private final NotificationService notificationService;

    /**
     * 5분마다 실패한 알림 재시도
     * cron: 초 분 시 일 월 요일
     */
    @Scheduled(cron = "0 */5 * * * *") // Every 5 minutes
    public void retryFailedNotifications() {
        log.info("Starting scheduled retry of failed notifications");
        try {
            notificationService.retryFailedNotifications();
            log.info("Completed scheduled retry of failed notifications");
        } catch (Exception e) {
            log.error("Error during scheduled retry of failed notifications", e);
        }
    }

    /**
     * 매일 자정에 30일 이상 된 알림 삭제
     */
    @Scheduled(cron = "0 0 0 * * *") // Every day at midnight
    public void cleanupOldNotifications() {
        log.info("Starting scheduled cleanup of old notifications");
        try {
            notificationService.deleteOldNotifications();
            log.info("Completed scheduled cleanup of old notifications");
        } catch (Exception e) {
            log.error("Error during scheduled cleanup of old notifications", e);
        }
    }
}
