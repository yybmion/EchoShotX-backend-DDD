package com.example.echoshotx.notification.application.event;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 영상 처리 완료 이벤트
 */
@Getter
public class VideoProcessingCompletedEvent {
    private final Long videoId;
    private final Long memberId;
    private final String fileName;
    private final LocalDateTime occurredAt;

    public VideoProcessingCompletedEvent(Long videoId, Long memberId, String fileName) {
        this.videoId = videoId;
        this.memberId = memberId;
        this.fileName = fileName;
        this.occurredAt = LocalDateTime.now();
    }
}
