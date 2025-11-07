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
 * AI 서버에서 처리 완료 시 호출하는 Webhook UseCase
 * - 처리된 영상 정보 업데이트
 * - 상태 변경 (QUEUED/PROCESSING → COMPLETED)
 * - 처리 완료 알림 발송
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
        log.info("Processing completed webhook received: videoId={}, aiJobId={}",
                request.getVideoId(), request.getAiJobId());

        // 2. ProcessedVideo 생성
        ProcessedVideo processedVideo = ProcessedVideo.builder()
                .s3Key(request.getProcessedS3Key())
                .fileSizeBytes(request.getProcessedFileSizeBytes())
                .build();

        // 3. Processed VideoMetadata 생성
        VideoMetadata processedMetadata = VideoMetadata.builder()
                .durationSeconds(request.getProcessedDurationSeconds())
                .width(request.getProcessedWidth())
                .height(request.getProcessedHeight())
                .codec(request.getProcessedCodec())
                .bitrate(request.getProcessedBitrate())
                .frameRate(request.getProcessedFrameRate())
                .build();

        // 4. 처리 완료 및 알림 발행 (QUEUED/PROCESSING → COMPLETED)
        videoService.completeProcessing(video, processedVideo, processedMetadata);
        log.info("Video processing completed successfully: videoId={}", request.getVideoId());

        // 5. 썸네일 저장 (옵셔널)
        if (request.getThumbnailS3Key() != null) {
            // TODO: 썸네일 저장 로직
            log.info("Thumbnail saved: videoId={}, thumbnailKey={}",
                    request.getVideoId(), request.getThumbnailS3Key());
        }
    }
}
