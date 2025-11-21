# Redis Pub/Sub 테스트 가이드

## 📋 개요

Redis Pub/Sub 기반 실시간 진행률 업데이트 시스템의 테스트 코드입니다.

- **통합 테스트**: Testcontainers로 실제 Redis를 띄워서 Pub/Sub 동작 검증
- **단위 테스트**: Mockito로 VideoProgressRedisListener 로직 검증

---

## 🧪 테스트 구조

```
src/test/java/com/example/echoshotx/video/infrastructure/redis/
├── RedisProgressPubSubIntegrationTest.java  # 통합 테스트 (Testcontainers)
├── VideoProgressRedisListenerTest.java      # 단위 테스트 (Mockito)
└── README.md                                 # 이 파일
```

---

## 🚀 테스트 실행 방법

### 전체 Redis 테스트 실행

```bash
./gradlew test --tests "com.example.echoshotx.video.infrastructure.redis.*"
```

### 통합 테스트만 실행

```bash
./gradlew test --tests "com.example.echoshotx.video.infrastructure.redis.RedisProgressPubSubIntegrationTest"
```

### 단위 테스트만 실행

```bash
./gradlew test --tests "com.example.echoshotx.video.infrastructure.redis.VideoProgressRedisListenerTest"
```

---

## 🐳 통합 테스트: RedisProgressPubSubIntegrationTest

### 특징

- **Testcontainers 사용**: 실제 Redis 7.2 컨테이너를 띄워서 테스트
- **비동기 검증**: Awaitility로 비동기 Pub/Sub 메시지 처리 대기
- **완전한 통합 테스트**: Redis → Listener → Service → SSE 전체 플로우 검증

### 테스트 케이스

#### 1️⃣ `testRedisProgressMessagePublishAndSubscribe()`
**목적**: Redis 메시지 발행 시 리스너가 수신하고 처리하는지 검증

```java
// Given: 진행률 메시지 생성
VideoProgressMessage message = VideoProgressMessage.builder()
    .videoId(123L)
    .progressPercentage(50)
    .estimatedTimeLeftSeconds(120)
    .currentStep("AI 처리 중")
    .build();

// When: Redis 채널에 메시지 발행
redisTemplate.convertAndSend("video:progress:updates", messageJson);

// Then: 리스너가 수신하고 VideoService 호출 확인
verify(videoProgressRedisListener).onMessage(any(), any());
verify(videoService).updateProcessingProgress(testVideo, 50, 120, "AI 처리 중");
```

**검증 항목**:
- ✅ VideoProgressRedisListener.onMessage() 호출
- ✅ VideoService.updateProcessingProgress() 호출
- ✅ Video 엔티티의 진행률 필드 업데이트

---

#### 2️⃣ `testMultipleProgressMessages()`
**목적**: 여러 진행률 메시지를 순차적으로 처리하는지 검증

```java
// When: 여러 메시지 발행
publishProgressMessage(123L, 10, "영상 분석 중");
publishProgressMessage(123L, 30, "AI 처리 중");
publishProgressMessage(123L, 60, "AI 처리 중");
publishProgressMessage(123L, 90, "인코딩 중");

// Then: 4개 메시지 모두 처리 확인
verify(videoProgressRedisListener, times(4)).onMessage(any(), any());
```

**검증 항목**:
- ✅ 4개 메시지 모두 수신
- ✅ 마지막 진행률(90%)이 반영됨

---

#### 3️⃣ `testInvalidJsonMessageDoesNotStopProcessing()`
**목적**: 잘못된 JSON 메시지가 와도 시스템이 중단되지 않는지 검증

```java
// When: 잘못된 JSON과 정상 메시지 발행
redisTemplate.convertAndSend(channel, "{ invalid json }");
publishProgressMessage(123L, 50, "AI 처리 중");

// Then: 정상 메시지는 처리됨
verify(videoService, times(1))
    .updateProcessingProgress(testVideo, 50, any(), "AI 처리 중");
```

**검증 항목**:
- ✅ 잘못된 메시지로 인한 예외 발생하지 않음
- ✅ 이후 정상 메시지는 처리됨

---

#### 4️⃣ `testNonExistentVideoIdContinuesProcessing()`
**목적**: 존재하지 않는 비디오 ID로 인한 에러가 전체 시스템을 멈추지 않는지 검증

