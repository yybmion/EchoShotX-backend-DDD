# 실시간 알림 시스템 통합 가이드

## 목차
1. [시스템 개요](#시스템-개요)
2. [아키텍처](#아키텍처)
3. [API 엔드포인트](#api-엔드포인트)
4. [Video UseCase 통합 가이드](#video-usecase-통합-가이드)
5. [프론트엔드 연동 가이드](#프론트엔드-연동-가이드)
6. [알림 타입](#알림-타입)
7. [고려사항](#고려사항)

---

## 시스템 개요

### 주요 기능
- **SSE 기반 실시간 알림 전송**: Server-Sent Events를 사용한 단방향 실시간 통신
- **알림 읽음/안읽음 관리**: 사용자별 알림 상태 추적
- **자동 재연결 및 재시도**: 전송 실패 시 최대 3회 자동 재시도 (5분 간격)
- **다중 디바이스 지원**: 한 사용자가 여러 디바이스에서 동시 연결 가능
- **이벤트 기반 아키텍처**: Spring Event를 활용한 도메인 간 느슨한 결합

### 알림 타입
```java
// 영상 관련
VIDEO_UPLOAD_COMPLETED       // 영상 업로드 완료
VIDEO_PROCESSING_STARTED     // 영상 처리 시작
VIDEO_PROCESSING_COMPLETED   // 영상 처리 완료
VIDEO_PROCESSING_FAILED      // 영상 처리 실패

// 크레딧 관련
CREDIT_CHARGED              // 크레딧 충전
CREDIT_USED                 // 크레딧 사용
CREDIT_REFUNDED             // 크레딧 환불

// 시스템
SYSTEM_ANNOUNCEMENT         // 시스템 공지사항
```

---

## 아키텍처

### 도메인 구조
```
notification/
├── domain/
│   ├── entity/
│   │   ├── Notification.java              # 알림 엔티티
│   │   ├── NotificationType.java          # 알림 타입 enum
│   │   └── NotificationStatus.java        # 전송 상태 enum
│   └── exception/
│       └── NotificationErrorStatus.java   # 에러 코드 정의
│
├── infrastructure/
│   └── persistence/
│       └── NotificationRepository.java    # JPA Repository
│
├── application/
│   ├── adaptor/
│   │   └── NotificationAdaptor.java       # 읽기 전용 쿼리
│   ├── service/
│   │   ├── NotificationService.java       # 비즈니스 로직
│   │   ├── SseConnectionManager.java      # SSE 연결 관리
│   │   └── NotificationRetryScheduler.java # 재시도 스케줄러
│   └── event/
│       ├── VideoNotificationEventListener.java  # 영상 이벤트 리스너
│       ├── CreditNotificationEventListener.java # 크레딧 이벤트 리스너
│       ├── Video*Event.java               # 영상 이벤트
│       └── Credit*Event.java              # 크레딧 이벤트
│
└── presentation/
    ├── controller/
    │   └── NotificationController.java    # REST API
    ├── dto/
    │   ├── request/
    │   │   └── SendNotificationRequest.java
    │   └── response/
    │       ├── NotificationResponse.java
    │       └── UnreadCountResponse.java
    └── exception/
        └── NotificationHandler.java       # 예외 핸들러
```

### 데이터 플로우
```
[Domain Event] → [EventListener] → [NotificationService] → [SSE Manager] → [Client]
                                          ↓
                                    [Database]
                                          ↓
                                 [Retry Scheduler]
```

---

## API 엔드포인트

### 1. SSE 연결 생성
```http
GET /notifications/subscribe
Content-Type: text/event-stream
Authorization: Bearer {token}
```

**응답 (SSE Stream)**
```javascript
event: connected
data: SSE connection established

event: notification
data: {"id":1,"type":"VIDEO_PROCESSING_COMPLETED","category":"영상",...}
```

### 2. 알림 목록 조회
```http
GET /notifications
Authorization: Bearer {token}
```

**응답**
```json
{
  "isSuccess": true,
  "code": 2000,
  "message": "성공",
  "result": [
    {
      "id": 1,
      "type": "VIDEO_PROCESSING_COMPLETED",
      "category": "영상",
      "title": "영상 처리 완료",
      "content": "'sample.mp4' 영상 처리가 완료되었습니다.",
      "isRead": false,
      "status": "SENT",
      "videoId": 123,
      "creditHistoryId": null,
      "createdAt": "2024-11-04T10:30:00"
    }
  ]
}
```

### 3. 읽지 않은 알림 조회
```http
GET /notifications/unread
Authorization: Bearer {token}
```

### 4. 읽지 않은 알림 개수
```http
GET /notifications/unread/count
Authorization: Bearer {token}
```

**응답**
```json
{
  "isSuccess": true,
  "code": 2000,
  "message": "성공",
  "result": {
    "unreadCount": 5
  }
}
```

### 5. 타입별 알림 조회
```http
GET /notifications/type/{type}
Authorization: Bearer {token}
```

**타입 예시**: `VIDEO_PROCESSING_COMPLETED`, `CREDIT_CHARGED`

### 6. 알림 읽음 처리
```http
PATCH /notifications/{notificationId}/read
Authorization: Bearer {token}
```

### 7. 모든 알림 읽음 처리
```http
PATCH /notifications/read-all
Authorization: Bearer {token}
```

### 8. 알림 삭제
```http
DELETE /notifications/{notificationId}
Authorization: Bearer {token}
```

### 9. SSE 연결 상태 확인 (디버깅용)
```http
GET /notifications/connection/status
Authorization: Bearer {token}
```

---

## Video UseCase 통합 가이드

### 이벤트 발행 방법

Video 도메인에서 알림을 발송하려면 `ApplicationEventPublisher`를 주입받아 이벤트를 발행합니다.

#### 1. UseCase에 EventPublisher 주입

```java
@UseCase
@Transactional
@RequiredArgsConstructor
public class CompleteVideoUploadUseCase {

    private final VideoService videoService;
    private final ApplicationEventPublisher eventPublisher; // 추가

    public VideoDetailResponse execute(Long videoId, Member member) {
        Video video = videoService.completeUpload(videoId);

        // 영상 업로드 완료 이벤트 발행
        eventPublisher.publishEvent(new VideoUploadCompletedEvent(
            video.getId(),
            video.getMemberId(),
            video.getOriginalFile().getFileName()
        ));

        return VideoDetailResponse.from(video);
    }
}
```

#### 2. 사용 가능한 이벤트

**a) 영상 업로드 완료**
```java
import com.example.echoshotx.notification.application.event.VideoUploadCompletedEvent;

eventPublisher.publishEvent(new VideoUploadCompletedEvent(
    videoId,
    memberId,
    fileName
));
```

**b) 영상 처리 시작**
```java
import com.example.echoshotx.notification.application.event.VideoProcessingStartedEvent;

eventPublisher.publishEvent(new VideoProcessingStartedEvent(
    videoId,
    memberId,
    fileName,
    processingType.name() // "BASIC_ENHANCEMENT" or "AI_UPSCALING"
));
```

**c) 영상 처리 완료**
```java
import com.example.echoshotx.notification.application.event.VideoProcessingCompletedEvent;

eventPublisher.publishEvent(new VideoProcessingCompletedEvent(
    videoId,
    memberId,
    fileName
));
```

**d) 영상 처리 실패**
```java
import com.example.echoshotx.notification.application.event.VideoProcessingFailedEvent;

eventPublisher.publishEvent(new VideoProcessingFailedEvent(
    videoId,
    memberId,
    fileName,
    "처리 중 오류가 발생했습니다" // 실패 사유
));
```

#### 3. 실제 통합 예시

```java
@UseCase
@Transactional
@RequiredArgsConstructor
public class ProcessVideoUseCase {

    private final VideoAdaptor videoAdaptor;
    private final VideoService videoService;
    private final CreditService creditService;
    private final ApplicationEventPublisher eventPublisher;

    public void execute(Long videoId, Member member) {
        Video video = videoAdaptor.queryById(videoId);

        try {
            // 1. 크레딧 차감
            creditService.useCreditsForVideoProcessing(video, video.getProcessingType());

            // 2. 영상 상태 변경
            videoService.startProcessing(videoId);

            // 3. 처리 시작 알림 발송
            eventPublisher.publishEvent(new VideoProcessingStartedEvent(
                video.getId(),
                video.getMemberId(),
                video.getOriginalFile().getFileName(),
                video.getProcessingType().name()
            ));

            // 4. AI 서버에 처리 요청
            // ... AI processing logic ...

            // 5. 처리 완료 후
            videoService.completeProcessing(videoId);
            eventPublisher.publishEvent(new VideoProcessingCompletedEvent(
                video.getId(),
                video.getMemberId(),
                video.getOriginalFile().getFileName()
            ));

        } catch (Exception e) {
            // 6. 실패 시 알림 발송
            videoService.failProcessing(videoId);
            eventPublisher.publishEvent(new VideoProcessingFailedEvent(
                video.getId(),
                video.getMemberId(),
                video.getOriginalFile().getFileName(),
                e.getMessage()
            ));

            // 7. 크레딧 환불
            creditService.refundCredits(
                video.getMemberId(),
                video.getId(),
                calculatedCredits,
                "영상 처리 실패로 인한 환불"
            );

            throw e;
        }
    }
}
```

---

## 프론트엔드 연동 가이드

### 1. SSE 연결 설정 (JavaScript)

```javascript
// SSE 클라이언트 생성
class NotificationClient {
    constructor(token) {
        this.token = token;
        this.eventSource = null;
        this.reconnectInterval = 3000; // 3초
        this.maxRetries = 5;
        this.retryCount = 0;
    }

    connect() {
        const url = `${API_BASE_URL}/notifications/subscribe`;

        this.eventSource = new EventSource(url, {
            headers: {
                'Authorization': `Bearer ${this.token}`
            }
        });

        // 연결 성공
        this.eventSource.addEventListener('connected', (event) => {
            console.log('SSE connected:', event.data);
            this.retryCount = 0;
        });

        // 알림 수신
        this.eventSource.addEventListener('notification', (event) => {
            const notification = JSON.parse(event.data);
            this.handleNotification(notification);
        });

        // 에러 처리
        this.eventSource.onerror = (error) => {
            console.error('SSE error:', error);
            this.eventSource.close();

            if (this.retryCount < this.maxRetries) {
                this.retryCount++;
                console.log(`Reconnecting... (${this.retryCount}/${this.maxRetries})`);
                setTimeout(() => this.connect(), this.reconnectInterval);
            }
        };
    }

    handleNotification(notification) {
        console.log('Received notification:', notification);

        // UI 업데이트
        this.showToast(notification);
        this.updateUnreadCount();
        this.addToNotificationList(notification);

        // 타입별 처리
        switch(notification.type) {
            case 'VIDEO_PROCESSING_COMPLETED':
                this.onVideoCompleted(notification);
                break;
            case 'CREDIT_CHARGED':
                this.onCreditCharged(notification);
                break;
            // ... 기타 타입
        }
    }

    showToast(notification) {
        // Toast 알림 표시
        // 라이브러리: react-toastify, vue-toastification 등
    }

    updateUnreadCount() {
        fetch(`${API_BASE_URL}/notifications/unread/count`, {
            headers: { 'Authorization': `Bearer ${this.token}` }
        })
        .then(res => res.json())
        .then(data => {
            // 읽지 않은 알림 뱃지 업데이트
            document.querySelector('.notification-badge').textContent =
                data.result.unreadCount;
        });
    }

    disconnect() {
        if (this.eventSource) {
            this.eventSource.close();
        }
    }
}

// 사용 예시
const token = localStorage.getItem('accessToken');
const notificationClient = new NotificationClient(token);
notificationClient.connect();

// 페이지 언마운트 시
window.addEventListener('beforeunload', () => {
    notificationClient.disconnect();
});
```

### 2. React 통합 예시

```jsx
import { useEffect, useState, useCallback } from 'react';

function useNotifications() {
    const [notifications, setNotifications] = useState([]);
    const [unreadCount, setUnreadCount] = useState(0);
    const token = localStorage.getItem('accessToken');

    useEffect(() => {
        const eventSource = new EventSource(
            `${API_BASE_URL}/notifications/subscribe`,
            { headers: { 'Authorization': `Bearer ${token}` } }
        );

        eventSource.addEventListener('notification', (event) => {
            const notification = JSON.parse(event.data);

            // 새 알림 추가
            setNotifications(prev => [notification, ...prev]);
            setUnreadCount(prev => prev + 1);

            // Toast 표시
            toast.info(notification.content);
        });

        eventSource.onerror = () => {
            eventSource.close();
            // 재연결 로직
        };

        return () => eventSource.close();
    }, [token]);

    const markAsRead = useCallback(async (notificationId) => {
        await fetch(`${API_BASE_URL}/notifications/${notificationId}/read`, {
            method: 'PATCH',
            headers: { 'Authorization': `Bearer ${token}` }
        });

        setNotifications(prev =>
            prev.map(n => n.id === notificationId ? {...n, isRead: true} : n)
        );
        setUnreadCount(prev => Math.max(0, prev - 1));
    }, [token]);

    const markAllAsRead = useCallback(async () => {
        await fetch(`${API_BASE_URL}/notifications/read-all`, {
            method: 'PATCH',
            headers: { 'Authorization': `Bearer ${token}` }
        });

        setNotifications(prev => prev.map(n => ({...n, isRead: true})));
        setUnreadCount(0);
    }, [token]);

    return { notifications, unreadCount, markAsRead, markAllAsRead };
}

// 컴포넌트에서 사용
function NotificationDropdown() {
    const { notifications, unreadCount, markAsRead, markAllAsRead } = useNotifications();

    return (
        <div className="notification-dropdown">
            <button>
                🔔
                {unreadCount > 0 && <span className="badge">{unreadCount}</span>}
            </button>

            <div className="dropdown-content">
                <div className="header">
                    <h3>알림</h3>
                    <button onClick={markAllAsRead}>모두 읽음</button>
                </div>

                <ul>
                    {notifications.map(notification => (
                        <li
                            key={notification.id}
                            className={!notification.isRead ? 'unread' : ''}
                            onClick={() => markAsRead(notification.id)}
                        >
                            <span className="category">{notification.category}</span>
                            <h4>{notification.title}</h4>
                            <p>{notification.content}</p>
                            <time>{new Date(notification.createdAt).toLocaleString()}</time>
                        </li>
                    ))}
                </ul>
            </div>
        </div>
    );
}
```

### 3. Vue.js 통합 예시

```vue
<template>
  <div class="notification-bell">
    <button @click="toggleDropdown">
      🔔
      <span v-if="unreadCount > 0" class="badge">{{ unreadCount }}</span>
    </button>

    <div v-if="isOpen" class="dropdown">
      <div class="header">
        <h3>알림</h3>
        <button @click="markAllAsRead">모두 읽음</button>
      </div>

      <ul>
        <li
          v-for="notification in notifications"
          :key="notification.id"
          :class="{ unread: !notification.isRead }"
          @click="markAsRead(notification.id)"
        >
          <span class="category">{{ notification.category }}</span>
          <h4>{{ notification.title }}</h4>
          <p>{{ notification.content }}</p>
          <time>{{ formatDate(notification.createdAt) }}</time>
        </li>
      </ul>
    </div>
  </div>
</template>

<script>
import { ref, onMounted, onUnmounted } from 'vue';

export default {
  setup() {
    const notifications = ref([]);
    const unreadCount = ref(0);
    const isOpen = ref(false);
    let eventSource = null;

    const connectSSE = () => {
      const token = localStorage.getItem('accessToken');
      eventSource = new EventSource(`${API_BASE_URL}/notifications/subscribe`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });

      eventSource.addEventListener('notification', (event) => {
        const notification = JSON.parse(event.data);
        notifications.value.unshift(notification);
        unreadCount.value++;
      });

      eventSource.onerror = () => {
        eventSource.close();
        // 재연결 로직
      };
    };

    const markAsRead = async (id) => {
      // API 호출
      notifications.value = notifications.value.map(n =>
        n.id === id ? {...n, isRead: true} : n
      );
      unreadCount.value--;
    };

    const markAllAsRead = async () => {
      // API 호출
      notifications.value = notifications.value.map(n => ({...n, isRead: true}));
      unreadCount.value = 0;
    };

    onMounted(connectSSE);
    onUnmounted(() => eventSource?.close());

    return {
      notifications,
      unreadCount,
      isOpen,
      markAsRead,
      markAllAsRead,
      toggleDropdown: () => isOpen.value = !isOpen.value
    };
  }
};
</script>
```

---

## 고려사항

### 1. SSE 동시 연결 제한
- **브라우저 제한**: 대부분의 브라우저는 동일 도메인에 대해 최대 6개의 SSE 연결만 허용
- **권장사항**:
  - 한 페이지에서 하나의 SSE 연결만 생성
  - 탭 간 연결 공유를 위해 SharedWorker 또는 BroadcastChannel 사용 고려

```javascript
// BroadcastChannel을 사용한 탭 간 연결 공유
const channel = new BroadcastChannel('notifications');

// 첫 번째 탭에서만 SSE 연결
if (!localStorage.getItem('sseConnected')) {
    localStorage.setItem('sseConnected', 'true');
    const eventSource = new EventSource(...);

    eventSource.addEventListener('notification', (event) => {
        // 다른 탭에 알림 브로드캐스트
        channel.postMessage(event.data);
    });
}

// 다른 탭에서 알림 수신
channel.onmessage = (event) => {
    const notification = JSON.parse(event.data);
    // 알림 처리
};
```

### 2. 알림 보관 정책
- **자동 삭제**: 30일 이상 된 알림은 자동 삭제 (매일 자정 실행)
- **수동 삭제**: 사용자가 개별 알림 삭제 가능
- **보관 기간 변경**: `NotificationRetryScheduler.java`의 `cleanupOldNotifications` 메서드 수정

```java
// 보관 기간을 60일로 변경하려면
LocalDateTime cutoffDate = LocalDateTime.now().minusDays(60);
```

### 3. 다중 서버 환경 (스케일 아웃)
현재 구현은 **단일 서버 환경**을 위한 것입니다. 다중 서버 환경에서는:

**문제점**:
- SSE 연결은 특정 서버에만 유지됨
- 사용자가 서버 A에 연결되어 있는데, 알림이 서버 B에서 생성되면 전달 안됨

**해결 방안**:
1. **Redis Pub/Sub 사용**
   ```java
   @Service
   public class RedisNotificationPublisher {
       private final RedisTemplate<String, Object> redisTemplate;

       public void publishNotification(NotificationResponse notification) {
           redisTemplate.convertAndSend("notifications", notification);
       }
   }

   @Component
   public class RedisNotificationSubscriber {
       @RedisMessageListener(topics = "notifications")
       public void onMessage(NotificationResponse notification) {
           sseConnectionManager.sendToMember(
               notification.getMemberId(), notification);
       }
   }
   ```

2. **Sticky Session 사용**
   - 로드 밸런서에서 같은 사용자를 항상 같은 서버로 라우팅

3. **WebSocket으로 전환**
   - 양방향 통신 필요 시 WebSocket + STOMP 고려

### 4. 성능 최적화
- **인덱스 활용**: `member_id`, `is_read`, `created_date`에 인덱스 설정됨
- **쿼리 최적화**: 읽지 않은 알림만 조회하여 DB 부하 감소
- **비동기 처리**: `@Async`로 알림 생성과 전송을 비블로킹으로 처리

### 5. 보안 고려사항
- **인증 필수**: 모든 엔드포인트에 JWT 인증 적용
- **소유권 검증**: 알림 조회/수정/삭제 시 본인 확인
- **Rate Limiting**: 과도한 알림 생성 방지를 위해 Rate Limiter 적용 권장

```java
@RateLimiter(name = "notification", fallbackMethod = "rateLimitFallback")
public Notification createAndSendNotification(...) {
    // ...
}
```

---

## 문제 해결

### 1. 알림이 전송되지 않음
- SSE 연결 상태 확인: `GET /notifications/connection/status`
- 로그 확인: `NotificationService`, `SseConnectionManager` 로그
- 이벤트 발행 확인: 도메인에서 `eventPublisher.publishEvent()` 호출 여부

### 2. 알림이 중복 전송됨
- 이벤트 리스너에 `@Transactional` 있는지 확인
- 같은 이벤트를 여러 곳에서 발행하는지 확인

### 3. SSE 연결이 자주 끊김
- 타임아웃 설정 확인 (기본 60분)
- 네트워크 프록시나 방화벽 설정 확인
- Keep-alive 메시지 전송 고려

---

## 모니터링

### 주요 메트릭
- 활성 SSE 연결 수
- 알림 전송 성공/실패율
- 알림 전송 지연 시간
- 재시도 횟수

### 로그 위치
- 알림 생성: `NotificationService`
- SSE 연결: `SseConnectionManager`
- 재시도: `NotificationRetryScheduler`

---

## 추가 기능 구현 가이드

### 시스템 공지사항 발송 (관리자용)

```java
@RestController
@RequestMapping("/admin/notifications")
public class AdminNotificationController {

    private final NotificationService notificationService;
    private final MemberAdaptor memberAdaptor;

    @PostMapping("/broadcast")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponseDto<Void> broadcastAnnouncement(
            @RequestBody SendNotificationRequest request) {

        // 모든 사용자에게 공지사항 발송
        List<Member> allMembers = memberAdaptor.queryAll();

        for (Member member : allMembers) {
            notificationService.createAndSendSystemNotification(
                member.getId(),
                request.getTitle(),
                request.getContent()
            );
        }

        return ApiResponseDto.onSuccess(null);
    }
}
```

---

## 참고 자료
- [Server-Sent Events (MDN)](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events)
- [Spring Events Documentation](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events)
- [Spring Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
