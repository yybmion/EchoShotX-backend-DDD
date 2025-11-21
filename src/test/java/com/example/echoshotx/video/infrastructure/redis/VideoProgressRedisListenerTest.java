package com.example.echoshotx.video.infrastructure.redis;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.echoshotx.video.application.adaptor.VideoAdaptor;
import com.example.echoshotx.video.application.service.VideoService;
import com.example.echoshotx.video.domain.entity.ProcessingType;
import com.example.echoshotx.video.domain.entity.Video;
import com.example.echoshotx.video.domain.entity.VideoStatus;
import com.example.echoshotx.video.domain.vo.VideoFile;
import com.example.echoshotx.video.infrastructure.redis.dto.VideoProgressMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.Message;

/** VideoProgressRedisListener 단위 테스트 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VideoProgressRedisListener 단위 테스트")
class VideoProgressRedisListenerTest {

  @Mock private VideoAdaptor videoAdaptor;

  @Mock private VideoService videoService;

  @Mock private ObjectMapper objectMapper;

  @Mock private Message message;

  @InjectMocks private VideoProgressRedisListener listener;

  private Video testVideo;

  @BeforeEach
  void setUp() {
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
            .build();
  }

  @Test
  @DisplayName("정상 메시지 수신 시 VideoService.updateProcessingProgress()를 호출한다")
  void testOnMessageSuccess() throws Exception {
    // Given
    VideoProgressMessage progressMessage =
        VideoProgressMessage.builder()
            .videoId(123L)
            .aiJobId("ai-job-123")
            .progressPercentage(50)
            .estimatedTimeLeftSeconds(120)
            .currentStep("AI 처리 중")
            .timestamp(LocalDateTime.now().toString())
            .build();

    String messageBody =
        "{\"video_id\":123,\"ai_job_id\":\"ai-job-123\",\"progress_percentage\":50,\"estimated_time_left_seconds\":120,\"current_step\":\"AI 처리 중\"}";

    when(message.getBody()).thenReturn(messageBody.getBytes());
    when(objectMapper.readValue(messageBody, VideoProgressMessage.class))
        .thenReturn(progressMessage);
    when(videoAdaptor.queryById(123L)).thenReturn(testVideo);

    // When
    listener.onMessage(message, null);

    // Then
    verify(videoAdaptor, times(1)).queryById(123L);
    verify(videoService, times(1))
        .updateProcessingProgress(eq(testVideo), eq(50), eq(120), eq("AI 처리 중"));
  }

  @Test
  @DisplayName("잘못된 JSON 메시지 수신 시 에러 로그를 남기고 계속 진행한다")
  void testOnMessageInvalidJson() throws Exception {
    // Given
    String invalidJson = "{ invalid json }";
    when(message.getBody()).thenReturn(invalidJson.getBytes());
    when(objectMapper.readValue(invalidJson, VideoProgressMessage.class))
        .thenThrow(new RuntimeException("JSON parse error"));

    // When
    listener.onMessage(message, null);

    // Then: 예외가 발생해도 프로그램이 중단되지 않음
    verify(videoAdaptor, never()).queryById(any());
    verify(videoService, never()).updateProcessingProgress(any(), any(), any(), any());
  }

  @Test
  @DisplayName("존재하지 않는 비디오 ID 수신 시 에러 로그를 남기고 계속 진행한다")
  void testOnMessageVideoNotFound() throws Exception {
    // Given
    VideoProgressMessage progressMessage =
        VideoProgressMessage.builder()
            .videoId(999L)
            .progressPercentage(50)
            .currentStep("AI 처리 중")
            .build();

    String messageBody = "{\"video_id\":999,\"progress_percentage\":50}";
    when(message.getBody()).thenReturn(messageBody.getBytes());
    when(objectMapper.readValue(messageBody, VideoProgressMessage.class))
        .thenReturn(progressMessage);
    when(videoAdaptor.queryById(999L)).thenThrow(new RuntimeException("Video not found"));

    // When
    listener.onMessage(message, null);

    // Then: 예외가 발생해도 프로그램이 중단되지 않음
    verify(videoAdaptor, times(1)).queryById(999L);
    verify(videoService, never()).updateProcessingProgress(any(), any(), any(), any());
  }

  @Test
  @DisplayName("null 필드가 있는 메시지도 정상 처리한다")
  void testOnMessageWithNullFields() throws Exception {
    // Given: estimatedTimeLeft와 currentStep이 null인 메시지
    VideoProgressMessage progressMessage =
        VideoProgressMessage.builder()
            .videoId(123L)
            .progressPercentage(30)
            .estimatedTimeLeftSeconds(null)
            .currentStep(null)
            .build();

    String messageBody = "{\"video_id\":123,\"progress_percentage\":30}";
    when(message.getBody()).thenReturn(messageBody.getBytes());
    when(objectMapper.readValue(messageBody, VideoProgressMessage.class))
        .thenReturn(progressMessage);
    when(videoAdaptor.queryById(123L)).thenReturn(testVideo);

    // When
    listener.onMessage(message, null);

    // Then
    verify(videoAdaptor, times(1)).queryById(123L);
    verify(videoService, times(1)).updateProcessingProgress(eq(testVideo), eq(30), eq(null), eq(null));
  }

  @Test
  @DisplayName("진행률 0% 메시지를 정상 처리한다")
  void testOnMessageWithZeroProgress() throws Exception {
    // Given
    VideoProgressMessage progressMessage =
        VideoProgressMessage.builder()
            .videoId(123L)
            .progressPercentage(0)
            .estimatedTimeLeftSeconds(300)
            .currentStep("처리 시작")
            .build();

    String messageBody =
        "{\"video_id\":123,\"progress_percentage\":0,\"estimated_time_left_seconds\":300,\"current_step\":\"처리 시작\"}";
    when(message.getBody()).thenReturn(messageBody.getBytes());
    when(objectMapper.readValue(messageBody, VideoProgressMessage.class))
        .thenReturn(progressMessage);
    when(videoAdaptor.queryById(123L)).thenReturn(testVideo);

    // When
    listener.onMessage(message, null);

    // Then
    verify(videoService, times(1))
        .updateProcessingProgress(eq(testVideo), eq(0), eq(300), eq("처리 시작"));
  }

  @Test
  @DisplayName("진행률 100% 메시지를 정상 처리한다")
  void testOnMessageWith100Progress() throws Exception {
    // Given
    VideoProgressMessage progressMessage =
        VideoProgressMessage.builder()
            .videoId(123L)
            .progressPercentage(100)
            .estimatedTimeLeftSeconds(0)
            .currentStep("거의 완료")
            .build();

    String messageBody =
        "{\"video_id\":123,\"progress_percentage\":100,\"estimated_time_left_seconds\":0,\"current_step\":\"거의 완료\"}";
    when(message.getBody()).thenReturn(messageBody.getBytes());
    when(objectMapper.readValue(messageBody, VideoProgressMessage.class))
        .thenReturn(progressMessage);
    when(videoAdaptor.queryById(123L)).thenReturn(testVideo);

    // When
    listener.onMessage(message, null);

    // Then
    verify(videoService, times(1))
        .updateProcessingProgress(eq(testVideo), eq(100), eq(0), eq("거의 완료"));
  }
}
