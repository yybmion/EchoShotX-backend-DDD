package com.example.echoshotx.video.presentation.controller;

import com.example.echoshotx.video.application.usecase.*;
import com.example.echoshotx.member.domain.entity.Member;
import com.example.echoshotx.video.presentation.dto.request.*;
import com.example.echoshotx.video.presentation.dto.response.*;
import com.example.echoshotx.shared.exception.payload.dto.ApiResponseDto;
import com.example.echoshotx.shared.security.aop.CurrentMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "Video", description = "영상 업로드 및 관리 API")
@RestController
@RequestMapping("/videos")
@RequiredArgsConstructor
public class VideoController {

    // UseCases
    private final InitiateVideoUploadUseCase initiateVideoUploadUseCase;
    private final GetVideoUseCase getVideoUseCase;
    private final CompleteVideoUploadUseCase completeVideoUploadUseCase;
    private final ProcessingCompletedWebhookUseCase processingCompletedWebhookUseCase;
    private final ProcessingFailedWebhookUseCase processingFailedWebhookUseCase;

    @Operation(summary = "영상 조회", description = "영상 ID로 영상 정보를 조회합니다")
    @GetMapping("/{videoId}")
    public ApiResponseDto<VideoDetailResponse> getVideo(
            @PathVariable Long videoId,
            @CurrentMember Member member) {
        
        VideoDetailResponse response = getVideoUseCase.execute(videoId, member);
        return ApiResponseDto.onSuccess(response);
    }

    @Operation(
            summary = "영상 업로드 시작",
            description = "영상 업로드를 위한 Presigned URL을 발급받습니다. " +
                    "이 URL로 클라이언트가 직접 S3에 업로드합니다."
    )
    @PostMapping("/upload/initiate")
    public ApiResponseDto<InitiateUploadResponse> initiateUpload(
            @Valid @RequestBody InitiateUploadRequest request,
            @CurrentMember Member member
    ) {
        InitiateUploadResponse response = initiateVideoUploadUseCase.execute(request, member);
        return ApiResponseDto.onSuccess(response);
    }

    @Operation(
            summary = "영상 업로드 완료 및 처리 시작",
            description = "클라이언트가 S3 업로드 완료 후 호출합니다. " +
                    "크레딧을 차감하고 AI 처리를 시작하며, 처리 시작 알림을 전송합니다."
    )
    @PostMapping("/{videoId}/complete-upload")
    public ApiResponseDto<CompleteUploadResponse> completeUpload(
            @PathVariable Long videoId,
            @Valid @RequestBody CompleteUploadRequest request,
            @CurrentMember Member member
    ) {
        CompleteUploadResponse response = completeVideoUploadUseCase.execute(videoId, request, member);
        return ApiResponseDto.onSuccess(response);
    }

    @Operation(
            summary = "AI 처리 완료 웹훅",
            description = "AI 서버에서 처리 완료 시 호출하는 웹훅 엔드포인트입니다. " +
                    "처리 완료 알림을 SSE로 브로드캐스팅합니다."
    )
    @PostMapping("/webhook/processing-completed")
    public ApiResponseDto<Void> processingCompletedWebhook(
            @Valid @RequestBody WebhookProcessingCompletedRequest request
    ) {
        processingCompletedWebhookUseCase.execute(request);
        return ApiResponseDto.onSuccess(null);
    }

    @Operation(
            summary = "AI 처리 실패 웹훅",
            description = "AI 서버에서 처리 실패 시 호출하는 웹훅 엔드포인트입니다. " +
                    "처리 실패 알림을 전송하고 크레딧을 환불합니다."
    )
    @PostMapping("/webhook/processing-failed")
    public ApiResponseDto<Void> processingFailedWebhook(
            @Valid @RequestBody WebhookProcessingFailedRequest request
    ) {
        processingFailedWebhookUseCase.execute(request);
        return ApiResponseDto.onSuccess(null);
    }

}
