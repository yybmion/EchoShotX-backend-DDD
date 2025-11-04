package com.example.echoshotx.notification.presentation.dto.request;

import com.example.echoshotx.notification.domain.entity.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Request for sending manual notifications (admin use case)
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SendNotificationRequest {

    @NotNull(message = "알림 타입은 필수입니다")
    private NotificationType type;

    @NotBlank(message = "제목은 필수입니다")
    private String title;

    @NotBlank(message = "내용은 필수입니다")
    private String content;
}
