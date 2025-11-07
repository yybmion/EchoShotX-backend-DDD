package com.example.echoshotx.video.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI 서버에서 처리 실패 시 백엔드로 전송하는 웹훅 요청
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class WebhookProcessingFailedRequest {

    @NotNull(message = "videoId는 필수입니다")
    private Long videoId;

    @NotBlank(message = "aiJobId는 필수입니다")
    private String aiJobId;

    @NotBlank(message = "errorMessage는 필수입니다")
    private String errorMessage;

    private String errorCode;
}
