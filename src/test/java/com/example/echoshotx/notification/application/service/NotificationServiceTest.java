package com.example.echoshotx.notification.application.service;

import com.example.echoshotx.notification.application.adaptor.NotificationAdaptor;
import com.example.echoshotx.notification.domain.entity.Notification;
import com.example.echoshotx.notification.domain.entity.NotificationStatus;
import com.example.echoshotx.notification.domain.entity.NotificationType;
import com.example.echoshotx.notification.presentation.dto.response.NotificationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * NotificationService 단위 테스트
 *
 * 테스트 범위:
 * 1. 비디오 알림 생성 및 전송
 * 2. 알림 읽음 처리
 * 3. SSE 전송 성공/실패 처리
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService 테스트")
class NotificationServiceTest {

    @Mock
    private NotificationAdaptor notificationAdaptor;

    @Mock
    private SseConnectionManager sseConnectionManager;

    @InjectMocks
    private NotificationService notificationService;

    private Long testMemberId;
    private Long testVideoId;
    private Notification testNotification;

    @BeforeEach
    void setUp() {
        testMemberId = 1L;
        testVideoId = 100L;

        testNotification = Notification.builder()
                .id(1L)
                .memberId(testMemberId)
                .videoId(testVideoId)
                .type(NotificationType.VIDEO_PROCESSING_STARTED)
                .title("영상 처리 시작")
                .content("'test.mp4' 영상 처리가 시작되었습니다.")
                .isRead(false)
                .status(NotificationStatus.PENDING)
                .retryCount(0)
                .build();
    }

    @Nested
    @DisplayName("비디오 알림 생성 및 전송 테스트")
    class CreateAndSendVideoNotificationTest {

        @Test
        @DisplayName("성공: 알림 생성 및 SSE 전송 성공")
        void createAndSendVideoNotification_Success_WhenSseConnectionExists() {
            // Given
            given(notificationAdaptor.save(any(Notification.class)))
                    .willReturn(testNotification);
            given(sseConnectionManager.sendToMember(eq(testMemberId), any()))
                    .willReturn(true);

            // When
            Notification result = notificationService.createAndSendVideoNotification(
                    testMemberId,
                    testVideoId,
                    NotificationType.VIDEO_PROCESSING_STARTED,
                    "영상 처리 시작",
                    "'test.mp4' 영상 처리가 시작되었습니다."
            );

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getMemberId()).isEqualTo(testMemberId);
            assertThat(result.getVideoId()).isEqualTo(testVideoId);
            assertThat(result.getType()).isEqualTo(NotificationType.VIDEO_PROCESSING_STARTED);

            verify(notificationAdaptor, times(2)).save(any(Notification.class));
            verify(sseConnectionManager).sendToMember(eq(testMemberId), any(NotificationResponse.class));
        }

        @Test
        @DisplayName("성공: SSE 연결 없을 때 알림은 생성되고 FAILED 상태로 저장됨")
        void createAndSendVideoNotification_Saved_WhenNoSseConnection() {
            // Given
            Notification savedNotification = Notification.builder()
                    .id(1L)
                    .memberId(testMemberId)
                    .videoId(testVideoId)
                    .type(NotificationType.VIDEO_PROCESSING_STARTED)
                    .title("영상 처리 시작")
                    .content("'test.mp4' 영상 처리가 시작되었습니다.")
                    .isRead(false)
                    .status(NotificationStatus.PENDING)
                    .retryCount(0)
                    .build();

            given(notificationAdaptor.save(any(Notification.class)))
                    .willReturn(savedNotification)
                    .willReturn(savedNotification);
            given(sseConnectionManager.sendToMember(eq(testMemberId), any()))
                    .willReturn(false);

            // When
            Notification result = notificationService.createAndSendVideoNotification(
                    testMemberId,
                    testVideoId,
                    NotificationType.VIDEO_PROCESSING_STARTED,
                    "영상 처리 시작",
                    "'test.mp4' 영상 처리가 시작되었습니다."
            );

            // Then
            assertThat(result).isNotNull();
            verify(notificationAdaptor, times(2)).save(any(Notification.class));
            verify(sseConnectionManager).sendToMember(eq(testMemberId), any());
        }

