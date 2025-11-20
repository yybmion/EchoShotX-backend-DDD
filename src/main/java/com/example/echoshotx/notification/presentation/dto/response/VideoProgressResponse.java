package com.example.echoshotx.notification.presentation.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * SSE를 통해 클라이언트에 전송되는 비디오 진행률 응답.
 * 알림 엔티티로 저장되지 않고, 실시간으로만 전송됩니다.
 */
@Getter
@Builder
public class VideoProgressResponse {

  /** 진행률 업데이트 타입 (항상 "progress") */
  @Builder.Default private final String type = "progress";

  /** 비디오 ID */
  private final Long videoId;

  /** 진행률 (0-100) */
  private final Integer progressPercentage;

  /** 예상 남은 시간 (초) */
  private final Integer estimatedTimeLeftSeconds;

  /** 현재 처리 단계 (예: "영상 분석 중", "AI 처리 중", "인코딩 중") */
  private final String currentStep;

  /** 타임스탬프 */
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
  private final LocalDateTime timestamp;
}
