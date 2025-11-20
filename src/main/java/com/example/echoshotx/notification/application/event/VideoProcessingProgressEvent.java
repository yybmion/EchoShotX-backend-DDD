package com.example.echoshotx.notification.application.event;

import java.time.LocalDateTime;
import lombok.Getter;

/**
 * 영상 처리 진행률 업데이트 이벤트.
 */
@Getter
public class VideoProcessingProgressEvent {

  private final Long videoId;
  private final Long memberId;
  private final Integer progressPercentage;
  private final Integer estimatedTimeLeftSeconds;
  private final String currentStep;
  private final LocalDateTime occurredAt;

  public VideoProcessingProgressEvent(
      Long videoId,
      Long memberId,
      Integer progressPercentage,
      Integer estimatedTimeLeftSeconds,
      String currentStep) {
    this.videoId = videoId;
    this.memberId = memberId;
    this.progressPercentage = progressPercentage;
    this.estimatedTimeLeftSeconds = estimatedTimeLeftSeconds;
    this.currentStep = currentStep;
    this.occurredAt = LocalDateTime.now();
  }
}
