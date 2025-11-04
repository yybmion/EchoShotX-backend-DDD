package com.example.echoshotx.notification.application.event;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 영상 처리 시작 이벤트
 */
@Getter
public class VideoProcessingStartedEvent {
    private final Long videoId;
    private final Long memberId;
    private final String fileName;
    private final String processingType;
    private final LocalDateTime occurredAt;

    public VideoProcessingStartedEvent(Long videoId, Long memberId, String fileName, String processingType) {
        this.videoId = videoId;
        this.memberId = memberId;
        this.fileName = fileName;
        this.processingType = processingType;
        this.occurredAt = LocalDateTime.now();
    }
}
