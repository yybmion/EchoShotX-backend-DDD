package com.example.echoshotx.video.presentation.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI 서버에서 처리 진행률을 백엔드로 전송하는 웹훅 요청.
 *
 * <p>AI 서버 → 백엔드 (비동기 Webhook 콜백)
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class WebhookProcessingProgressRequest {

  @NotNull(message = "videoId는 필수입니다.")
  private Long videoId;

  @NotBlank(message = "aiJobId는 필수입니다.")
  private String aiJobId;

  /**
   * 처리 진행률 (0-100)
   */
  @NotNull(message = "progress는 필수입니다.")
  @Min(value = 0, message = "progress는 0 이상이어야 합니다.")
  @Max(value = 100, message = "progress는 100 이하여야 합니다.")
  private Integer progress;

  /**
   * 현재 처리 단계 (예: "Analyzing", "Enhancing", "Encoding")
   */
  private String currentStage;

  /**
   * 예상 남은 시간 (초)
   */
  private Integer estimatedTimeRemainingSeconds;
}
