package com.example.echoshotx.video.application.usecase;

import com.example.echoshotx.credit.application.service.CreditService;
import com.example.echoshotx.shared.annotation.usecase.UseCase;
import com.example.echoshotx.video.application.adaptor.VideoAdaptor;
import com.example.echoshotx.video.application.service.VideoService;
import com.example.echoshotx.video.domain.entity.Video;
import com.example.echoshotx.video.presentation.dto.request.WebhookProcessingFailedRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 서버에서 처리 실패 시 호출하는 Webhook UseCase.
 *
 * <ul>
 *   <li>상태 변경 (QUEUED/PROCESSING → FAILED)</li>
 *   <li>처리 실패 알림 발송</li>
 *   <li>크레딧 환불</li>
 * </ul>
 */
@Slf4j
@UseCase
@Transactional
@RequiredArgsConstructor
public class ProcessingFailedWebhookUseCase {

  private final VideoAdaptor videoAdaptor;
  private final VideoService videoService;
  private final CreditService creditService;

  public void execute(WebhookProcessingFailedRequest request) {
	// 1. 비디오 조회
	Video video = videoAdaptor.queryById(request.getVideoId());
	log.warn(
		"Processing failed webhook received: videoId={}, aiJobId={}, requestId={}, currentStatus={}, error={}",
		request.getVideoId(),
		request.getAiJobId(),
		request.getRequestId(),
		video.getStatus(),
		request.getErrorMessage());

	// 2. 멱등성 체크: 이미 FAILED 상태인 경우 중복 처리 방지
	if (video.getStatus() == com.example.echoshotx.video.domain.entity.VideoStatus.FAILED) {
	  log.warn(
		  "Video already failed. Skipping duplicate webhook: videoId={}, aiJobId={}, requestId={}",
		  request.getVideoId(),
		  request.getAiJobId(),
		  request.getRequestId());
	  return;
	}

	// 3. 이미 COMPLETED 상태인 경우 실패 웹훅 무시 (완료된 작업은 실패 처리 불가)
	if (video.getStatus() == com.example.echoshotx.video.domain.entity.VideoStatus.COMPLETED) {
	  log.error(
		  "Video already completed. Cannot process failed webhook: videoId={}, aiJobId={}, requestId={}",
		  request.getVideoId(),
		  request.getAiJobId(),
		  request.getRequestId());
	  throw new IllegalStateException(
		  String.format("Cannot fail video %d as it is already completed", request.getVideoId()));
	}

	// 4. aiJobId 검증: 다른 작업의 웹훅인 경우 거부
	if (video.getAiJobId() != null && !video.getAiJobId().equals(request.getAiJobId())) {
	  log.error(
		  "AiJobId mismatch. Expected: {}, Received: {}. videoId={}",
		  video.getAiJobId(),
		  request.getAiJobId(),
		  request.getVideoId());
	  throw new IllegalArgumentException(
		  String.format(
			  "AiJobId mismatch for video %d. Expected: %s, Received: %s",
			  request.getVideoId(), video.getAiJobId(), request.getAiJobId()));
	}

	// 5. 사용된 크레딧 계산 (환불을 위해)
	int usedCredits = calculateUsedCredits(video);

	// 6. 처리 실패 및 알림 발행 (QUEUED/PROCESSING → FAILED)
	String errorMessage =
		String.format(
			"[%s] %s",
			request.getErrorCode() != null ? request.getErrorCode() : "UNKNOWN",
			request.getErrorMessage());
	videoService.failProcessing(video, errorMessage);
	log.info(
		"Video processing failed: videoId={}, aiJobId={}, requestId={}, retryCount={}",
		request.getVideoId(),
		request.getAiJobId(),
		request.getRequestId(),
		video.getRetryCount());

	// 7. 크레딧 환불
	if (usedCredits > 0) {
	  creditService.refundCredits(
		  video.getMemberId(), video.getId(), usedCredits, "영상 처리 실패로 인한 크레딧 환불");
	  log.info(
		  "Credits refunded: videoId={}, amount={}, aiJobId={}, requestId={}",
		  request.getVideoId(),
		  usedCredits,
		  request.getAiJobId(),
		  request.getRequestId());
	}
  }

  /**
   * 사용된 크레딧 계산.
   *
   * <p>TODO: 실제 CreditHistory에서 조회하거나, Video에서 계산.
   */
  private int calculateUsedCredits(Video video) {
	if (video.getOriginalMetadata() == null) {
	  return 0;
	}
	double costPerSecond = video.getProcessingType().getCreditCostPerSecond();
	double duration = video.getOriginalMetadata().getDurationSeconds();
	return (int) Math.ceil(costPerSecond * duration);
  }
}