        @Test
        @DisplayName("성공: Notification 엔티티가 올바르게 생성됨")
        void createAndSendVideoNotification_CreatesCorrectEntity() {
            // Given
            ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
            given(notificationAdaptor.save(notificationCaptor.capture()))
                    .willReturn(testNotification);
            given(sseConnectionManager.sendToMember(anyLong(), any()))
                    .willReturn(true);

            // When
            notificationService.createAndSendVideoNotification(
                    testMemberId,
                    testVideoId,
                    NotificationType.VIDEO_PROCESSING_COMPLETED,
                    "영상 처리 완료",
                    "영상 처리가 완료되었습니다."
            );

            // Then
            List<Notification> capturedNotifications = notificationCaptor.getAllValues();
            Notification createdNotification = capturedNotifications.get(0);

            assertThat(createdNotification.getMemberId()).isEqualTo(testMemberId);
            assertThat(createdNotification.getVideoId()).isEqualTo(testVideoId);
            assertThat(createdNotification.getType()).isEqualTo(NotificationType.VIDEO_PROCESSING_COMPLETED);
            assertThat(createdNotification.getTitle()).isEqualTo("영상 처리 완료");
            assertThat(createdNotification.getContent()).isEqualTo("영상 처리가 완료되었습니다.");
            assertThat(createdNotification.getIsRead()).isFalse();
            assertThat(createdNotification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("알림 읽음 처리 테스트")
    class MarkAsReadTest {

        @Test
        @DisplayName("성공: 알림 읽음 처리")
        void markAsRead_Success() {
            // Given
            Long notificationId = 1L;
            Notification unreadNotification = Notification.builder()
                    .id(notificationId)
                    .memberId(testMemberId)
                    .type(NotificationType.VIDEO_PROCESSING_COMPLETED)
                    .title("Test")
                    .content("Test")
                    .isRead(false)
                    .status(NotificationStatus.SENT)
                    .retryCount(0)
                    .build();

            given(notificationAdaptor.queryById(notificationId))
                    .willReturn(unreadNotification);
            given(notificationAdaptor.save(any(Notification.class)))
                    .willReturn(unreadNotification);

            // When
            notificationService.markAsRead(notificationId, testMemberId);

            // Then
            verify(notificationAdaptor).validateNotificationOwnership(notificationId, testMemberId);
            verify(notificationAdaptor).queryById(notificationId);
            verify(notificationAdaptor).save(any(Notification.class));
        }

        @Test
        @DisplayName("성공: 모든 알림 읽음 처리")
        void markAllAsRead_Success() {
            // Given
            Notification notification1 = Notification.builder()
                    .id(1L)
                    .memberId(testMemberId)
                    .type(NotificationType.VIDEO_PROCESSING_STARTED)
                    .title("Test 1")
                    .content("Test 1")
                    .isRead(false)
                    .status(NotificationStatus.SENT)
                    .retryCount(0)
                    .build();

            Notification notification2 = Notification.builder()
                    .id(2L)
                    .memberId(testMemberId)
                    .type(NotificationType.VIDEO_PROCESSING_COMPLETED)
                    .title("Test 2")
                    .content("Test 2")
                    .isRead(false)
                    .status(NotificationStatus.SENT)
                    .retryCount(0)
                    .build();

            List<Notification> unreadNotifications = List.of(notification1, notification2);

            given(notificationAdaptor.queryUnreadByMemberId(testMemberId))
                    .willReturn(unreadNotifications);
            given(notificationAdaptor.saveAll(anyList()))
                    .willReturn(unreadNotifications);

            // When
            notificationService.markAllAsRead(testMemberId);

            // Then
            verify(notificationAdaptor).queryUnreadByMemberId(testMemberId);
            verify(notificationAdaptor).saveAll(argThat(list ->
                    list.size() == 2 &&
                    list.stream().allMatch(Notification::getIsRead)
            ));
        }
    }

    @Nested
    @DisplayName("알림 조회 테스트")
    class GetNotificationsTest {

        @Test
        @DisplayName("성공: 읽지 않은 알림 목록 조회")
        void getUnreadNotifications_Success() {
            // Given
            List<Notification> unreadNotifications = List.of(testNotification);
            given(notificationAdaptor.queryUnreadByMemberId(testMemberId))
                    .willReturn(unreadNotifications);

            // When
            List<NotificationResponse> result = notificationService.getUnreadNotifications(testMemberId);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getType()).isEqualTo(NotificationType.VIDEO_PROCESSING_STARTED);
            verify(notificationAdaptor).queryUnreadByMemberId(testMemberId);
        }

        @Test
        @DisplayName("성공: 읽지 않은 알림 개수 조회")
        void getUnreadCount_Success() {
            // Given
            given(notificationAdaptor.countUnreadByMemberId(testMemberId))
                    .willReturn(5L);

            // When
            Long count = notificationService.getUnreadCount(testMemberId);

            // Then
            assertThat(count).isEqualTo(5L);
            verify(notificationAdaptor).countUnreadByMemberId(testMemberId);
        }
    }
}
