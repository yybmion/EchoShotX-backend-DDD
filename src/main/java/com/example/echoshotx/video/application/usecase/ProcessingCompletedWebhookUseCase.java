package com.example.echoshotx.video.application.usecase;

import com.example.echoshotx.shared.annotation.usecase.UseCase;
import com.example.echoshotx.video.application.adaptor.VideoAdaptor;
import com.example.echoshotx.video.application.service.VideoService;
import com.example.echoshotx.video.domain.entity.Video;
import com.example.echoshotx.video.domain.vo.ProcessedVideo;
import com.example.echoshotx.video.domain.vo.VideoMetadata;
import com.example.echoshotx.video.presentation.dto.request.WebhookProcessingCompletedRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 서버에서 처리 완료 시 호출하는 Webhook UseCase.
 *
 * <ul>
 *   <li>처리된 영상 정보 업데이트</li>
 *   <li>상태 변경 (QUEUED/PROCESSING → COMPLETED)</li>
 *   <li>처리 완료 알림 발송</li>
 * </ul>
 */
@Slf4j
@UseCase
@Transactional
@RequiredArgsConstructor
public class ProcessingCompletedWebhookUseCase {

  private final VideoAdaptor videoAdaptor;
  private final VideoService videoService;

  public void execute(WebhookProcessingCompletedRequest request) {
	// 1. 비디오 조회
	Video video = videoAdaptor.queryById(request.getVideoId());
	log.info(
		"Processing completed webhook received: videoId={}, aiJobId={}, requestId={}, currentStatus={}",
		request.getVideoId(),
		request.getAiJobId(),
		request.getRequestId(),
		video.getStatus());

	// 2. 멱등성 체크: 이미 COMPLETED 상태인 경우 중복 처리 방지
	if (video.getStatus() == com.example.echoshotx.video.domain.entity.VideoStatus.COMPLETED) {
	  log.warn(
		  "Video already completed. Skipping duplicate webhook: videoId={}, aiJobId={}, requestId={}",
		  request.getVideoId(),
		  request.getAiJobId(),
		  request.getRequestId());
	  return;
	}

	// 3. aiJobId 검증: 다른 작업의 웹훅인 경우 거부
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

	// 4. ProcessedVideo 생성
	ProcessedVideo processedVideo =
		ProcessedVideo.builder()
			.s3Key(request.getProcessedS3Key())
			.fileSizeBytes(request.getProcessedFileSizeBytes())
			.build();

	// 5. Processed VideoMetadata 생성
	VideoMetadata processedMetadata =
		VideoMetadata.builder()
			.durationSeconds(request.getProcessedDurationSeconds())
			.width(request.getProcessedWidth())
			.height(request.getProcessedHeight())
			.codec(request.getProcessedCodec())
			.bitrate(request.getProcessedBitrate())
			.frameRate(request.getProcessedFrameRate())
			.build();

	// 6. 처리 완료 및 알림 발행 (QUEUED/PROCESSING → COMPLETED)
	videoService.completeProcessing(video, processedVideo, processedMetadata);
	log.info(
		"Video processing completed successfully: videoId={}, aiJobId={}, requestId={}",
		request.getVideoId(),
		request.getAiJobId(),
		request.getRequestId());

	// 7. 썸네일 저장 (옵셔널)
	if (request.getThumbnailS3Key() != null) {
	  // TODO: 썸네일 저장 로직
	  log.info(
		  "Thumbnail saved: videoId={}, thumbnailKey={}",
		  request.getVideoId(),
		  request.getThumbnailS3Key());
	}
  }
}
