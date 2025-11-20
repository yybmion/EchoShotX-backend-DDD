package com.example.echoshotx.video.infrastructure.redis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Redis Pub/Sub를 통해 전송되는 비디오 진행률 메시지.
 * AI 서버에서 Redis에 Publish하면 백엔드가 Subscribe하여 수신합니다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoProgressMessage {

    /**
     * 비디오 ID.
     */
    @JsonProperty("video_id")
    private Long videoId;

    /**
     * AI 작업 ID (선택사항).
     */
    @JsonProperty("ai_job_id")
    private String aiJobId;

    /**
     * 진행률 (0-100).
     */
    @JsonProperty("progress_percentage")
    private Integer progressPercentage;

    /**
     * 예상 남은 시간 (초).
     */
    @JsonProperty("estimated_time_left_seconds")
    private Integer estimatedTimeLeftSeconds;

    /**
     * 현재 처리 단계.
     * 예: "영상 분석 중", "AI 처리 중", "인코딩 중"
     */
    @JsonProperty("current_step")
    private String currentStep;

    /**
     * 메시지 타임스탬프 (ISO-8601 형식).
     */
    @JsonProperty("timestamp")
    private String timestamp;
}
