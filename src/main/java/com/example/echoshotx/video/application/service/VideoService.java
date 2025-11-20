package com.example.echoshotx.video.application.service;

import com.example.echoshotx.notification.application.event.VideoProcessingCompletedEvent;
import com.example.echoshotx.notification.application.event.VideoProcessingFailedEvent;
import com.example.echoshotx.notification.application.event.VideoProcessingProgressEvent;
import com.example.echoshotx.notification.application.event.VideoProcessingStartedEvent;
import com.example.echoshotx.video.domain.entity.ProcessingType;
import com.example.echoshotx.video.domain.entity.Video;
import com.example.echoshotx.video.domain.vo.ProcessedVideo;
import com.example.echoshotx.video.domain.vo.VideoMetadata;
import com.example.echoshotx.video.infrastructure.persistence.VideoRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비디오 업로드, 처리, 실패 등의 비즈니스 로직을 담당하는 서비스.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VideoService {

  private final VideoRepository videoRepository;
  private final ApplicationEventPublisher eventPublisher; // 이벤트 발행용 의존성

  /**
   * Presigned URL 발급 시 Video 엔티티를 생성한다.
   */
  @Transactional
  public Video uploadVideo(
	  Long memberId,
	  String fileName,
	  long fileSize,
	  ProcessingType processingType,
	  String uploadId,
	  String s3Key,
	  LocalDateTime expiresAt) {

	Video video =
		Video.createForPresignedUpload(
			memberId, fileName, fileSize, processingType, s3Key, uploadId, expiresAt);
	return videoRepository.save(video);
  }

  /**
   * 업로드 완료를 처리한다.
   */
  @Transactional
  public Video completeUpload(Video video, VideoMetadata metadata) {
	video.completeUpload(metadata);
	return videoRepository.save(video);
  }

  /**
   * AI 처리 대기열에 추가하고, 처리 시작 이벤트를 발행한다.
   */
  @Transactional
  public Video enqueueForProcessing(Video video, String sqsMessageId) {
	video.enqueueForProcessing(sqsMessageId);
	video = videoRepository.save(video);

	// 처리 시작 이벤트 발행
	eventPublisher.publishEvent(
		new VideoProcessingStartedEvent(
			video.getId(),
			video.getMemberId(),
			video.getOriginalFile().getFileName(),
			video.getProcessingType().name()));
	log.info("Published VideoProcessingStartedEvent for video: {}", video.getId());

	return video;
  }

  /**
   * AI 처리가 완료되면 엔티티를 갱신하고 완료 이벤트를 발행한다.
   */
  @Transactional
  public Video completeProcessing(
	  Video video, ProcessedVideo processedVideo, VideoMetadata processedMetadata) {
	video.completeProcessing(processedVideo, processedMetadata);
	video = videoRepository.save(video);

	// 처리 완료 이벤트 발행
	eventPublisher.publishEvent(
		new VideoProcessingCompletedEvent(
			video.getId(),
			video.getMemberId(),
			video.getOriginalFile().getFileName()));
	log.info("Published VideoProcessingCompletedEvent for video: {}", video.getId());

	return video;
  }

  /**
   * AI 처리 실패 시 엔티티를 갱신하고 실패 이벤트를 발행한다.
   */
  @Transactional
  public Video failProcessing(Video video, String errorMessage) {
	video.failProcessing(errorMessage);
	video = videoRepository.save(video);

	// 처리 실패 이벤트 발행
	eventPublisher.publishEvent(
		new VideoProcessingFailedEvent(
			video.getId(),
			video.getMemberId(),
			video.getOriginalFile().getFileName(),
			errorMessage));
	log.info("Published VideoProcessingFailedEvent for video: {}", video.getId());

	return video;
  }

  /**
   * AI 처리 진행률을 업데이트하고 진행률 이벤트를 발행한다.
   */
  @Transactional
  public Video updateProcessingProgress(
	  Video video, Integer progressPercentage, Integer estimatedTimeLeft, String currentStep) {

	video.updateProcessingProgress(progressPercentage, estimatedTimeLeft, currentStep);
	video = videoRepository.save(video);

	// 진행률 업데이트 이벤트 발행
	eventPublisher.publishEvent(
		new VideoProcessingProgressEvent(
			video.getId(),
			video.getMemberId(),
			progressPercentage,
			estimatedTimeLeft,
			currentStep));
	log.debug(
		"Published VideoProcessingProgressEvent for video: {}, progress: {}%",
		video.getId(),
		progressPercentage);

	return video;
  }
}
