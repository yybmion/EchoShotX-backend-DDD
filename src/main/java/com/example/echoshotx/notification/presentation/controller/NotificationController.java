package com.example.echoshotx.notification.presentation.controller;

import com.example.echoshotx.member.domain.entity.Member;
import com.example.echoshotx.notification.application.service.NotificationService;
import com.example.echoshotx.notification.application.service.SseConnectionManager;
import com.example.echoshotx.notification.domain.entity.NotificationType;
import com.example.echoshotx.notification.presentation.dto.response.NotificationResponse;
import com.example.echoshotx.notification.presentation.dto.response.UnreadCountResponse;
import com.example.echoshotx.shared.annotation.CurrentMember;
import com.example.echoshotx.shared.exception.payload.ApiResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Slf4j
@Tag(name = "Notification", description = "실시간 알림 API")
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final SseConnectionManager sseConnectionManager;

    @Operation(
            summary = "SSE 연결",
            description = "실시간 알림을 받기 위한 SSE 연결을 생성합니다. " +
                    "클라이언트는 이 엔드포인트로 연결하여 서버로부터 실시간 알림을 수신할 수 있습니다."
    )
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@CurrentMember Member member) {
        log.info("SSE connection request from member: {}", member.getId());
        return sseConnectionManager.createConnection(member.getId());
    }

    @Operation(
            summary = "알림 목록 조회",
            description = "현재 사용자의 모든 알림을 조회합니다. 최신순으로 정렬됩니다."
    )
    @GetMapping
    public ApiResponseDto<List<NotificationResponse>> getNotifications(
            @CurrentMember Member member
    ) {
        List<NotificationResponse> notifications = notificationService.getNotifications(member.getId());
        return ApiResponseDto.onSuccess(notifications);
    }

    @Operation(
            summary = "읽지 않은 알림 조회",
            description = "현재 사용자의 읽지 않은 알림만 조회합니다."
    )
    @GetMapping("/unread")
    public ApiResponseDto<List<NotificationResponse>> getUnreadNotifications(
            @CurrentMember Member member
    ) {
        List<NotificationResponse> notifications = notificationService.getUnreadNotifications(member.getId());
        return ApiResponseDto.onSuccess(notifications);
    }

    @Operation(
            summary = "읽지 않은 알림 개수",
            description = "현재 사용자의 읽지 않은 알림 개수를 조회합니다."
    )
    @GetMapping("/unread/count")
    public ApiResponseDto<UnreadCountResponse> getUnreadCount(
            @CurrentMember Member member
    ) {
        Long count = notificationService.getUnreadCount(member.getId());
        return ApiResponseDto.onSuccess(UnreadCountResponse.of(count));
    }

    @Operation(
            summary = "타입별 알림 조회",
            description = "특정 타입의 알림만 조회합니다. (VIDEO_*, CREDIT_*, SYSTEM_*)"
    )
    @GetMapping("/type/{type}")
    public ApiResponseDto<List<NotificationResponse>> getNotificationsByType(
            @CurrentMember Member member,
            @PathVariable NotificationType type
    ) {
        List<NotificationResponse> notifications = notificationService.getNotificationsByType(
                member.getId(), type);
        return ApiResponseDto.onSuccess(notifications);
    }

    @Operation(
            summary = "알림 읽음 처리",
            description = "특정 알림을 읽음 상태로 변경합니다."
    )
    @PatchMapping("/{notificationId}/read")
    public ApiResponseDto<Void> markAsRead(
            @PathVariable Long notificationId,
            @CurrentMember Member member
    ) {
        notificationService.markAsRead(notificationId, member.getId());
        return ApiResponseDto.onSuccess(null);
    }

    @Operation(
            summary = "모든 알림 읽음 처리",
            description = "현재 사용자의 모든 알림을 읽음 상태로 변경합니다."
    )
    @PatchMapping("/read-all")
    public ApiResponseDto<Void> markAllAsRead(
            @CurrentMember Member member
    ) {
        notificationService.markAllAsRead(member.getId());
        return ApiResponseDto.onSuccess(null);
    }

    @Operation(
            summary = "알림 삭제",
            description = "특정 알림을 삭제합니다."
    )
    @DeleteMapping("/{notificationId}")
    public ApiResponseDto<Void> deleteNotification(
            @PathVariable Long notificationId,
            @CurrentMember Member member
    ) {
        notificationService.deleteNotification(notificationId, member.getId());
        return ApiResponseDto.onSuccess(null);
    }

    @Operation(
            summary = "SSE 연결 상태 확인",
            description = "현재 사용자의 활성 SSE 연결 수를 조회합니다. (디버깅/모니터링용)"
    )
    @GetMapping("/connection/status")
    public ApiResponseDto<Integer> getConnectionStatus(
            @CurrentMember Member member
    ) {
        int connectionCount = sseConnectionManager.getConnectionCount(member.getId());
        return ApiResponseDto.onSuccess(connectionCount);
    }
}
