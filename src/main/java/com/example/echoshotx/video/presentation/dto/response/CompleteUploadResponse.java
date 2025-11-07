package com.example.echoshotx.video.presentation.dto.response;

import com.example.echoshotx.video.domain.entity.Video;
import com.example.echoshotx.video.domain.entity.VideoStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 업로드 완료 응답 (AI 처리 시작됨)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompleteUploadResponse {

    private Long videoId;
    private VideoStatus status;
    private String message;
    private String sqsMessageId;

    public static CompleteUploadResponse from(Video video) {
        return CompleteUploadResponse.builder()
                .videoId(video.getId())
                .status(video.getStatus())
                .message("영상 처리가 시작되었습니다")
                .sqsMessageId(video.getSqsMessageId())
                .build();
    }
}
