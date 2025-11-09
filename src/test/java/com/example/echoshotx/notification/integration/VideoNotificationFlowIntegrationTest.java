package com.example.echoshotx.notification.integration;

import com.example.echoshotx.member.domain.entity.Member;
import com.example.echoshotx.member.domain.entity.Role;
import com.example.echoshotx.member.infrastructure.jpa.MemberRepository;
import com.example.echoshotx.notification.application.event.VideoProcessingCompletedEvent;
import com.example.echoshotx.notification.application.event.VideoProcessingFailedEvent;
import com.example.echoshotx.notification.application.event.VideoProcessingStartedEvent;
import com.example.echoshotx.notification.domain.entity.Notification;
import com.example.echoshotx.notification.domain.entity.NotificationStatus;
import com.example.echoshotx.notification.domain.entity.NotificationType;
import com.example.echoshotx.notification.infrastructure.jpa.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.*;

/**
 * 비디오 처리 → 이벤트 발행 → 알림 생성 전체 플로우 통합 테스트
 *
 * 실제 Spring의 이벤트 시스템과 비동기 처리를 검증합니다.
 * @Async로 인한 비동기 처리는 Awaitility를 사용하여 검증합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("비디오-알림 통합 플로우 테스트")
class VideoNotificationFlowIntegrationTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private MemberRepository memberRepository;

    private Member testMember;
    private Long testVideoId;
    private String testFileName;

    @BeforeEach
    void setUp() {
        // 테스트 데이터 초기화
        notificationRepository.deleteAll();

        testMember = Member.builder()
                .username("test@example.com")
                .nickname("테스트유저")
                .email("test@example.com")
                .role(Role.USER)
                .currentCredits(1000)
                .build();
        testMember = memberRepository.save(testMember);

        testVideoId = 100L;
        testFileName = "test_video.mp4";
    }

    @Nested
    @DisplayName("비디오 처리 시작 이벤트 → 알림 생성")
    class VideoProcessingStartedEventTest {

        @Test
        @DisplayName("성공: VideoProcessingStartedEvent 발행 시 알림이 자동 생성됨")
        void publishVideoProcessingStartedEvent_CreatesNotification() {
            // Given
            VideoProcessingStartedEvent event = new VideoProcessingStartedEvent(
                    testVideoId,
                    testMember.getId(),
                    testFileName,
                    "BASIC_ENHANCEMENT"
            );

            // When: 이벤트 발행
            eventPublisher.publishEvent(event);

            // Then: 비동기 처리를 기다린 후 알림이 생성되었는지 확인
            await().atMost(3, TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        List<Notification> notifications = notificationRepository
                                .findByMemberIdAndIsReadOrderByCreatedDateDesc(testMember.getId(), false);

                        assertThat(notifications).hasSize(1);
                        Notification notification = notifications.get(0);

                        assertThat(notification.getMemberId()).isEqualTo(testMember.getId());
                        assertThat(notification.getVideoId()).isEqualTo(testVideoId);
                        assertThat(notification.getType()).isEqualTo(NotificationType.VIDEO_PROCESSING_STARTED);
                        assertThat(notification.getTitle()).isEqualTo("영상 처리 시작");
                        assertThat(notification.getContent()).contains(testFileName);
                        assertThat(notification.getContent()).contains("BASIC_ENHANCEMENT");
                        assertThat(notification.getIsRead()).isFalse();
                    });
        }

        @Test
        @DisplayName("성공: 알림 상태가 PENDING 또는 FAILED로 설정됨 (SSE 연결 없으면 FAILED)")
        void notification_StatusIsSetCorrectly() {
            // Given
            VideoProcessingStartedEvent event = new VideoProcessingStartedEvent(
                    testVideoId,
                    testMember.getId(),
                    testFileName,
                    "AI_UPSCALING"
            );

            // When
            eventPublisher.publishEvent(event);

            // Then: SSE 연결이 없으면 FAILED 상태로 저장됨
            await().atMost(3, TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        List<Notification> notifications = notificationRepository
                                .findByMemberIdAndIsReadOrderByCreatedDateDesc(testMember.getId(), false);

                        assertThat(notifications).hasSize(1);
                        Notification notification = notifications.get(0);

                        // SSE 연결이 없으므로 FAILED 상태
                        assertThat(notification.getStatus())
                                .isIn(NotificationStatus.PENDING, NotificationStatus.FAILED);
                    });
        }
    }

    @Nested
    @DisplayName("비디오 처리 완료 이벤트 → 알림 생성")
    class VideoProcessingCompletedEventTest {

        @Test
        @DisplayName("성공: VideoProcessingCompletedEvent 발행 시 알림이 자동 생성됨")
        void publishVideoProcessingCompletedEvent_CreatesNotification() {
            // Given
            VideoProcessingCompletedEvent event = new VideoProcessingCompletedEvent(
                    testVideoId,
                    testMember.getId(),
                    testFileName
            );

            // When
            eventPublisher.publishEvent(event);

            // Then
            await().atMost(3, TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        List<Notification> notifications = notificationRepository
                                .findByMemberIdAndIsReadOrderByCreatedDateDesc(testMember.getId(), false);

                        assertThat(notifications).hasSize(1);
                        Notification notification = notifications.get(0);

                        assertThat(notification.getMemberId()).isEqualTo(testMember.getId());
                        assertThat(notification.getVideoId()).isEqualTo(testVideoId);
                        assertThat(notification.getType()).isEqualTo(NotificationType.VIDEO_PROCESSING_COMPLETED);
                        assertThat(notification.getTitle()).isEqualTo("영상 처리 완료");
                        assertThat(notification.getContent()).contains(testFileName);
                        assertThat(notification.getContent()).contains("완료");
                        assertThat(notification.getContent()).contains("다운로드");
                    });
        }
    }

    @Nested
    @DisplayName("비디오 처리 실패 이벤트 → 알림 생성")
    class VideoProcessingFailedEventTest {

        @Test
        @DisplayName("성공: VideoProcessingFailedEvent 발행 시 알림이 자동 생성됨")
        void publishVideoProcessingFailedEvent_CreatesNotification() {
            // Given
            String errorReason = "파일 형식이 지원되지 않습니다";
            VideoProcessingFailedEvent event = new VideoProcessingFailedEvent(
                    testVideoId,
                    testMember.getId(),
                    testFileName,
                    errorReason
            );

            // When
            eventPublisher.publishEvent(event);

            // Then
            await().atMost(3, TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        List<Notification> notifications = notificationRepository
                                .findByMemberIdAndIsReadOrderByCreatedDateDesc(testMember.getId(), false);

                        assertThat(notifications).hasSize(1);
                        Notification notification = notifications.get(0);

                        assertThat(notification.getMemberId()).isEqualTo(testMember.getId());
                        assertThat(notification.getVideoId()).isEqualTo(testVideoId);
                        assertThat(notification.getType()).isEqualTo(NotificationType.VIDEO_PROCESSING_FAILED);
                        assertThat(notification.getTitle()).isEqualTo("영상 처리 실패");
                        assertThat(notification.getContent()).contains(testFileName);
                        assertThat(notification.getContent()).contains(errorReason);
                        assertThat(notification.getContent()).contains("실패");
                    });
        }
    }

    @Nested
    @DisplayName("전체 비디오 처리 플로우 시나리오")
    class CompleteVideoProcessingFlowTest {

        @Test
        @DisplayName("성공: 처리 시작 → 완료까지 전체 플로우에서 모든 알림이 생성됨")
        void completeVideoProcessingFlow_CreatesAllNotifications() {
            // 1. 처리 시작 이벤트
            eventPublisher.publishEvent(new VideoProcessingStartedEvent(
                    testVideoId, testMember.getId(), testFileName, "BASIC_ENHANCEMENT"
            ));

            await().atMost(3, TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        List<Notification> notifications = notificationRepository
                                .findByMemberIdAndIsReadOrderByCreatedDateDesc(testMember.getId(), false);
                        assertThat(notifications).hasSize(1);
                        assertThat(notifications.get(0).getType())
                                .isEqualTo(NotificationType.VIDEO_PROCESSING_STARTED);
                    });

            // 2. 처리 완료 이벤트
            eventPublisher.publishEvent(new VideoProcessingCompletedEvent(
                    testVideoId, testMember.getId(), testFileName
            ));

            await().atMost(3, TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        List<Notification> notifications = notificationRepository
                                .findByMemberIdAndIsReadOrderByCreatedDateDesc(testMember.getId(), false);
                        assertThat(notifications).hasSize(2);

                        // 최신 알림이 처리 완료 알림
                        assertThat(notifications.get(0).getType())
                                .isEqualTo(NotificationType.VIDEO_PROCESSING_COMPLETED);
                        // 이전 알림이 처리 시작 알림
                        assertThat(notifications.get(1).getType())
                                .isEqualTo(NotificationType.VIDEO_PROCESSING_STARTED);
                    });
        }

        @Test
        @DisplayName("성공: 처리 시작 → 실패까지 플로우에서 알림이 생성됨")
        void videoProcessingFailureFlow_CreatesNotifications() {
            // 1. 처리 시작
            eventPublisher.publishEvent(new VideoProcessingStartedEvent(
                    testVideoId, testMember.getId(), testFileName, "AI_UPSCALING"
            ));

            await().atMost(3, TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        assertThat(notificationRepository.count()).isEqualTo(1);
                    });

            // 2. 처리 실패
            eventPublisher.publishEvent(new VideoProcessingFailedEvent(
                    testVideoId, testMember.getId(), testFileName, "처리 시간 초과"
            ));

            await().atMost(3, TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        List<Notification> notifications = notificationRepository
                                .findByMemberIdAndIsReadOrderByCreatedDateDesc(testMember.getId(), false);
                        assertThat(notifications).hasSize(2);

                        assertThat(notifications.get(0).getType())
                                .isEqualTo(NotificationType.VIDEO_PROCESSING_FAILED);
                        assertThat(notifications.get(0).getContent()).contains("처리 시간 초과");
                    });
        }
    }

    @Nested
    @DisplayName("여러 비디오 동시 처리 시나리오")
    class MultipleVideosTest {

        @Test
        @DisplayName("성공: 여러 비디오 동시 처리 시 각각의 알림이 생성됨")
        void multipleVideos_CreatesSeparateNotifications() {
            // Given
            Long videoId1 = 101L;
            Long videoId2 = 102L;
            Long videoId3 = 103L;

            // When: 3개의 비디오 처리 시작
            eventPublisher.publishEvent(new VideoProcessingStartedEvent(
                    videoId1, testMember.getId(), "video1.mp4", "BASIC_ENHANCEMENT"
            ));
            eventPublisher.publishEvent(new VideoProcessingStartedEvent(
                    videoId2, testMember.getId(), "video2.mp4", "AI_UPSCALING"
            ));
            eventPublisher.publishEvent(new VideoProcessingStartedEvent(
                    videoId3, testMember.getId(), "video3.mp4", "BASIC_ENHANCEMENT"
            ));

            // Then: 3개의 알림이 모두 생성됨
            await().atMost(5, TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        List<Notification> notifications = notificationRepository
                                .findByMemberIdAndIsReadOrderByCreatedDateDesc(testMember.getId(), false);

                        assertThat(notifications).hasSize(3);

                        // 각 비디오에 대한 알림이 존재하는지 확인
                        assertThat(notifications)
                                .extracting(Notification::getVideoId)
                                .containsExactlyInAnyOrder(videoId1, videoId2, videoId3);
                    });
        }
    }
}
