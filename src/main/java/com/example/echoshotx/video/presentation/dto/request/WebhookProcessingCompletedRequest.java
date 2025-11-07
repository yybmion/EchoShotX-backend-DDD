package com.example.echoshotx.video.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI 서버에서 처리 완료 시 백엔드로 전송하는 웹훅 요청
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class WebhookProcessingCompletedRequest {

    @NotNull(message = "videoId는 필수입니다")
    private Long videoId;

    @NotBlank(message = "aiJobId는 필수입니다")
    private String aiJobId;

    // 처리된 영상 S3 정보
    @NotBlank(message = "processedS3Key는 필수입니다")
    private String processedS3Key;

    @NotNull(message = "processedFileSizeBytes는 필수입니다")
    @Positive(message = "processedFileSizeBytes는 양수여야 합니다")
    private Long processedFileSizeBytes;

    // 처리된 영상 메타데이터
    private Double processedDurationSeconds;
    private Integer processedWidth;
    private Integer processedHeight;
    private String processedCodec;
    private Long processedBitrate;
    private Double processedFrameRate;

    // 썸네일 (옵셔널)
    private String thumbnailS3Key;
}
