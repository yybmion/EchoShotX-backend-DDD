package com.example.echoshotx.notification.application.event;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 영상 처리 실패 이벤트
 */
@Getter
public class VideoProcessingFailedEvent {
    private final Long videoId;
    private final Long memberId;
    private final String fileName;
    private final String reason;
    private final LocalDateTime occurredAt;

    public VideoProcessingFailedEvent(Long videoId, Long memberId, String fileName, String reason) {
        this.videoId = videoId;
        this.memberId = memberId;
        this.fileName = fileName;
        this.reason = reason;
        this.occurredAt = LocalDateTime.now();
    }
}
