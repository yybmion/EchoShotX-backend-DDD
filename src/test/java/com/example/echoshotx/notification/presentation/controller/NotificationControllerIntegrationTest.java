package com.example.echoshotx.notification.presentation.controller;

import com.example.echoshotx.member.domain.entity.Member;
import com.example.echoshotx.member.domain.entity.Role;
import com.example.echoshotx.member.infrastructure.jpa.MemberRepository;
import com.example.echoshotx.notification.application.service.NotificationService;
import com.example.echoshotx.notification.domain.entity.Notification;
import com.example.echoshotx.notification.domain.entity.NotificationType;
import com.example.echoshotx.notification.infrastructure.jpa.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * NotificationController 통합 테스트
 *
 * 실제 Spring Context를 띄워서 전체 레이어(Controller → Service → Repository)를 검증합니다.
 * H2 인메모리 DB를 사용하여 실제 데이터베이스 연동을 테스트합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("NotificationController 통합 테스트")
class NotificationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private MemberRepository memberRepository;

    private Member testMember;
    private Notification testNotification1;
    private Notification testNotification2;

    @BeforeEach
    void setUp() {
        // 테스트 회원 생성
        testMember = Member.builder()
                .username("test@example.com")
                .nickname("테스트유저")
                .email("test@example.com")
                .role(Role.USER)
                .currentCredits(1000)
                .build();
        testMember = memberRepository.save(testMember);

        // 테스트 알림 생성
        testNotification1 = Notification.createVideoNotification(
                testMember.getId(),
                100L,
                NotificationType.VIDEO_PROCESSING_STARTED,
                "영상 처리 시작",
                "test.mp4 영상 처리가 시작되었습니다."
        );
        testNotification1 = notificationRepository.save(testNotification1);

        testNotification2 = Notification.createVideoNotification(
                testMember.getId(),
                101L,
                NotificationType.VIDEO_PROCESSING_COMPLETED,
                "영상 처리 완료",
                "test2.mp4 영상 처리가 완료되었습니다."
        );
        testNotification2 = notificationRepository.save(testNotification2);
    }

    @Nested
    @DisplayName("알림 조회 API 테스트")
    class GetNotificationsTest {

        @Test
        @WithMockUser(username = "test@example.com", roles = "USER")
        @DisplayName("성공: 전체 알림 목록 조회")
        void getNotifications_Success() throws Exception {
            // When & Then
            mockMvc.perform(get("/notifications")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(2));
        }

        @Test
        @WithMockUser(username = "test@example.com", roles = "USER")
        @DisplayName("성공: 읽지 않은 알림만 조회")
        void getUnreadNotifications_Success() throws Exception {
            // Given: 하나의 알림을 읽음 처리
            testNotification1.markAsRead();
            notificationRepository.save(testNotification1);

            // When & Then
            mockMvc.perform(get("/notifications/unread")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].id").value(testNotification2.getId()));
        }

        @Test
        @WithMockUser(username = "test@example.com", roles = "USER")
        @DisplayName("성공: 읽지 않은 알림 개수 조회")
        void getUnreadCount_Success() throws Exception {
            // When & Then
            mockMvc.perform(get("/notifications/unread/count")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.count").value(2));
        }

        @Test
        @WithMockUser(username = "test@example.com", roles = "USER")
        @DisplayName("성공: 타입별 알림 조회")
        void getNotificationsByType_Success() throws Exception {
            // When & Then
            mockMvc.perform(get("/notifications/type/VIDEO_PROCESSING_STARTED")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].type").value("VIDEO_PROCESSING_STARTED"));
        }
    }

    @Nested
    @DisplayName("알림 읽음 처리 API 테스트")
    class MarkAsReadTest {

        @Test
        @WithMockUser(username = "test@example.com", roles = "USER")
        @DisplayName("성공: 단일 알림 읽음 처리")
        void markAsRead_Success() throws Exception {
            // When
            mockMvc.perform(patch("/notifications/{notificationId}/read", testNotification1.getId())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            // Then: DB에서 실제로 읽음 처리되었는지 확인
            Notification updated = notificationRepository.findById(testNotification1.getId()).orElseThrow();
            assertThat(updated.getIsRead()).isTrue();
        }

        @Test
        @WithMockUser(username = "test@example.com", roles = "USER")
        @DisplayName("성공: 모든 알림 읽음 처리")
        void markAllAsRead_Success() throws Exception {
            // When
            mockMvc.perform(patch("/notifications/read-all")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            // Then: 모든 알림이 읽음 처리되었는지 확인
            Notification updated1 = notificationRepository.findById(testNotification1.getId()).orElseThrow();
            Notification updated2 = notificationRepository.findById(testNotification2.getId()).orElseThrow();

            assertThat(updated1.getIsRead()).isTrue();
            assertThat(updated2.getIsRead()).isTrue();
        }
    }

    @Nested
    @DisplayName("알림 삭제 API 테스트")
    class DeleteNotificationTest {

        @Test
        @WithMockUser(username = "test@example.com", roles = "USER")
        @DisplayName("성공: 알림 삭제")
        void deleteNotification_Success() throws Exception {
            // When
            mockMvc.perform(delete("/notifications/{notificationId}", testNotification1.getId())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            // Then: DB에서 실제로 삭제되었는지 확인
            boolean exists = notificationRepository.existsById(testNotification1.getId());
            assertThat(exists).isFalse();
        }
    }

    @Nested
    @DisplayName("SSE 연결 상태 API 테스트")
    class ConnectionStatusTest {

        @Test
        @WithMockUser(username = "test@example.com", roles = "USER")
        @DisplayName("성공: SSE 연결 상태 조회")
        void getConnectionStatus_Success() throws Exception {
            // When & Then: 초기 연결 수는 0
            mockMvc.perform(get("/notifications/connection/status")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").value(0));
        }
    }

    @Nested
    @DisplayName("전체 알림 플로우 통합 테스트")
    class NotificationFlowTest {

        @Test
        @WithMockUser(username = "test@example.com", roles = "USER")
        @DisplayName("성공: 알림 생성 → 조회 → 읽음 처리 → 삭제 전체 플로우")
        void completeNotificationFlow_Success() throws Exception {
            // 1. 초기 상태 확인: 2개의 알림
            mockMvc.perform(get("/notifications"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2));

            // 2. 읽지 않은 알림 개수 확인: 2개
            mockMvc.perform(get("/notifications/unread/count"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.count").value(2));

            // 3. 첫 번째 알림 읽음 처리
            mockMvc.perform(patch("/notifications/{notificationId}/read", testNotification1.getId()))
                    .andExpect(status().isOk());

            // 4. 읽지 않은 알림 개수 확인: 1개로 감소
            mockMvc.perform(get("/notifications/unread/count"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.count").value(1));

            // 5. 모든 알림 읽음 처리
            mockMvc.perform(patch("/notifications/read-all"))
                    .andExpect(status().isOk());

            // 6. 읽지 않은 알림 개수 확인: 0개
            mockMvc.perform(get("/notifications/unread/count"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.count").value(0));

            // 7. 첫 번째 알림 삭제
            mockMvc.perform(delete("/notifications/{notificationId}", testNotification1.getId()))
                    .andExpect(status().isOk());

            // 8. 전체 알림 개수 확인: 1개로 감소
            mockMvc.perform(get("/notifications"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1));
        }
    }
}
