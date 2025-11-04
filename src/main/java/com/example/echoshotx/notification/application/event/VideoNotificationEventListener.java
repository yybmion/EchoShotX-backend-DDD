package com.example.echoshotx.notification.application.event;

import com.example.echoshotx.notification.application.service.NotificationService;
import com.example.echoshotx.notification.domain.entity.NotificationType;
import com.example.echoshotx.video.domain.entity.Video;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 영상 관련 이벤트를 수신하여 알림을 생성하는 리스너
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoNotificationEventListener {

    private final NotificationService notificationService;

    @Async
    @EventListener
    public void handleVideoUploadCompleted(VideoUploadCompletedEvent event) {
        log.info("Handling VideoUploadCompletedEvent for video: {}", event.getVideoId());

        notificationService.createAndSendVideoNotification(
                event.getMemberId(),
                event.getVideoId(),
                NotificationType.VIDEO_UPLOAD_COMPLETED,
                "영상 업로드 완료",
                String.format("'%s' 영상 업로드가 완료되었습니다.", event.getFileName())
        );
    }

    @Async
    @EventListener
    public void handleVideoProcessingStarted(VideoProcessingStartedEvent event) {
        log.info("Handling VideoProcessingStartedEvent for video: {}", event.getVideoId());

        notificationService.createAndSendVideoNotification(
                event.getMemberId(),
                event.getVideoId(),
                NotificationType.VIDEO_PROCESSING_STARTED,
                "영상 처리 시작",
                String.format("'%s' 영상 처리가 시작되었습니다. 처리 타입: %s",
                        event.getFileName(), event.getProcessingType())
        );
    }

    @Async
    @EventListener
    public void handleVideoProcessingCompleted(VideoProcessingCompletedEvent event) {
        log.info("Handling VideoProcessingCompletedEvent for video: {}", event.getVideoId());

        notificationService.createAndSendVideoNotification(
                event.getMemberId(),
                event.getVideoId(),
                NotificationType.VIDEO_PROCESSING_COMPLETED,
                "영상 처리 완료",
                String.format("'%s' 영상 처리가 완료되었습니다. 다운로드 가능합니다.",
                        event.getFileName())
        );
    }

    @Async
    @EventListener
    public void handleVideoProcessingFailed(VideoProcessingFailedEvent event) {
        log.info("Handling VideoProcessingFailedEvent for video: {}", event.getVideoId());

        notificationService.createAndSendVideoNotification(
                event.getMemberId(),
                event.getVideoId(),
                NotificationType.VIDEO_PROCESSING_FAILED,
                "영상 처리 실패",
                String.format("'%s' 영상 처리가 실패했습니다. 사유: %s",
                        event.getFileName(), event.getReason())
        );
    }
}
