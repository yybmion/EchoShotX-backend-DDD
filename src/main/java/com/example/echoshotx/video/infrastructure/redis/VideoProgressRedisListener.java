package com.example.echoshotx.video.infrastructure.redis;

import com.example.echoshotx.video.application.adaptor.VideoAdaptor;
import com.example.echoshotx.video.application.service.VideoService;
import com.example.echoshotx.video.domain.entity.Video;
import com.example.echoshotx.video.infrastructure.redis.dto.VideoProgressMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Redis Pub/Sub 메시지를 수신하는 리스너.
 * AI 서버가 Redis에 발행한 진행률 메시지를 받아서 처리합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoProgressRedisListener implements MessageListener {

    private final VideoAdaptor videoAdaptor;
    private final VideoService videoService;
    private final ObjectMapper objectMapper;

    /**
     * Redis Pub/Sub 메시지 수신 시 호출되는 메서드.
     *
     * @param message Redis 메시지
     * @param pattern 구독 패턴 (현재는 단일 채널만 사용)
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String messageBody = new String(message.getBody());
            log.debug("Received Redis message: {}", messageBody);

            // JSON 메시지 파싱
            VideoProgressMessage progressMessage =
                    objectMapper.readValue(messageBody, VideoProgressMessage.class);

            log.info(
                    "Processing video progress update: videoId={}, progress={}%, step={}",
                    progressMessage.getVideoId(),
                    progressMessage.getProgressPercentage(),
                    progressMessage.getCurrentStep());

            // 비디오 조회
            Video video = videoAdaptor.queryById(progressMessage.getVideoId());

            // 진행률 업데이트 및 이벤트 발행
            videoService.updateProcessingProgress(
                    video,
                    progressMessage.getProgressPercentage(),
                    progressMessage.getEstimatedTimeLeftSeconds(),
                    progressMessage.getCurrentStep());

        } catch (Exception e) {
            log.error("Error processing Redis message: {}", e.getMessage(), e);
            // Redis 메시지 처리 실패 시 메시지를 버립니다 (재시도 없음)
            // 다음 진행률 업데이트가 올 것이므로 문제 없음
        }
    }
}
