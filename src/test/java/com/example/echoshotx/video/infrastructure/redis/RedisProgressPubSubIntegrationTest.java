package com.example.echoshotx.video.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.echoshotx.notification.application.service.NotificationService;
import com.example.echoshotx.video.application.adaptor.VideoAdaptor;
import com.example.echoshotx.video.application.service.VideoService;
import com.example.echoshotx.video.domain.entity.ProcessingType;
import com.example.echoshotx.video.domain.entity.Video;
import com.example.echoshotx.video.domain.entity.VideoStatus;
import com.example.echoshotx.video.domain.vo.VideoFile;
import com.example.echoshotx.video.infrastructure.redis.dto.VideoProgressMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Redis Pub/Sub 통합 테스트.
 * Testcontainers로 실제 Redis를 띄워서 Pub/Sub이 제대로 작동하는지 검증합니다.
 */
@Testcontainers
@SpringBootTest
@DisplayName("Redis Pub/Sub 통합 테스트")
class RedisProgressPubSubIntegrationTest {

  @Container
  static GenericContainer<?> redisContainer =
      new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
          .withExposedPorts(6379)
          .withReuse(true);

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", redisContainer::getHost);
    registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
  }

  @Autowired private RedisTemplate<String, String> redisTemplate;

  @Autowired private ChannelTopic videoProgressTopic;

  @Autowired private ObjectMapper objectMapper;

  @SpyBean private VideoProgressRedisListener videoProgressRedisListener;

  @SpyBean private VideoService videoService;

  @MockBean private VideoAdaptor videoAdaptor;

  @MockBean private NotificationService notificationService;

  private Video testVideo;

  @BeforeEach
  void setUp() {
    // 테스트용 Video 엔티티 생성
    testVideo =
        Video.builder()
            .id(123L)
            .memberId(1L)
            .originalFile(
                VideoFile.builder()
                    .fileName("test-video.mp4")
                    .fileSizeBytes(1000000L)
                    .s3Key("uploads/test-video.mp4")
                    .build())
            .status(VideoStatus.PROCESSING)
            .processingType(ProcessingType.UPSCALE_4K)
            .aiJobId("ai-job-123")
            .processingStartedAt(LocalDateTime.now())
            .processingProgressPercentage(0)
            .build();
  }

  @Test
  @DisplayName("Redis Pub/Sub: 진행률 메시지 발행 시 리스너가 수신하고 처리한다")
  void testRedisProgressMessagePublishAndSubscribe() throws Exception {
    // Given: 진행률 메시지 생성
    VideoProgressMessage message =
        VideoProgressMessage.builder()
            .videoId(123L)
            .aiJobId("ai-job-123")
            .progressPercentage(50)
            .estimatedTimeLeftSeconds(120)
            .currentStep("AI 처리 중")
            .timestamp(LocalDateTime.now().toString())
            .build();

    String messageJson = objectMapper.writeValueAsString(message);

    // VideoAdaptor Mock 설정
    org.mockito.Mockito.when(videoAdaptor.queryById(123L)).thenReturn(testVideo);

    // When: Redis 채널에 메시지 발행
    redisTemplate.convertAndSend(videoProgressTopic.getTopic(), messageJson);

    // Then: 리스너가 메시지를 수신하고 처리 (비동기이므로 대기)
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              // VideoProgressRedisListener.onMessage() 호출 확인
              verify(videoProgressRedisListener, times(1)).onMessage(any(), any());

              // VideoService.updateProcessingProgress() 호출 확인
              verify(videoService, times(1))
                  .updateProcessingProgress(
                      eq(testVideo), eq(50), eq(120), eq("AI 처리 중"));
            });

    // Video 엔티티의 진행률이 업데이트되었는지 확인
    assertThat(testVideo.getProcessingProgressPercentage()).isEqualTo(50);
    assertThat(testVideo.getEstimatedTimeLeftSeconds()).isEqualTo(120);
    assertThat(testVideo.getCurrentProcessingStep()).isEqualTo("AI 처리 중");
  }

  @Test
  @DisplayName("Redis Pub/Sub: 여러 진행률 메시지를 순차적으로 처리한다")
  void testMultipleProgressMessages() throws Exception {
    // Given: VideoAdaptor Mock 설정
    org.mockito.Mockito.when(videoAdaptor.queryById(123L)).thenReturn(testVideo);

    // When: 여러 진행률 메시지를 순차적으로 발행
    publishProgressMessage(123L, 10, "영상 분석 중");
    publishProgressMessage(123L, 30, "AI 처리 중");
    publishProgressMessage(123L, 60, "AI 처리 중");
    publishProgressMessage(123L, 90, "인코딩 중");

    // Then: 모든 메시지가 처리될 때까지 대기
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              // 4개의 메시지가 모두 처리되었는지 확인
              verify(videoProgressRedisListener, times(4)).onMessage(any(), any());
              verify(videoService, times(4))
                  .updateProcessingProgress(eq(testVideo), anyInt(), any(), anyString());
            });

    // 마지막 진행률이 반영되었는지 확인
    assertThat(testVideo.getProcessingProgressPercentage()).isEqualTo(90);
    assertThat(testVideo.getCurrentProcessingStep()).isEqualTo("인코딩 중");
  }

  @Test
  @DisplayName("Redis Pub/Sub: 잘못된 JSON 메시지는 무시하고 계속 진행한다")
  void testInvalidJsonMessageDoesNotStopProcessing() throws Exception {
    // Given: VideoAdaptor Mock 설정
    org.mockito.Mockito.when(videoAdaptor.queryById(123L)).thenReturn(testVideo);

    // When: 잘못된 JSON 메시지 발행
    redisTemplate.convertAndSend(videoProgressTopic.getTopic(), "{ invalid json }");

    // 정상 메시지 발행
    publishProgressMessage(123L, 50, "AI 처리 중");

    // Then: 정상 메시지는 처리되어야 함
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              // 잘못된 메시지와 정상 메시지 모두 onMessage()가 호출됨
              verify(videoProgressRedisListener, times(2)).onMessage(any(), any());

              // 정상 메시지만 VideoService로 전달됨
              verify(videoService, times(1))
                  .updateProcessingProgress(eq(testVideo), eq(50), any(), eq("AI 처리 중"));
            });
  }

  @Test
  @DisplayName("Redis Pub/Sub: 존재하지 않는 비디오 ID는 에러 로그를 남기고 계속 진행한다")
  void testNonExistentVideoIdContinuesProcessing() throws Exception {
    // Given: 존재하지 않는 비디오 ID에 대해 예외 발생
    org.mockito.Mockito.when(videoAdaptor.queryById(999L))
        .thenThrow(new RuntimeException("Video not found"));

    org.mockito.Mockito.when(videoAdaptor.queryById(123L)).thenReturn(testVideo);

    // When: 존재하지 않는 비디오 메시지 발행
    publishProgressMessage(999L, 50, "AI 처리 중");

    // 정상 비디오 메시지 발행
    publishProgressMessage(123L, 50, "AI 처리 중");

    // Then: 정상 메시지는 처리되어야 함
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              // 2개 메시지 모두 리스너가 수신
              verify(videoProgressRedisListener, times(2)).onMessage(any(), any());

              // 정상 비디오만 처리됨
              verify(videoService, times(1))
                  .updateProcessingProgress(eq(testVideo), eq(50), any(), eq("AI 처리 중"));
            });
  }

  @Test
  @DisplayName("Redis Pub/Sub: SSE로 진행률이 전송된다")
  void testProgressSentViaSSE() throws Exception {
    // Given: VideoAdaptor Mock 설정
    org.mockito.Mockito.when(videoAdaptor.queryById(123L)).thenReturn(testVideo);

    // When: 진행률 메시지 발행
    publishProgressMessage(123L, 75, "인코딩 중");

    // Then: NotificationService.sendProgressUpdate() 호출 확인
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              verify(notificationService, times(1))
                  .sendProgressUpdate(eq(1L), eq(123L), eq(75), any(), eq("인코딩 중"));
            });
  }

  // === Helper Methods ===

  /** Redis 채널에 진행률 메시지를 발행하는 헬퍼 메서드 */
  private void publishProgressMessage(Long videoId, Integer progress, String currentStep)
      throws Exception {
    VideoProgressMessage message =
        VideoProgressMessage.builder()
            .videoId(videoId)
            .progressPercentage(progress)
            .estimatedTimeLeftSeconds((100 - progress) * 3)
            .currentStep(currentStep)
            .timestamp(LocalDateTime.now().toString())
            .build();

    String messageJson = objectMapper.writeValueAsString(message);
    redisTemplate.convertAndSend(videoProgressTopic.getTopic(), messageJson);

    // 메시지 처리를 위한 짧은 대기
    Thread.sleep(100);
  }
}
