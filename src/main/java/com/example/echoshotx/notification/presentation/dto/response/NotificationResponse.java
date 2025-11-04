package com.example.echoshotx.notification.presentation.dto.response;

import com.example.echoshotx.notification.domain.entity.Notification;
import com.example.echoshotx.notification.domain.entity.NotificationStatus;
import com.example.echoshotx.notification.domain.entity.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {

    private Long id;
    private NotificationType type;
    private String category;
    private String title;
    private String content;
    private Boolean isRead;
    private NotificationStatus status;
    private Integer retryCount;
    private Long videoId;
    private Long creditHistoryId;
    private LocalDateTime createdAt;

    /**
     * Factory method to create response from entity
     */
    public static NotificationResponse from(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType())
                .category(notification.getType().getCategory())
                .title(notification.getTitle())
                .content(notification.getContent())
                .isRead(notification.getIsRead())
                .status(notification.getStatus())
                .retryCount(notification.getRetryCount())
                .videoId(notification.getVideoId())
                .creditHistoryId(notification.getCreditHistoryId())
                .createdAt(notification.getCreatedDate())
                .build();
    }
}
