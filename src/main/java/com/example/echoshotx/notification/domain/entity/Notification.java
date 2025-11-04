package com.example.echoshotx.notification.domain.entity;

import com.example.echoshotx.shared.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "notification",
    indexes = {
        @Index(name = "idx_notification_member_id", columnList = "member_id"),
        @Index(name = "idx_notification_is_read", columnList = "is_read"),
        @Index(name = "idx_notification_created_date", columnList = "created_date"),
        @Index(name = "idx_notification_member_read", columnList = "member_id, is_read"),
        @Index(name = "idx_notification_status", columnList = "status")
    }
)
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationType type;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.PENDING;

    // Retry information
    @Column(nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column
    private LocalDateTime lastRetryAt;

    // Related entity IDs for reference
    @Column
    private Long videoId;

    @Column
    private Long creditHistoryId;

    // Business methods

    /**
     * Factory method for creating video-related notifications
     */
    public static Notification createVideoNotification(
            Long memberId,
            Long videoId,
            NotificationType type,
            String title,
            String content
    ) {
        validateVideoNotificationType(type);
        return Notification.builder()
                .memberId(memberId)
                .videoId(videoId)
                .type(type)
                .title(title)
                .content(content)
                .isRead(false)
                .status(NotificationStatus.PENDING)
                .retryCount(0)
                .build();
    }

    /**
     * Factory method for creating credit-related notifications
     */
    public static Notification createCreditNotification(
            Long memberId,
            Long creditHistoryId,
            NotificationType type,
            String title,
            String content
    ) {
        validateCreditNotificationType(type);
        return Notification.builder()
                .memberId(memberId)
                .creditHistoryId(creditHistoryId)
                .type(type)
                .title(title)
                .content(content)
                .isRead(false)
                .status(NotificationStatus.PENDING)
                .retryCount(0)
                .build();
    }

    /**
     * Factory method for creating system notifications
     */
    public static Notification createSystemNotification(
            Long memberId,
            String title,
            String content
    ) {
        return Notification.builder()
                .memberId(memberId)
                .type(NotificationType.SYSTEM_ANNOUNCEMENT)
                .title(title)
                .content(content)
                .isRead(false)
                .status(NotificationStatus.PENDING)
                .retryCount(0)
                .build();
    }

    /**
     * Mark notification as read
     */
    public void markAsRead() {
        if (this.isRead) {
            return; // Already read, no-op
        }
        this.isRead = true;
    }

    /**
     * Mark notification as sent successfully
     */
    public void markAsSent() {
        this.status = NotificationStatus.SENT;
    }

    /**
     * Mark notification as failed and increment retry count
     */
    public void markAsFailed() {
        this.status = NotificationStatus.FAILED;
        this.retryCount++;
        this.lastRetryAt = LocalDateTime.now();
    }

    /**
     * Check if notification can be retried (max 3 attempts)
     */
    public boolean canRetry() {
        return this.status == NotificationStatus.FAILED && this.retryCount < 3;
    }

    /**
     * Reset status to pending for retry
     */
    public void resetForRetry() {
        if (!canRetry()) {
            throw new IllegalStateException("Cannot retry notification - max retry count exceeded or wrong status");
        }
        this.status = NotificationStatus.PENDING;
    }

    // Domain validation methods

    private static void validateVideoNotificationType(NotificationType type) {
        if (type != NotificationType.VIDEO_UPLOAD_COMPLETED &&
            type != NotificationType.VIDEO_PROCESSING_STARTED &&
            type != NotificationType.VIDEO_PROCESSING_COMPLETED &&
            type != NotificationType.VIDEO_PROCESSING_FAILED) {
            throw new IllegalArgumentException("Invalid video notification type: " + type);
        }
    }

    private static void validateCreditNotificationType(NotificationType type) {
        if (type != NotificationType.CREDIT_CHARGED &&
            type != NotificationType.CREDIT_USED &&
            type != NotificationType.CREDIT_REFUNDED) {
            throw new IllegalArgumentException("Invalid credit notification type: " + type);
        }
    }
}
