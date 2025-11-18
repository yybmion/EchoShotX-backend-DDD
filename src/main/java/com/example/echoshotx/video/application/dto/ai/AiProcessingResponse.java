package com.example.echoshotx.video.application.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 서버에서 처리 요청에 대한 응답 DTO.
 *
 * <p>AI 서버 → 백엔드로 전송됩니다 (동기 응답).
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AiProcessingResponse {

  /** AI 작업 ID */
  private String jobId;

  /** 작업 상태 (QUEUED, PROCESSING, etc.) */
  private String status;

  /** 예상 완료 시간 (옵셔널) */
  private LocalDateTime estimatedCompletionTime;

  /** 응답 메시지 */
  private String message;
}
