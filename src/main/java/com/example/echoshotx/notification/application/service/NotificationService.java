package com.example.echoshotx.notification.application.service;

import com.example.echoshotx.notification.application.adaptor.NotificationAdaptor;
import com.example.echoshotx.notification.domain.entity.Notification;
import com.example.echoshotx.notification.domain.entity.NotificationStatus;
import com.example.echoshotx.notification.domain.entity.NotificationType;
import com.example.echoshotx.notification.domain.exception.NotificationErrorStatus;
import com.example.echoshotx.notification.infrastructure.persistence.NotificationRepository;
import com.example.echoshotx.notification.presentation.dto.response.NotificationResponse;
import com.example.echoshotx.notification.presentation.exception.NotificationHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationAdaptor notificationAdaptor;
    private final SseConnectionManager sseConnectionManager;

    /**
     * 영상 관련 알림 생성 및 전송
     */
    public Notification createAndSendVideoNotification(
            Long memberId,
            Long videoId,
            NotificationType type,
            String title,
            String content
    ) {
        // 도메인 팩토리 메서드로 생성
        Notification notification = Notification.createVideoNotification(
                memberId, videoId, type, title, content);

        // 알림 저장
        notification = notificationRepository.save(notification);
        log.info("Video notification created: id={}, memberId={}, type={}",
                notification.getId(), memberId, type);

        // 실시간 전송 시도
        sendNotificationRealtime(notification);

        return notification;
    }

    /**
     * 크레딧 관련 알림 생성 및 전송
     */
    public Notification createAndSendCreditNotification(
            Long memberId,
            Long creditHistoryId,
            NotificationType type,
            String title,
            String content
    ) {
        // 도메인 팩토리 메서드로 생성
        Notification notification = Notification.createCreditNotification(
                memberId, creditHistoryId, type, title, content);

        // 알림 저장
        notification = notificationRepository.save(notification);
        log.info("Credit notification created: id={}, memberId={}, type={}",
                notification.getId(), memberId, type);

        // 실시간 전송 시도
        sendNotificationRealtime(notification);

        return notification;
    }

    /**
     * 시스템 알림 생성 및 전송
     */
    public Notification createAndSendSystemNotification(
            Long memberId,
            String title,
            String content
    ) {
        // 도메인 팩토리 메서드로 생성
        Notification notification = Notification.createSystemNotification(
                memberId, title, content);

        // 알림 저장
        notification = notificationRepository.save(notification);
        log.info("System notification created: id={}, memberId={}",
                notification.getId(), memberId);

        // 실시간 전송 시도
        sendNotificationRealtime(notification);

        return notification;
    }

    /**
     * 실시간 알림 전송
     */
    private void sendNotificationRealtime(Notification notification) {
        try {
            NotificationResponse response = NotificationResponse.from(notification);
            boolean sent = sseConnectionManager.sendToMember(notification.getMemberId(), response);

            if (sent) {
                notification.markAsSent();
                notificationRepository.save(notification);
                log.info("Notification sent successfully: id={}", notification.getId());
            } else {
                notification.markAsFailed();
                notificationRepository.save(notification);
                log.warn("Failed to send notification (no active connection): id={}", notification.getId());
            }
        } catch (Exception e) {
            notification.markAsFailed();
            notificationRepository.save(notification);
            log.error("Error sending notification: id={}, error={}", notification.getId(), e.getMessage(), e);
        }
    }

    /**
     * 알림 읽음 처리
     */
    public void markAsRead(Long notificationId, Long memberId) {
        // 소유권 검증
        notificationAdaptor.validateNotificationOwnership(notificationId, memberId);

        Notification notification = notificationAdaptor.queryById(notificationId);
        notification.markAsRead();
        notificationRepository.save(notification);

        log.info("Notification marked as read: id={}, memberId={}", notificationId, memberId);
    }

    /**
     * 모든 알림 읽음 처리
     */
    public void markAllAsRead(Long memberId) {
        List<Notification> unreadNotifications = notificationAdaptor.queryUnreadByMemberId(memberId);

        for (Notification notification : unreadNotifications) {
            notification.markAsRead();
        }

        notificationRepository.saveAll(unreadNotifications);
        log.info("All notifications marked as read for member: {}, count: {}",
                memberId, unreadNotifications.size());
    }

    /**
     * 알림 삭제
     */
    public void deleteNotification(Long notificationId, Long memberId) {
        // 소유권 검증
        notificationAdaptor.validateNotificationOwnership(notificationId, memberId);

        notificationRepository.deleteById(notificationId);
        log.info("Notification deleted: id={}, memberId={}", notificationId, memberId);
    }

    /**
     * 알림 목록 조회
     */
    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(Long memberId) {
        List<Notification> notifications = notificationAdaptor.queryAllByMemberId(memberId);
        return notifications.stream()
                .map(NotificationResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 읽지 않은 알림 목록 조회
     */
    @Transactional(readOnly = true)
    public List<NotificationResponse> getUnreadNotifications(Long memberId) {
        List<Notification> notifications = notificationAdaptor.queryUnreadByMemberId(memberId);
        return notifications.stream()
                .map(NotificationResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 읽지 않은 알림 개수 조회
     */
    @Transactional(readOnly = true)
    public Long getUnreadCount(Long memberId) {
        return notificationAdaptor.countUnreadByMemberId(memberId);
    }

    /**
     * 타입별 알림 조회
     */
    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotificationsByType(Long memberId, NotificationType type) {
        List<Notification> notifications = notificationAdaptor.queryByMemberIdAndType(memberId, type);
        return notifications.stream()
                .map(NotificationResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 실패한 알림 재시도
     */
    public void retryFailedNotifications() {
        // 5분 전에 실패한 알림 조회
        LocalDateTime retryAfter = LocalDateTime.now().minusMinutes(5);
        List<Notification> failedNotifications = notificationAdaptor.queryFailedNotificationsForRetry(retryAfter);

        log.info("Retrying {} failed notifications", failedNotifications.size());

        for (Notification notification : failedNotifications) {
            if (!notification.canRetry()) {
                log.warn("Cannot retry notification (max attempts reached): id={}", notification.getId());
                continue;
            }

            try {
                notification.resetForRetry();
                notificationRepository.save(notification);
                sendNotificationRealtime(notification);
            } catch (Exception e) {
                log.error("Error retrying notification: id={}, error={}",
                        notification.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * 오래된 알림 삭제 (30일 이상)
     */
    public void deleteOldNotifications() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);
        notificationRepository.deleteByCreatedDateBefore(cutoffDate);
        log.info("Deleted notifications older than: {}", cutoffDate);
    }
}