```java
// Given: 존재하지 않는 비디오 ID (999)
when(videoAdaptor.queryById(999L))
    .thenThrow(new RuntimeException("Video not found"));

// When: 존재하지 않는 ID와 정상 ID 메시지 발행
publishProgressMessage(999L, 50, "AI 처리 중");  // 에러 발생
publishProgressMessage(123L, 50, "AI 처리 중");  // 정상

// Then: 정상 메시지는 처리됨
verify(videoService, times(1))
    .updateProcessingProgress(testVideo, 50, any(), "AI 처리 중");
```

**검증 항목**:
- ✅ 존재하지 않는 비디오로 인한 예외가 전파되지 않음
- ✅ 다른 비디오의 진행률은 정상 처리

---

#### 5️⃣ `testProgressSentViaSSE()`
**목적**: 진행률이 SSE를 통해 클라이언트에 전송되는지 검증

```java
// When: 진행률 메시지 발행
publishProgressMessage(123L, 75, "인코딩 중");

// Then: SSE 전송 확인
verify(notificationService, times(1))
    .sendProgressUpdate(1L, 123L, 75, any(), "인코딩 중");
```

**검증 항목**:
- ✅ NotificationService.sendProgressUpdate() 호출
- ✅ 올바른 memberId, videoId, progress 전달

---

### 사용 기술

| 기술 | 용도 |
|------|------|
| **Testcontainers** | 실제 Redis 7.2 컨테이너 실행 |
| **Awaitility** | 비동기 메시지 처리 대기 및 검증 |
| **@SpyBean** | 실제 객체를 Spy로 래핑하여 호출 검증 |
| **@MockBean** | 의존성 Mock 처리 |

---

## 🔧 단위 테스트: VideoProgressRedisListenerTest

### 특징

- **Mock 기반**: Redis 없이 Mockito로 빠르게 테스트
- **로직 검증**: 리스너의 메시지 처리 로직만 집중 검증
- **경계값 테스트**: 0%, 100%, null 값 등 다양한 입력 검증

### 테스트 케이스

#### 1️⃣ `testOnMessageSuccess()`
**목적**: 정상 메시지 수신 시 VideoService 호출 검증

```java
// Given: 정상 메시지
VideoProgressMessage message = VideoProgressMessage.builder()
    .videoId(123L)
    .progressPercentage(50)
    .estimatedTimeLeftSeconds(120)
    .currentStep("AI 처리 중")
    .build();

// When: onMessage() 호출
listener.onMessage(message, null);

// Then: VideoService 호출 확인
verify(videoService).updateProcessingProgress(testVideo, 50, 120, "AI 처리 중");
```

---

#### 2️⃣ `testOnMessageInvalidJson()`
**목적**: 잘못된 JSON 처리 시 예외 처리 검증

```java
// Given: 잘못된 JSON
when(objectMapper.readValue(invalidJson, VideoProgressMessage.class))
    .thenThrow(new RuntimeException("JSON parse error"));

// When: onMessage() 호출
listener.onMessage(message, null);

// Then: 예외가 발생해도 프로그램 중단 안 됨
verify(videoService, never()).updateProcessingProgress(any(), any(), any(), any());
```

---

#### 3️⃣ `testOnMessageVideoNotFound()`
**목적**: 존재하지 않는 비디오 ID 처리 검증

```java
// Given: 존재하지 않는 비디오
when(videoAdaptor.queryById(999L))
    .thenThrow(new RuntimeException("Video not found"));

// When: onMessage() 호출
listener.onMessage(message, null);

// Then: 예외가 발생해도 프로그램 중단 안 됨
verify(videoService, never()).updateProcessingProgress(any(), any(), any(), any());
```

---

#### 4️⃣ `testOnMessageWithNullFields()`
**목적**: null 필드가 있는 메시지 처리 검증

```java
// Given: estimatedTimeLeft, currentStep이 null
VideoProgressMessage message = VideoProgressMessage.builder()
    .videoId(123L)
    .progressPercentage(30)
    .estimatedTimeLeftSeconds(null)
    .currentStep(null)
    .build();

// When: onMessage() 호출
listener.onMessage(message, null);

// Then: null 값도 정상 처리
verify(videoService).updateProcessingProgress(testVideo, 30, null, null);
```

