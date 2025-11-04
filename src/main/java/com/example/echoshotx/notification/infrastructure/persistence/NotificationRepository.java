package com.example.echoshotx.notification.infrastructure.persistence;

import com.example.echoshotx.notification.domain.entity.Notification;
import com.example.echoshotx.notification.domain.entity.NotificationStatus;
import com.example.echoshotx.notification.domain.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Find all notifications for a specific member, ordered by creation date (newest first)
     */
    List<Notification> findByMemberIdOrderByCreatedDateDesc(Long memberId);

    /**
     * Find unread notifications for a specific member
     */
    List<Notification> findByMemberIdAndIsReadOrderByCreatedDateDesc(Long memberId, Boolean isRead);

    /**
     * Find notifications by member and type
     */
    List<Notification> findByMemberIdAndTypeOrderByCreatedDateDesc(Long memberId, NotificationType type);

    /**
     * Find notifications by member, read status, and type
     */
    List<Notification> findByMemberIdAndIsReadAndTypeOrderByCreatedDateDesc(
            Long memberId, Boolean isRead, NotificationType type);

    /**
     * Count unread notifications for a member
     */
    Long countByMemberIdAndIsRead(Long memberId, Boolean isRead);

    /**
     * Find failed notifications that can be retried
     */
    @Query("SELECT n FROM Notification n WHERE n.status = :status AND n.retryCount < 3 " +
           "AND (n.lastRetryAt IS NULL OR n.lastRetryAt < :retryAfter)")
    List<Notification> findFailedNotificationsForRetry(
            @Param("status") NotificationStatus status,
            @Param("retryAfter") LocalDateTime retryAfter);

    /**
     * Find all notifications by status
     */
    List<Notification> findByStatusOrderByCreatedDateDesc(NotificationStatus status);

    /**
     * Delete old notifications (for cleanup)
     */
    void deleteByCreatedDateBefore(LocalDateTime date);

    /**
     * Find notifications by video ID
     */
    List<Notification> findByVideoIdOrderByCreatedDateDesc(Long videoId);

    /**
     * Find notifications by credit history ID
     */
    List<Notification> findByCreditHistoryIdOrderByCreatedDateDesc(Long creditHistoryId);
}
