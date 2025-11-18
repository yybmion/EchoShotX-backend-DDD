package com.example.echoshotx.video.presentation.controller;

import com.example.echoshotx.member.domain.entity.Member;
import com.example.echoshotx.shared.exception.payload.dto.ApiResponseDto;
import com.example.echoshotx.shared.security.aop.CurrentMember;
import com.example.echoshotx.video.application.usecase.CompleteVideoUploadUseCase;
import com.example.echoshotx.video.application.usecase.GetVideoUseCase;
import com.example.echoshotx.video.application.usecase.InitiateVideoUploadUseCase;
import com.example.echoshotx.video.application.usecase.ProcessingCompletedWebhookUseCase;
import com.example.echoshotx.video.application.usecase.ProcessingFailedWebhookUseCase;
import com.example.echoshotx.video.application.usecase.ProcessingProgressWebhookUseCase;
import com.example.echoshotx.video.presentation.dto.request.CompleteUploadRequest;
import com.example.echoshotx.video.presentation.dto.request.InitiateUploadRequest;
import com.example.echoshotx.video.presentation.dto.request.WebhookProcessingCompletedRequest;
import com.example.echoshotx.video.presentation.dto.request.WebhookProcessingFailedRequest;
import com.example.echoshotx.video.presentation.dto.request.WebhookProcessingProgressRequest;
import com.example.echoshotx.video.presentation.dto.response.CompleteUploadResponse;
import com.example.echoshotx.video.presentation.dto.response.InitiateUploadResponse;
import com.example.echoshotx.video.presentation.dto.response.VideoDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 영상 업로드 및 관리 API.
 */
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
  private final ProcessingProgressWebhookUseCase processingProgressWebhookUseCase;

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

  /**
   * 영상 업로드 완료 및 처리 시작.
   *
   * <p>클라이언트가 S3 업로드 완료 후 호출한다. 크레딧을 차감하고 AI 처리를 시작하며,
   * 처리 시작 알림을 전송한다.
   */
  @Operation(
	  summary = "영상 업로드 완료 및 처리 시작",
	  description =
		  "클라이언트가 S3 업로드 완료 후 호출합니다. "
			  + "크레딧을 차감하고 AI 처리를 시작하며, 처리 시작 알림을 전송합니다.")
  @PostMapping("/{videoId}/complete-upload")
  public ApiResponseDto<CompleteUploadResponse> completeUpload(
	  @PathVariable Long videoId,
	  @Valid @RequestBody CompleteUploadRequest request,
	  @CurrentMember Member member) {

	CompleteUploadResponse response =
		completeVideoUploadUseCase.execute(videoId, request, member);
	return ApiResponseDto.onSuccess(response);
  }

  /**
   * AI 처리 완료 웹훅.
   *
   * <p>AI 서버에서 처리 완료 시 호출하는 엔드포인트로, 처리 완료 알림을 SSE로 브로드캐스팅한다.
   */
  @Operation(
	  summary = "AI 처리 완료 웹훅",
	  description =
		  "AI 서버에서 처리 완료 시 호출하는 웹훅 엔드포인트입니다. "
			  + "처리 완료 알림을 SSE로 브로드캐스팅합니다.")
  @PostMapping("/webhook/processing-completed")
  public ApiResponseDto<Void> processingCompletedWebhook(
	  @Valid @RequestBody WebhookProcessingCompletedRequest request) {

	processingCompletedWebhookUseCase.execute(request);
	return ApiResponseDto.onSuccess(null);
  }

  /**
   * AI 처리 실패 웹훅.
   *
   * <p>AI 서버에서 처리 실패 시 호출하는 엔드포인트로, 실패 알림을 전송하고 크레딧을 환불한다.
   */
  @Operation(
	  summary = "AI 처리 실패 웹훅",
	  description =
		  "AI 서버에서 처리 실패 시 호출하는 웹훅 엔드포인트입니다. "
			  + "처리 실패 알림을 전송하고 크레딧을 환불합니다.")
  @PostMapping("/webhook/processing-failed")
  public ApiResponseDto<Void> processingFailedWebhook(
	  @Valid @RequestBody WebhookProcessingFailedRequest request) {

	processingFailedWebhookUseCase.execute(request);
	return ApiResponseDto.onSuccess(null);
  }

  /**
   * AI 처리 진행률 웹훅.
   *
   * <p>AI 서버에서 처리 진행률을 전송하는 엔드포인트로, 진행률을 로깅하고 향후 SSE로 전송할 수 있다.
   */
  @Operation(
	  summary = "AI 처리 진행률 웹훅",
	  description =
		  "AI 서버에서 처리 진행률을 전송하는 웹훅 엔드포인트입니다. "
			  + "진행률을 로깅하며, 향후 SSE를 통한 실시간 전송을 지원할 수 있습니다.")
  @PostMapping("/webhook/processing-progress")
  public ApiResponseDto<Void> processingProgressWebhook(
	  @Valid @RequestBody WebhookProcessingProgressRequest request) {

	processingProgressWebhookUseCase.execute(request);
	return ApiResponseDto.onSuccess(null);
  }

}