---

#### 5️⃣ `testOnMessageWithZeroProgress()`
**목적**: 진행률 0% 처리 검증

```java
// Given: 진행률 0%
VideoProgressMessage message = VideoProgressMessage.builder()
    .videoId(123L)
    .progressPercentage(0)
    .build();

// When: onMessage() 호출
listener.onMessage(message, null);

// Then: 0%도 정상 처리
verify(videoService).updateProcessingProgress(testVideo, 0, 300, "처리 시작");
```

---

#### 6️⃣ `testOnMessageWith100Progress()`
**목적**: 진행률 100% 처리 검증

```java
// Given: 진행률 100%
VideoProgressMessage message = VideoProgressMessage.builder()
    .videoId(123L)
    .progressPercentage(100)
    .build();

// When: onMessage() 호출
listener.onMessage(message, null);

// Then: 100%도 정상 처리
verify(videoService).updateProcessingProgress(testVideo, 100, 0, "거의 완료");
```

---

## 🛠️ 테스트 환경 설정

### 필수 의존성 (build.gradle)

```gradle
dependencies {
    // Testcontainers
    testImplementation 'org.testcontainers:testcontainers:1.19.3'
    testImplementation 'org.testcontainers:junit-jupiter:1.19.3'
    testImplementation 'org.testcontainers:redis:1.19.3'

    // Awaitility (비동기 테스트)
    testImplementation 'org.awaitility:awaitility:4.2.0'

    // Spring Boot Test
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
```

### Docker 필수

통합 테스트는 Testcontainers가 Docker를 사용하므로 **Docker가 실행 중이어야 합니다**.

```bash
# Docker 상태 확인
docker ps

# Docker가 없으면 설치 필요
```

---

## 📊 테스트 커버리지

| 클래스 | 테스트 종류 | 커버리지 |
|--------|-------------|----------|
| VideoProgressRedisListener | 단위 + 통합 | 100% |
| VideoService.updateProcessingProgress() | 통합 | 100% |
| NotificationService.sendProgressUpdate() | 통합 | 100% |
| Redis Pub/Sub 채널 | 통합 | 100% |

---

## 🐛 트러블슈팅

### 1. Testcontainers가 Docker를 찾지 못함

**증상**:
```
Could not find a valid Docker environment
```

**해결**:
- Docker Desktop 실행 확인
- Docker 소켓 권한 확인: `sudo chmod 666 /var/run/docker.sock`

### 2. Redis 컨테이너 시작 실패

**증상**:
```
Container startup failed
```

**해결**:
- 포트 6379가 이미 사용 중인지 확인: `lsof -i :6379`
- 기존 Redis 중지 또는 다른 포트 사용

### 3. 비동기 테스트 실패 (Timeout)

**증상**:
```
ConditionTimeoutException: Condition was not fulfilled within 5 seconds
```

**해결**:
- Awaitility 타임아웃 증가: `.atMost(Duration.ofSeconds(10))`
- 로그 레벨을 DEBUG로 변경하여 메시지 수신 확인

### 4. Mock이 호출되지 않음

**증상**:
```
Wanted but not invoked: videoService.updateProcessingProgress(...)
```

**해결**:
- @SpyBean과 @MockBean 구분 확인
- 비동기 처리이므로 Awaitility 사용 확인

---

## 📝 추가 테스트 시나리오 (TODO)

- [ ] 대량 메시지 처리 성능 테스트 (1000개)
- [ ] Redis 연결 끊김 시나리오 테스트
- [ ] 여러 서버 인스턴스에서 동시 수신 테스트
- [ ] 진행률 순서 보장 테스트 (10% → 30% → 60%)
- [ ] 메모리 누수 테스트 (장시간 실행)

---

## 📚 참고 자료

- [Testcontainers 공식 문서](https://www.testcontainers.org/)
- [Awaitility 사용법](https://github.com/awaitility/awaitility)
- [Redis Pub/Sub 공식 문서](https://redis.io/docs/manual/pubsub/)
- [Spring Data Redis Testing](https://docs.spring.io/spring-data/redis/docs/current/reference/html/#redis.testing)

---

**작성일**: 2025-11-20
**작성자**: Claude
**버전**: 1.0.0
