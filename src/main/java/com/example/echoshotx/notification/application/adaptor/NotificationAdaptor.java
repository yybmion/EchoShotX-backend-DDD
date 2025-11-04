package com.example.echoshotx.notification.application.adaptor;

import com.example.echoshotx.notification.domain.entity.Notification;
import com.example.echoshotx.notification.domain.entity.NotificationStatus;
import com.example.echoshotx.notification.domain.entity.NotificationType;
import com.example.echoshotx.notification.domain.exception.NotificationErrorStatus;
import com.example.echoshotx.notification.infrastructure.persistence.NotificationRepository;
import com.example.echoshotx.notification.presentation.exception.NotificationHandler;
import com.example.echoshotx.shared.annotation.Adaptor;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Adaptor
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationAdaptor {

    private final NotificationRepository notificationRepository;

    /**
     * Query notification by ID
     */
    public Notification queryById(Long notificationId) {
        return notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotificationHandler(NotificationErrorStatus.NOTIFICATION_NOT_FOUND));
    }

    /**
     * Query all notifications for a member
     */
    public List<Notification> queryAllByMemberId(Long memberId) {
        return notificationRepository.findByMemberIdOrderByCreatedDateDesc(memberId);
    }

    /**
     * Query unread notifications for a member
     */
    public List<Notification> queryUnreadByMemberId(Long memberId) {
        return notificationRepository.findByMemberIdAndIsReadOrderByCreatedDateDesc(memberId, false);
    }

    /**
     * Query read notifications for a member
     */
    public List<Notification> queryReadByMemberId(Long memberId) {
        return notificationRepository.findByMemberIdAndIsReadOrderByCreatedDateDesc(memberId, true);
    }

    /**
     * Query notifications by member and type
     */
    public List<Notification> queryByMemberIdAndType(Long memberId, NotificationType type) {
        return notificationRepository.findByMemberIdAndTypeOrderByCreatedDateDesc(memberId, type);
    }

    /**
     * Query unread notifications by member and type
     */
    public List<Notification> queryUnreadByMemberIdAndType(Long memberId, NotificationType type) {
        return notificationRepository.findByMemberIdAndIsReadAndTypeOrderByCreatedDateDesc(
                memberId, false, type);
    }

    /**
     * Count unread notifications for a member
     */
    public Long countUnreadByMemberId(Long memberId) {
        return notificationRepository.countByMemberIdAndIsRead(memberId, false);
    }

    /**
     * Query failed notifications for retry
     */
    public List<Notification> queryFailedNotificationsForRetry(LocalDateTime retryAfter) {
        return notificationRepository.findFailedNotificationsForRetry(
                NotificationStatus.FAILED, retryAfter);
    }

    /**
     * Query notifications by status
     */
    public List<Notification> queryByStatus(NotificationStatus status) {
        return notificationRepository.findByStatusOrderByCreatedDateDesc(status);
    }

    /**
     * Query notifications by video ID
     */
    public List<Notification> queryByVideoId(Long videoId) {
        return notificationRepository.findByVideoIdOrderByCreatedDateDesc(videoId);
    }

    /**
     * Query notifications by credit history ID
     */
    public List<Notification> queryByCreditHistoryId(Long creditHistoryId) {
        return notificationRepository.findByCreditHistoryIdOrderByCreatedDateDesc(creditHistoryId);
    }

    /**
     * Validate notification belongs to member
     */
    public void validateNotificationOwnership(Long notificationId, Long memberId) {
        Notification notification = queryById(notificationId);
        if (!notification.getMemberId().equals(memberId)) {
            throw new NotificationHandler(NotificationErrorStatus.NOTIFICATION_ACCESS_DENIED);
        }
    }
}
