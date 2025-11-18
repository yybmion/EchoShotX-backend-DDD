package com.example.echoshotx.video.application.dto.ai;

import com.example.echoshotx.video.domain.entity.ProcessingType;
import lombok.Builder;
import lombok.Getter;

/**
 * AI 서버로 영상 처리를 요청할 때 사용하는 DTO.
 *
 * <p>백엔드 → AI 서버로 전송됩니다.
 */
@Getter
@Builder
public class AiProcessingRequest {

  /** 영상 ID */
  private Long videoId;

  /** S3에 업로드된 원본 영상 키 */
  private String s3Key;

  /** 처리 유형 (BASIC_ENHANCEMENT, AI_UPSCALING) */
  private ProcessingType processingType;

  /** Webhook 콜백 URL (처리 완료/실패/진행률) */
  private String callbackUrl;

  /** 원본 영상 메타데이터 */
  private VideoMetadataDto metadata;

  /** 원본 영상 메타데이터 DTO */
  @Getter
  @Builder
  public static class VideoMetadataDto {
    private Double durationSeconds;
    private Integer width;
    private Integer height;
    private String codec;
    private Long bitrate;
    private Double frameRate;
  }
}
