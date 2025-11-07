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
 * AI 서버에서 처리 실패 시 호출하는 Webhook UseCase
 * - 상태 변경 (QUEUED/PROCESSING → FAILED)
 * - 처리 실패 알림 발송
 * - 크레딧 환불
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
        log.warn("Processing failed webhook received: videoId={}, aiJobId={}, error={}",
                request.getVideoId(), request.getAiJobId(), request.getErrorMessage());

        // 2. 사용된 크레딧 계산 (환불을 위해)
        int usedCredits = calculateUsedCredits(video);

        // 3. 처리 실패 및 알림 발행 (QUEUED/PROCESSING → FAILED)
        String errorMessage = String.format("[%s] %s",
                request.getErrorCode() != null ? request.getErrorCode() : "UNKNOWN",
                request.getErrorMessage());
        videoService.failProcessing(video, errorMessage);
        log.info("Video processing failed: videoId={}, retryCount={}",
                request.getVideoId(), video.getRetryCount());

        // 4. 크레딧 환불
        if (usedCredits > 0) {
            creditService.refundCredits(
                    video.getMemberId(),
                    video.getId(),
                    usedCredits,
                    "영상 처리 실패로 인한 크레딧 환불"
            );
            log.info("Credits refunded: videoId={}, amount={}", request.getVideoId(), usedCredits);
        }
    }

    /**
     * 사용된 크레딧 계산
     * TODO: 실제 CreditHistory에서 조회하거나, Video에서 계산
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
