package com.example.echoshotx.notification.application.event;

import com.example.echoshotx.notification.application.service.NotificationService;
import com.example.echoshotx.notification.domain.entity.Notification;
import com.example.echoshotx.notification.domain.entity.NotificationStatus;
import com.example.echoshotx.notification.domain.entity.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * VideoNotificationEventListener 단위 테스트
 *
 * 테스트 범위:
 * 1. 영상 처리 시작 이벤트 수신 및 알림 생성
 * 2. 영상 처리 완료 이벤트 수신 및 알림 생성
 * 3. 영상 처리 실패 이벤트 수신 및 알림 생성
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VideoNotificationEventListener 테스트")
class VideoNotificationEventListenerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private VideoNotificationEventListener eventListener;

    private Long testMemberId;
    private Long testVideoId;
    private String testFileName;

    @BeforeEach
    void setUp() {
        testMemberId = 1L;
        testVideoId = 100L;
        testFileName = "test_video.mp4";
    }

    @Nested
    @DisplayName("영상 처리 시작 이벤트 처리 테스트")
    class HandleVideoProcessingStartedTest {

        @Test
        @DisplayName("성공: VideoProcessingStartedEvent 수신 시 알림 생성")
        void handleVideoProcessingStarted_CreatesNotification() {
            // Given
            String processingType = "ECHO_REMOVAL";
            VideoProcessingStartedEvent event = new VideoProcessingStartedEvent(
                    testVideoId,
                    testMemberId,
                    testFileName,
                    processingType
            );

            Notification mockNotification = Notification.builder()
                    .id(1L)
                    .memberId(testMemberId)
                    .videoId(testVideoId)
                    .type(NotificationType.VIDEO_PROCESSING_STARTED)
                    .title("영상 처리 시작")
                    .content(String.format("'%s' 영상 처리가 시작되었습니다. 처리 타입: %s",
                            testFileName, processingType))
                    .isRead(false)
                    .status(NotificationStatus.PENDING)
                    .retryCount(0)
                    .build();

            given(notificationService.createAndSendVideoNotification(
                    anyLong(), anyLong(), any(NotificationType.class), anyString(), anyString()
            )).willReturn(mockNotification);

            // When
            eventListener.handleVideoProcessingStarted(event);

            // Then
            verify(notificationService).createAndSendVideoNotification(
                    eq(testMemberId),
                    eq(testVideoId),
                    eq(NotificationType.VIDEO_PROCESSING_STARTED),
                    eq("영상 처리 시작"),
                    eq(String.format("'%s' 영상 처리가 시작되었습니다. 처리 타입: %s",
                            testFileName, processingType))
            );
        }

        @Test
        @DisplayName("성공: 이벤트의 모든 정보가 알림 생성에 전달됨")
        void handleVideoProcessingStarted_PassesAllEventData() {
            // Given
            String processingType = "NOISE_REDUCTION";
            VideoProcessingStartedEvent event = new VideoProcessingStartedEvent(
                    testVideoId,
                    testMemberId,
                    testFileName,
                    processingType
            );

            ArgumentCaptor<Long> memberIdCaptor = ArgumentCaptor.forClass(Long.class);
            ArgumentCaptor<Long> videoIdCaptor = ArgumentCaptor.forClass(Long.class);
            ArgumentCaptor<NotificationType> typeCaptor = ArgumentCaptor.forClass(NotificationType.class);
            ArgumentCaptor<String> titleCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);

            // When
            eventListener.handleVideoProcessingStarted(event);

            // Then
            verify(notificationService).createAndSendVideoNotification(
                    memberIdCaptor.capture(),
                    videoIdCaptor.capture(),
                    typeCaptor.capture(),
                    titleCaptor.capture(),
                    contentCaptor.capture()
            );

            assertThat(memberIdCaptor.getValue()).isEqualTo(testMemberId);
            assertThat(videoIdCaptor.getValue()).isEqualTo(testVideoId);
            assertThat(typeCaptor.getValue()).isEqualTo(NotificationType.VIDEO_PROCESSING_STARTED);
            assertThat(titleCaptor.getValue()).isEqualTo("영상 처리 시작");
            assertThat(contentCaptor.getValue()).contains(testFileName, processingType);
        }
    }

    @Nested
    @DisplayName("영상 처리 완료 이벤트 처리 테스트")
    class HandleVideoProcessingCompletedTest {

        @Test
        @DisplayName("성공: VideoProcessingCompletedEvent 수신 시 알림 생성")
        void handleVideoProcessingCompleted_CreatesNotification() {
            // Given
            VideoProcessingCompletedEvent event = new VideoProcessingCompletedEvent(
                    testVideoId,
                    testMemberId,
                    testFileName
            );

            Notification mockNotification = Notification.builder()
                    .id(1L)
                    .memberId(testMemberId)
                    .videoId(testVideoId)
                    .type(NotificationType.VIDEO_PROCESSING_COMPLETED)
                    .title("영상 처리 완료")
                    .content(String.format("'%s' 영상 처리가 완료되었습니다. 다운로드 가능합니다.",
                            testFileName))
                    .isRead(false)
                    .status(NotificationStatus.SENT)
                    .retryCount(0)
                    .build();

            given(notificationService.createAndSendVideoNotification(
                    anyLong(), anyLong(), any(NotificationType.class), anyString(), anyString()
            )).willReturn(mockNotification);

            // When
            eventListener.handleVideoProcessingCompleted(event);

            // Then
            verify(notificationService).createAndSendVideoNotification(
                    eq(testMemberId),
                    eq(testVideoId),
                    eq(NotificationType.VIDEO_PROCESSING_COMPLETED),
                    eq("영상 처리 완료"),
                    eq(String.format("'%s' 영상 처리가 완료되었습니다. 다운로드 가능합니다.",
                            testFileName))
            );
        }

        @Test
        @DisplayName("성공: 완료 알림 내용에 파일명이 포함됨")
        void handleVideoProcessingCompleted_IncludesFileName() {
            // Given
            VideoProcessingCompletedEvent event = new VideoProcessingCompletedEvent(
                    testVideoId,
                    testMemberId,
                    testFileName
            );

            ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);

            // When
            eventListener.handleVideoProcessingCompleted(event);

            // Then
            verify(notificationService).createAndSendVideoNotification(
                    anyLong(),
                    anyLong(),
                    any(NotificationType.class),
                    anyString(),
                    contentCaptor.capture()
            );

            assertThat(contentCaptor.getValue()).contains(testFileName);
            assertThat(contentCaptor.getValue()).contains("완료");
            assertThat(contentCaptor.getValue()).contains("다운로드");
        }
    }

    @Nested
    @DisplayName("영상 처리 실패 이벤트 처리 테스트")
    class HandleVideoProcessingFailedTest {

        @Test
        @DisplayName("성공: VideoProcessingFailedEvent 수신 시 알림 생성")
        void handleVideoProcessingFailed_CreatesNotification() {
            // Given
            String errorReason = "파일 형식이 지원되지 않습니다";
            VideoProcessingFailedEvent event = new VideoProcessingFailedEvent(
                    testVideoId,
                    testMemberId,
                    testFileName,
                    errorReason
            );

            Notification mockNotification = Notification.builder()
                    .id(1L)
                    .memberId(testMemberId)
                    .videoId(testVideoId)
                    .type(NotificationType.VIDEO_PROCESSING_FAILED)
                    .title("영상 처리 실패")
                    .content(String.format("'%s' 영상 처리가 실패했습니다. 사유: %s",
                            testFileName, errorReason))
                    .isRead(false)
                    .status(NotificationStatus.SENT)
                    .retryCount(0)
                    .build();

            given(notificationService.createAndSendVideoNotification(
                    anyLong(), anyLong(), any(NotificationType.class), anyString(), anyString()
            )).willReturn(mockNotification);

            // When
            eventListener.handleVideoProcessingFailed(event);

            // Then
            verify(notificationService).createAndSendVideoNotification(
                    eq(testMemberId),
                    eq(testVideoId),
                    eq(NotificationType.VIDEO_PROCESSING_FAILED),
                    eq("영상 처리 실패"),
                    eq(String.format("'%s' 영상 처리가 실패했습니다. 사유: %s",
                            testFileName, errorReason))
            );
        }

        @Test
        @DisplayName("성공: 실패 알림 내용에 에러 사유가 포함됨")
        void handleVideoProcessingFailed_IncludesErrorReason() {
            // Given
            String errorReason = "처리 시간 초과";
            VideoProcessingFailedEvent event = new VideoProcessingFailedEvent(
                    testVideoId,
                    testMemberId,
                    testFileName,
                    errorReason
            );

            ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);

            // When
            eventListener.handleVideoProcessingFailed(event);

            // Then
            verify(notificationService).createAndSendVideoNotification(
                    anyLong(),
                    anyLong(),
                    any(NotificationType.class),
                    anyString(),
                    contentCaptor.capture()
            );

            assertThat(contentCaptor.getValue()).contains(testFileName);
            assertThat(contentCaptor.getValue()).contains(errorReason);
            assertThat(contentCaptor.getValue()).contains("실패");
        }

        @Test
        @DisplayName("성공: 다양한 실패 사유 처리")
        void handleVideoProcessingFailed_HandlesVariousReasons() {
            // Given
            String[] errorReasons = {
                    "메모리 부족",
                    "네트워크 오류",
                    "파일 손상",
                    "허용되지 않은 코덱"
            };

            for (String reason : errorReasons) {
                VideoProcessingFailedEvent event = new VideoProcessingFailedEvent(
                        testVideoId,
                        testMemberId,
                        testFileName,
                        reason
                );

                // When
                eventListener.handleVideoProcessingFailed(event);

                // Then
                verify(notificationService).createAndSendVideoNotification(
                        eq(testMemberId),
                        eq(testVideoId),
                        eq(NotificationType.VIDEO_PROCESSING_FAILED),
                        eq("영상 처리 실패"),
                        contains(reason)
                );
            }

            // 총 4번 호출되었는지 확인
            verify(notificationService, times(4)).createAndSendVideoNotification(
                    anyLong(), anyLong(), any(NotificationType.class), anyString(), anyString()
            );
        }
    }
}
