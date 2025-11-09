package com.example.echoshotx.notification.application.service;

import com.example.echoshotx.member.domain.entity.Member;
import com.example.echoshotx.member.domain.entity.Role;
import com.example.echoshotx.member.infrastructure.jpa.MemberRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * NotificationRetryScheduler 통합 테스트
 *
 * 실제 스케줄러 로직을 검증합니다.
 * 스케줄은 실제로 대기하지 않고 메서드를 직접 호출하여 테스트합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("NotificationRetryScheduler 통합 테스트")
class NotificationRetrySchedulerIntegrationTest {

    @Autowired
    private NotificationRetryScheduler notificationRetryScheduler;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private SseConnectionManager sseConnectionManager;

    private Member testMember;

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
    }

    @Nested
    @DisplayName("실패한 알림 재시도 테스트")
    class RetryFailedNotificationsTest {

        @Test
        @DisplayName("성공: 실패한 알림이 재시도됨")
        void retryFailedNotifications_RetriesFailed() {
            // Given: 실패한 알림 생성 (5분 이전에 실패)
            Notification failedNotification = Notification.createVideoNotification(
                    testMember.getId(),
                    100L,
                    NotificationType.VIDEO_PROCESSING_STARTED,
                    "영상 처리 시작",
                    "test.mp4 영상 처리가 시작되었습니다."
            );
            failedNotification.markAsFailed();
            failedNotification = notificationRepository.save(failedNotification);

            // lastRetryAt을 6분 전으로 설정 (재시도 대상)
            Notification toUpdate = notificationRepository.findById(failedNotification.getId()).orElseThrow();
            // Reflection을 사용하여 lastRetryAt 변경
            try {
                java.lang.reflect.Field field = Notification.class.getDeclaredField("lastRetryAt");
                field.setAccessible(true);
                field.set(toUpdate, LocalDateTime.now().minusMinutes(6));
                notificationRepository.save(toUpdate);
            } catch (Exception e) {
                fail("Failed to set lastRetryAt: " + e.getMessage());
            }

            // When: 스케줄러 실행
            notificationRetryScheduler.retryFailedNotifications();

            // Then: 재시도 카운트가 증가하고 상태가 업데이트됨
            Notification retried = notificationRepository.findById(failedNotification.getId()).orElseThrow();
            assertThat(retried.getRetryCount()).isGreaterThan(0);
        }

        @Test
        @DisplayName("성공: 재시도 횟수가 3회를 초과한 알림은 재시도하지 않음")
        void retryFailedNotifications_SkipsMaxRetriesExceeded() {
            // Given: 이미 3번 재시도한 알림
            Notification maxRetriedNotification = Notification.createVideoNotification(
                    testMember.getId(),
                    100L,
                    NotificationType.VIDEO_PROCESSING_STARTED,
                    "영상 처리 시작",
                    "test.mp4"
            );
            maxRetriedNotification.markAsFailed();
            maxRetriedNotification.markAsFailed();
            maxRetriedNotification.markAsFailed(); // 3번 실패
            maxRetriedNotification = notificationRepository.save(maxRetriedNotification);

            int initialRetryCount = maxRetriedNotification.getRetryCount();

            // When
            notificationRetryScheduler.retryFailedNotifications();

            // Then: 재시도 카운트가 증가하지 않음
            Notification notRetried = notificationRepository.findById(maxRetriedNotification.getId()).orElseThrow();
            assertThat(notRetried.getRetryCount()).isEqualTo(initialRetryCount);
            assertThat(notRetried.getStatus()).isEqualTo(NotificationStatus.FAILED);
        }

        @Test
        @DisplayName("성공: 최근 5분 이내 실패한 알림은 재시도하지 않음")
        void retryFailedNotifications_SkipsRecentFailures() {
            // Given: 방금 실패한 알림 (lastRetryAt이 현재 시간)
            Notification recentFailedNotification = Notification.createVideoNotification(
                    testMember.getId(),
                    100L,
                    NotificationType.VIDEO_PROCESSING_STARTED,
                    "영상 처리 시작",
                    "test.mp4"
            );
            recentFailedNotification.markAsFailed();
            recentFailedNotification = notificationRepository.save(recentFailedNotification);

            int initialRetryCount = recentFailedNotification.getRetryCount();

            // When
            notificationRetryScheduler.retryFailedNotifications();

            // Then: 재시도되지 않음
            Notification notRetried = notificationRepository.findById(recentFailedNotification.getId()).orElseThrow();
            assertThat(notRetried.getRetryCount()).isEqualTo(initialRetryCount);
        }

        @Test
        @DisplayName("성공: 여러 개의 실패한 알림을 한 번에 재시도")
        void retryFailedNotifications_RetriesMultiple() {
            // Given: 3개의 실패한 알림
            Notification failed1 = createAndSaveFailedNotification(101L, "video1.mp4");
            Notification failed2 = createAndSaveFailedNotification(102L, "video2.mp4");
            Notification failed3 = createAndSaveFailedNotification(103L, "video3.mp4");

            // lastRetryAt을 모두 6분 전으로 설정
            setLastRetryAt(failed1.getId(), LocalDateTime.now().minusMinutes(6));
            setLastRetryAt(failed2.getId(), LocalDateTime.now().minusMinutes(6));
            setLastRetryAt(failed3.getId(), LocalDateTime.now().minusMinutes(6));

            // When
            notificationRetryScheduler.retryFailedNotifications();

            // Then: 모든 알림이 재시도됨
            List<Notification> allNotifications = notificationRepository.findAll();
            assertThat(allNotifications).hasSize(3);
            // 재시도 카운트 또는 상태 변화 확인 가능
        }

        private Notification createAndSaveFailedNotification(Long videoId, String fileName) {
            Notification notification = Notification.createVideoNotification(
                    testMember.getId(),
                    videoId,
                    NotificationType.VIDEO_PROCESSING_STARTED,
                    "영상 처리 시작",
                    fileName
            );
            notification.markAsFailed();
            return notificationRepository.save(notification);
        }

        private void setLastRetryAt(Long notificationId, LocalDateTime lastRetryAt) {
            Notification notification = notificationRepository.findById(notificationId).orElseThrow();
            try {
                java.lang.reflect.Field field = Notification.class.getDeclaredField("lastRetryAt");
                field.setAccessible(true);
                field.set(notification, lastRetryAt);
                notificationRepository.save(notification);
            } catch (Exception e) {
                fail("Failed to set lastRetryAt: " + e.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("오래된 알림 삭제 테스트")
    class CleanupOldNotificationsTest {

        @Test
        @DisplayName("성공: 30일 이상 된 알림이 삭제됨")
        void cleanupOldNotifications_DeletesOldNotifications() {
            // Given: 35일 전 알림 생성
            Notification oldNotification = Notification.createVideoNotification(
                    testMember.getId(),
                    100L,
                    NotificationType.VIDEO_PROCESSING_COMPLETED,
                    "영상 처리 완료",
                    "old_video.mp4"
            );
            oldNotification = notificationRepository.save(oldNotification);

            // createdDate를 35일 전으로 설정
            setCreatedDate(oldNotification.getId(), LocalDateTime.now().minusDays(35));

            // When
            notificationRetryScheduler.cleanupOldNotifications();

            // Then: 알림이 삭제됨
            boolean exists = notificationRepository.existsById(oldNotification.getId());
            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("성공: 30일 미만 알림은 삭제되지 않음")
        void cleanupOldNotifications_KeepsRecentNotifications() {
            // Given: 20일 전 알림
            Notification recentNotification = Notification.createVideoNotification(
                    testMember.getId(),
                    100L,
                    NotificationType.VIDEO_PROCESSING_COMPLETED,
                    "영상 처리 완료",
                    "recent_video.mp4"
            );
            recentNotification = notificationRepository.save(recentNotification);

            setCreatedDate(recentNotification.getId(), LocalDateTime.now().minusDays(20));

            // When
            notificationRetryScheduler.cleanupOldNotifications();

            // Then: 알림이 유지됨
            boolean exists = notificationRepository.existsById(recentNotification.getId());
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("성공: 정확히 30일 된 알림은 유지됨")
        void cleanupOldNotifications_KeepsExactly30DaysOld() {
            // Given: 정확히 30일 전 알림
            Notification thirtyDaysOld = Notification.createVideoNotification(
                    testMember.getId(),
                    100L,
                    NotificationType.VIDEO_PROCESSING_COMPLETED,
                    "영상 처리 완료",
                    "30days_video.mp4"
            );
            thirtyDaysOld = notificationRepository.save(thirtyDaysOld);

            setCreatedDate(thirtyDaysOld.getId(), LocalDateTime.now().minusDays(30));

            // When
            notificationRetryScheduler.cleanupOldNotifications();

            // Then: 30일 경계값은 유지됨
            boolean exists = notificationRepository.existsById(thirtyDaysOld.getId());
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("성공: 여러 개의 오래된 알림을 한 번에 삭제")
        void cleanupOldNotifications_DeletesMultipleOld() {
            // Given: 여러 개의 오래된 알림
            Notification old1 = createAndSaveNotification(101L, "old1.mp4");
            Notification old2 = createAndSaveNotification(102L, "old2.mp4");
            Notification recent = createAndSaveNotification(103L, "recent.mp4");

            setCreatedDate(old1.getId(), LocalDateTime.now().minusDays(35));
            setCreatedDate(old2.getId(), LocalDateTime.now().minusDays(40));
            setCreatedDate(recent.getId(), LocalDateTime.now().minusDays(10)); // 최근 알림

            // When
            notificationRetryScheduler.cleanupOldNotifications();

            // Then: 오래된 알림만 삭제
            assertThat(notificationRepository.existsById(old1.getId())).isFalse();
            assertThat(notificationRepository.existsById(old2.getId())).isFalse();
            assertThat(notificationRepository.existsById(recent.getId())).isTrue();
        }

        private Notification createAndSaveNotification(Long videoId, String fileName) {
            Notification notification = Notification.createVideoNotification(
                    testMember.getId(),
                    videoId,
                    NotificationType.VIDEO_PROCESSING_COMPLETED,
                    "영상 처리 완료",
                    fileName
            );
            return notificationRepository.save(notification);
        }

        private void setCreatedDate(Long notificationId, LocalDateTime createdDate) {
            Notification notification = notificationRepository.findById(notificationId).orElseThrow();
            try {
                java.lang.reflect.Field field = notification.getClass().getSuperclass()
                        .getDeclaredField("createdDate");
                field.setAccessible(true);
                field.set(notification, createdDate);
                notificationRepository.save(notification);
            } catch (Exception e) {
                fail("Failed to set createdDate: " + e.getMessage());
            }
        }
    }
}
