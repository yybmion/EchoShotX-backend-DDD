package com.example.echoshotx.video.infrastructure.ai;

import com.example.echoshotx.video.application.dto.ai.AiProcessingRequest;
import com.example.echoshotx.video.application.dto.ai.AiProcessingResponse;
import com.example.echoshotx.video.domain.entity.Video;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

/**
 * AI 서버와 통신하는 인프라 서비스.
 *
 * <p>영상 처리 요청을 AI 서버로 전송하고 응답을 받습니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiServerService {

  private final WebClient aiServerWebClient;

  @Value("${app.backend.base-url:http://localhost:8080}")
  private String backendBaseUrl;

  private static final String PROCESSING_ENDPOINT = "/api/v1/videos/process";
  private static final int MAX_RETRY_ATTEMPTS = 3;

  /**
   * AI 서버에 영상 처리 요청을 전송합니다.
   *
   * @param video 처리할 영상 엔티티
   * @return AI 서버 응답 (jobId 포함)
   */
  public AiProcessingResponse submitProcessingJob(Video video) {
    log.info(
        "Submitting video processing job to AI server: videoId={}, processingType={}",
        video.getId(),
        video.getProcessingType());

    // Webhook 콜백 URL 생성
    String callbackUrl = buildWebhookCallbackUrl();

    // 요청 DTO 생성
    AiProcessingRequest request = buildProcessingRequest(video, callbackUrl);

    try {
      // AI 서버로 동기 요청 전송 (Exponential Backoff 재시도)
      AiProcessingResponse response =
          aiServerWebClient
              .post()
              .uri(PROCESSING_ENDPOINT)
              .bodyValue(request)
              .retrieve()
              .bodyToMono(AiProcessingResponse.class)
              .retryWhen(
                  Retry.backoff(MAX_RETRY_ATTEMPTS, Duration.ofSeconds(2))
                      .filter(this::isRetryableException)
                      .doBeforeRetry(
                          retrySignal ->
                              log.warn(
                                  "Retrying AI server request: attempt={}, error={}",
                                  retrySignal.totalRetries() + 1,
                                  retrySignal.failure().getMessage())))
              .doOnError(
                  error ->
                      log.error(
                          "Failed to submit processing job to AI server: videoId={}, error={}",
                          video.getId(),
                          error.getMessage()))
              .block(); // 동기 처리

      log.info(
          "Successfully submitted processing job: videoId={}, jobId={}",
          video.getId(),
          response.getJobId());
      return response;

    } catch (Exception e) {
      log.error(
          "Error submitting processing job to AI server: videoId={}", video.getId(), e);
      throw new AiServerCommunicationException(
          "AI 서버와 통신 중 오류가 발생했습니다: " + e.getMessage(), e);
    }
  }

  /**
   * AI 처리 요청 DTO를 생성합니다.
   */
  private AiProcessingRequest buildProcessingRequest(Video video, String callbackUrl) {
    AiProcessingRequest.VideoMetadataDto metadataDto = null;

    if (video.getOriginalMetadata() != null) {
      metadataDto =
          AiProcessingRequest.VideoMetadataDto.builder()
              .durationSeconds(video.getOriginalMetadata().getDurationSeconds())
              .width(video.getOriginalMetadata().getWidth())
              .height(video.getOriginalMetadata().getHeight())
              .codec(video.getOriginalMetadata().getCodec())
              .bitrate(video.getOriginalMetadata().getBitrate())
              .frameRate(video.getOriginalMetadata().getFrameRate())
              .build();
    }

    return AiProcessingRequest.builder()
        .videoId(video.getId())
        .s3Key(video.getOriginalFile().getS3Key())
        .processingType(video.getProcessingType())
        .callbackUrl(callbackUrl)
        .metadata(metadataDto)
        .build();
  }

  /**
   * Webhook 콜백 URL을 생성합니다.
   *
   * <p>AI 서버가 처리 완료/실패/진행률을 전송할 엔드포인트 URL입니다.
   */
  private String buildWebhookCallbackUrl() {
    return backendBaseUrl + "/videos/webhook";
  }

  /**
   * 재시도 가능한 예외인지 확인합니다.
   *
   * <p>네트워크 오류, 5xx 서버 오류는 재시도하고, 4xx 클라이언트 오류는 재시도하지 않습니다.
   */
  private boolean isRetryableException(Throwable throwable) {
    if (throwable instanceof WebClientResponseException webClientException) {
      int statusCode = webClientException.getStatusCode().value();
      // 5xx 서버 오류는 재시도, 4xx 클라이언트 오류는 재시도하지 않음
      return statusCode >= 500;
    }
    // 네트워크 오류 등은 재시도
    return true;
  }

  /**
   * AI 서버 통신 실패 예외.
   */
  public static class AiServerCommunicationException extends RuntimeException {
    public AiServerCommunicationException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
