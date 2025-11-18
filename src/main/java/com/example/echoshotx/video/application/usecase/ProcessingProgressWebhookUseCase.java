package com.example.echoshotx.video.application.usecase;

import com.example.echoshotx.shared.annotation.usecase.UseCase;
import com.example.echoshotx.video.application.adaptor.VideoAdaptor;
import com.example.echoshotx.video.domain.entity.Video;
import com.example.echoshotx.video.presentation.dto.request.WebhookProcessingProgressRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 서버에서 처리 진행률을 전송할 때 호출하는 Webhook UseCase.
 *
 * <p>현재는 진행률을 로깅만 하며, 향후 SSE를 통한 실시간 진행률 전송을 추가할 수 있습니다.
 */
@Slf4j
@UseCase
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProcessingProgressWebhookUseCase {

  private final VideoAdaptor videoAdaptor;
  // private final SseConnectionManager sseConnectionManager; // TODO: SSE 진행률 전송 시 주입

  public void execute(WebhookProcessingProgressRequest request) {
    // 1. 비디오 조회 및 검증
    Video video = videoAdaptor.queryById(request.getVideoId());

    // 2. aiJobId 검증
    if (!request.getAiJobId().equals(video.getAiJobId())) {
      log.warn(
          "AI Job ID mismatch: videoId={}, expected={}, received={}",
          video.getId(),
          video.getAiJobId(),
          request.getAiJobId());
      return;
    }

    // 3. 진행률 로깅
    log.info(
        "Processing progress update: videoId={}, aiJobId={}, progress={}%, stage={}, estimatedTimeRemaining={}s",
        request.getVideoId(),
        request.getAiJobId(),
        request.getProgress(),
        request.getCurrentStage(),
        request.getEstimatedTimeRemainingSeconds());

    // 4. TODO: SSE를 통한 실시간 진행률 전송
    // sseConnectionManager.sendProgressUpdate(video.getMemberId(), request);
  }
}
