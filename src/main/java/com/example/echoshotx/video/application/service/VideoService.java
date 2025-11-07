package com.example.echoshotx.video.application.service;

import com.example.echoshotx.notification.application.event.VideoProcessingCompletedEvent;
import com.example.echoshotx.notification.application.event.VideoProcessingFailedEvent;
import com.example.echoshotx.notification.application.event.VideoProcessingStartedEvent;
import com.example.echoshotx.video.domain.entity.ProcessingType;
import com.example.echoshotx.video.domain.entity.Video;
import com.example.echoshotx.video.domain.vo.ProcessedVideo;
import com.example.echoshotx.video.domain.vo.VideoMetadata;
import com.example.echoshotx.video.infrastructure.persistence.VideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VideoService {

    private final VideoRepository videoRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Presigned URL 발급 시 Video 엔티티 생성
     */
    @Transactional
    public Video uploadVideo(Long memberId, String fileName, long fileSize,
                             ProcessingType processingType, String uploadId, String s3Key, LocalDateTime expiresAt) {
        Video video = Video.createForPresignedUpload(
                memberId,
                fileName,
                fileSize,
                processingType,
                s3Key,
                uploadId,
                expiresAt
        );
        return videoRepository.save(video);
    }

    /**
     * 업로드 완료 처리
     */
    @Transactional
    public Video completeUpload(Video video, VideoMetadata metadata) {
        video.completeUpload(metadata);
        return videoRepository.save(video);
    }

    /**
     * AI 처리 대기열에 추가 및 처리 시작 알림 발행
     */
    @Transactional
    public Video enqueueForProcessing(Video video, String sqsMessageId) {
        video.enqueueForProcessing(sqsMessageId);
        video = videoRepository.save(video);

        // 처리 시작 알림 이벤트 발행
        eventPublisher.publishEvent(new VideoProcessingStartedEvent(
                video.getId(),
                video.getMemberId(),
                video.getOriginalFile().getFileName(),
                video.getProcessingType().name()
        ));
        log.info("Published VideoProcessingStartedEvent for video: {}", video.getId());

        return video;
    }

    /**
     * AI 처리 완료 및 알림 발행
     */
    @Transactional
    public Video completeProcessing(Video video, ProcessedVideo processedVideo, VideoMetadata processedMetadata) {
        video.completeProcessing(processedVideo, processedMetadata);
        video = videoRepository.save(video);

        // 처리 완료 알림 이벤트 발행
        eventPublisher.publishEvent(new VideoProcessingCompletedEvent(
                video.getId(),
                video.getMemberId(),
                video.getOriginalFile().getFileName()
        ));
        log.info("Published VideoProcessingCompletedEvent for video: {}", video.getId());

        return video;
    }

    /**
     * 처리 실패 및 알림 발행
     */
    @Transactional
    public Video failProcessing(Video video, String errorMessage) {
        video.failProcessing(errorMessage);
        video = videoRepository.save(video);

        // 처리 실패 알림 이벤트 발행
        eventPublisher.publishEvent(new VideoProcessingFailedEvent(
                video.getId(),
                video.getMemberId(),
                video.getOriginalFile().getFileName(),
                errorMessage
        ));
        log.info("Published VideoProcessingFailedEvent for video: {}", video.getId());

        return video;
    }

}
