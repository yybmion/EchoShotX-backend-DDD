package com.example.echoshotx.video.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 클라이언트가 S3 업로드 완료 후 백엔드에 전송하는 요청
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CompleteUploadRequest {

    @NotNull(message = "비디오 duration은 필수입니다")
    @Positive(message = "비디오 duration은 양수여야 합니다")
    private Double durationSeconds;

    @NotNull(message = "비디오 width는 필수입니다")
    @Positive(message = "비디오 width는 양수여야 합니다")
    private Integer width;

    @NotNull(message = "비디오 height는 필수입니다")
    @Positive(message = "비디오 height는 양수여야 합니다")
    private Integer height;

    private String codec;

    private Long bitrate;

    private Double frameRate;
}
