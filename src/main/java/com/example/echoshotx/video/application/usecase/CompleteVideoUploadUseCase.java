package com.example.echoshotx.video.application.usecase;

import com.example.echoshotx.credit.application.service.CreditService;
import com.example.echoshotx.member.domain.entity.Member;
import com.example.echoshotx.shared.annotation.usecase.UseCase;
import com.example.echoshotx.video.application.adaptor.VideoAdaptor;
import com.example.echoshotx.video.application.service.VideoService;
import com.example.echoshotx.video.domain.entity.Video;
import com.example.echoshotx.video.domain.vo.VideoMetadata;
import com.example.echoshotx.video.presentation.dto.request.CompleteUploadRequest;
import com.example.echoshotx.video.presentation.dto.response.CompleteUploadResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

/**
 * 클라이언트가 S3 업로드 완료 후 호출하는 UseCase.
 *
 * <ul>
 *   <li>업로드 완료 처리</li>
 *   <li>크레딧 차감</li>
 *   <li>SQS 큐에 메시지 전송</li>
 *   <li>처리 시작 알림 발송</li>
 * </ul>
 */
@Slf4j
@UseCase
@Transactional
@RequiredArgsConstructor
public class CompleteVideoUploadUseCase {

  private final VideoAdaptor videoAdaptor;
  private final VideoService videoService;
  private final CreditService creditService;
  // private final SqsService sqsService; // TODO: SQS 서비스 구현 후 주입

  public CompleteUploadResponse execute(
	  Long videoId, CompleteUploadRequest request, Member member) {

	// 1. 비디오 조회 및 권한 검증
	Video video = videoAdaptor.queryById(videoId);
	video.validateMember(member);

	// 2. VideoMetadata 생성
	VideoMetadata metadata =
		VideoMetadata.builder()
			.durationSeconds(request.getDurationSeconds())
			.width(request.getWidth())
			.height(request.getHeight())
			.codec(request.getCodec())
			.bitrate(request.getBitrate())
			.frameRate(request.getFrameRate())
			.build();

	// 3. 업로드 완료 처리 (PENDING_UPLOAD → UPLOAD_COMPLETED)
	video = videoService.completeUpload(video, metadata);
	log.info("Video upload completed: videoId={}, memberId={}", videoId, member.getId());

	// 4. 크레딧 차감
	creditService.useCreditsForVideoProcessing(video, video.getProcessingType());
	log.info("Credits deducted for video processing: videoId={}", videoId);

	// 5. SQS에 메시지 전송 (TODO: 실제 SQS 서비스 구현)
	String sqsMessageId = sendToProcessingQueue(video);
	log.info("Video sent to processing queue: videoId={}, sqsMessageId={}", videoId, sqsMessageId);

	// 6. 처리 대기열에 추가 및 알림 발행 (UPLOAD_COMPLETED → QUEUED)
	video = videoService.enqueueForProcessing(video, sqsMessageId);

	return CompleteUploadResponse.from(video);
  }

  /**
   * SQS에 메시지 전송 (TODO: 실제 구현). 현재는 Mock으로 UUID 반환.
   */
  private String sendToProcessingQueue(Video video) {
	// TODO: SqsService를 통해 실제 SQS 메시지 전송
	// return sqsService.sendMessage(video);

	// Mock implementation
	String messageId = UUID.randomUUID().toString();
	log.info("Mock SQS message sent: videoId={}, messageId={}", video.getId(), messageId);
	return messageId;
  }
}
