# 📊 Redis Pub/Sub 기반 실시간 진행률 업데이트 가이드

## 📋 개요

AI 서버에서 비디오 처리 중 진행률을 백엔드에 실시간으로 전송하는 방법을 설명합니다.

**핵심 개념:**
- AI 서버가 Redis에 진행률 메시지를 **Publish**
- 백엔드가 Redis 채널을 **Subscribe**하여 자동 수신
- 백엔드가 SSE를 통해 클라이언트에 실시간 전송

---

## 🔧 시스템 아키텍처

```
AI 서버 (Python/Node.js 등)
   |
   v
Redis Pub/Sub (채널: "video:progress:updates")
   |
   v
백엔드 (VideoProgressRedisListener)
   |
   v
VideoService.updateProcessingProgress()
   |
   v
VideoProcessingProgressEvent 발행
   |
   v
VideoNotificationEventListener
   |
   v
NotificationService.sendProgressUpdate()
   |
   v
SSE (Server-Sent Events)
   |
   v
클라이언트 (브라우저/모바일 앱)
```

---

## 📡 Redis 채널 정보

- **채널 이름**: `video:progress:updates`
- **메시지 형식**: JSON
- **전송 주기**: 10-30초마다 (권장)

---

## 📤 AI 서버에서 진행률 전송하기

### Python 예제

```python
import redis
import json
from datetime import datetime

# Redis 연결
redis_client = redis.Redis(
    host='localhost',
    port=6379,
    decode_responses=True
)

def send_progress_update(
    video_id: int,
    ai_job_id: str,
    progress_percentage: int,
    estimated_time_left_seconds: int = None,
    current_step: str = None
):
    """
    비디오 처리 진행률을 Redis Pub/Sub로 전송합니다.

    Args:
        video_id: 비디오 ID (필수)
        ai_job_id: AI 작업 ID (선택)
        progress_percentage: 진행률 0-100 (필수)
        estimated_time_left_seconds: 예상 남은 시간 (초, 선택)
        current_step: 현재 처리 단계 (선택)
    """

    message = {
        "video_id": video_id,
        "ai_job_id": ai_job_id,
        "progress_percentage": progress_percentage,
        "estimated_time_left_seconds": estimated_time_left_seconds,
        "current_step": current_step,
        "timestamp": datetime.now().isoformat()
    }

    # Redis Pub/Sub 채널에 메시지 발행
    redis_client.publish(
        "video:progress:updates",
        json.dumps(message)
    )

    print(f"Progress update sent: video_id={video_id}, progress={progress_percentage}%")


# 사용 예시
def process_video(video_id: int, ai_job_id: str):
    """비디오 처리 예제"""

    # 1. 처리 시작 (0%)
    send_progress_update(
        video_id=video_id,
        ai_job_id=ai_job_id,
        progress_percentage=0,
        estimated_time_left_seconds=300,
        current_step="영상 분석 중"
    )

    # 2. 영상 분석 완료 (30%)
    send_progress_update(
        video_id=video_id,
        ai_job_id=ai_job_id,
        progress_percentage=30,
        estimated_time_left_seconds=210,
        current_step="AI 처리 중"
    )

    # 3. AI 처리 진행 중 (60%)
    send_progress_update(
        video_id=video_id,
        ai_job_id=ai_job_id,
        progress_percentage=60,
        estimated_time_left_seconds=120,
        current_step="AI 처리 중"
    )

    # 4. 인코딩 중 (90%)
    send_progress_update(
        video_id=video_id,
        ai_job_id=ai_job_id,
        progress_percentage=90,
        estimated_time_left_seconds=30,
        current_step="인코딩 중"
    )

    # 5. 거의 완료 (95%)
    send_progress_update(
        video_id=video_id,
        ai_job_id=ai_job_id,
        progress_percentage=95,
        estimated_time_left_seconds=10,
        current_step="최종 검증 중"
    )

    # 6. 완료는 웹훅으로 전송
    # POST /videos/webhook/processing-completed
```

### Node.js 예제

```javascript
const redis = require('redis');

// Redis 클라이언트 생성
const redisClient = redis.createClient({
    host: 'localhost',
    port: 6379
});

async function sendProgressUpdate({
    videoId,
    aiJobId,
    progressPercentage,
    estimatedTimeLeftSeconds = null,
    currentStep = null
}) {
    const message = {
        video_id: videoId,
        ai_job_id: aiJobId,
        progress_percentage: progressPercentage,
        estimated_time_left_seconds: estimatedTimeLeftSeconds,
        current_step: currentStep,
        timestamp: new Date().toISOString()
    };

    await redisClient.publish(
        'video:progress:updates',
        JSON.stringify(message)
    );

    console.log(`Progress update sent: video_id=${videoId}, progress=${progressPercentage}%`);
}

// 사용 예시
async function processVideo(videoId, aiJobId) {
    // 처리 시작 (0%)
    await sendProgressUpdate({
        videoId: videoId,
        aiJobId: aiJobId,
        progressPercentage: 0,
        estimatedTimeLeftSeconds: 300,
        currentStep: '영상 분석 중'
    });

    // 진행률 30%
    await sendProgressUpdate({
        videoId: videoId,
        aiJobId: aiJobId,
        progressPercentage: 30,
        estimatedTimeLeftSeconds: 210,
        currentStep: 'AI 처리 중'
    });

    // ... 나머지 진행률 업데이트
}
```

---

## 📋 메시지 형식 상세

### JSON 스키마

```json
{
  "video_id": 123,                          // 필수: 비디오 ID (Long)
  "ai_job_id": "ai-job-abc-123",            // 선택: AI 작업 ID (String)
  "progress_percentage": 45,                // 필수: 진행률 0-100 (Integer)
  "estimated_time_left_seconds": 180,       // 선택: 예상 남은 시간 (초, Integer)
  "current_step": "AI 처리 중",              // 선택: 현재 처리 단계 (String)
  "timestamp": "2025-11-20T14:30:00.123Z"   // 선택: 타임스탬프 (ISO-8601)
}
```

### 필드 설명

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `video_id` | Long | ✅ | 비디오 ID (백엔드에서 조회에 사용) |
| `ai_job_id` | String | ❌ | AI 작업 ID (추적용) |
| `progress_percentage` | Integer | ✅ | 진행률 (0-100 범위) |
| `estimated_time_left_seconds` | Integer | ❌ | 예상 남은 시간 (초) |
| `current_step` | String | ❌ | 현재 처리 단계 (100자 이내) |
| `timestamp` | String | ❌ | 메시지 생성 시각 (ISO-8601) |

---

## 🎯 처리 단계 권장 값

클라이언트에 표시할 처리 단계 메시지 예시:

| 진행률 | current_step | 설명 |
|-------|--------------|------|
| 0% | "대기 중" | 처리 시작 대기 |
| 0-10% | "영상 분석 중" | 메타데이터 추출 |
| 10-40% | "AI 처리 중" | AI 모델 실행 |
| 40-70% | "AI 처리 중" | 모델 후처리 |
| 70-90% | "인코딩 중" | 영상 인코딩 |
| 90-95% | "최종 검증 중" | 품질 검사 |
| 95-99% | "업로드 준비 중" | S3 업로드 준비 |

---

## ⚙️ 구현 시 주의사항

### 1️⃣ 전송 주기

**권장: 10-30초마다 전송**

```python
import time

def process_video_with_progress(video_id: int):
    total_steps = 100

    for step in range(0, total_steps + 1, 5):  # 5% 단위
        # 실제 처리 로직
        do_processing_step()

        # 진행률 전송
        send_progress_update(
            video_id=video_id,
            progress_percentage=step,
            estimated_time_left_seconds=calculate_eta()
        )

        # 10초 대기 (너무 자주 전송하지 않음)
        time.sleep(10)
```

**주의:**
- ❌ 1초마다 전송 → Redis/DB 부하 증가
- ❌ 1% 단위로 전송 → 불필요한 트래픽
- ✅ 10-30초 또는 5-10% 단위로 전송

### 2️⃣ 진행률 검증

```python
def send_progress_update(video_id: int, progress_percentage: int, **kwargs):
    # 진행률 범위 검증
    if not 0 <= progress_percentage <= 100:
        raise ValueError(f"Invalid progress: {progress_percentage}% (must be 0-100)")

    # 메시지 전송
    # ...
```

### 3️⃣ 에러 처리

```python
def send_progress_update(video_id: int, progress_percentage: int, **kwargs):
    try:
        redis_client.publish(
            "video:progress:updates",
            json.dumps(message)
        )
    except redis.RedisError as e:
        # Redis 연결 실패 시 로그만 남기고 계속 진행
        # 진행률 전송 실패가 전체 처리를 멈추면 안 됨
        print(f"Warning: Failed to send progress update: {e}")
```

### 4️⃣ 100% 완료 시

**중요: 100% 완료는 Redis가 아닌 웹훅으로 전송하세요!**

```python
def process_video(video_id: int):
    # 진행률 95%까지만 Redis로 전송
    send_progress_update(video_id, 95, current_step="최종 검증 중")

    # 처리 완료 후 웹훅 호출
    requests.post(
        f"{BACKEND_URL}/videos/webhook/processing-completed",
        json={
            "video_id": video_id,
            "ai_job_id": ai_job_id,
            "processed_s3_key": "processed/...",
            # ... 기타 완료 정보
        }
    )
```

**이유:**
- 웹훅은 재시도 가능 (Redis Pub/Sub는 재시도 불가)
- 완료 처리는 트랜잭션 필요 (DB 업데이트 + 알림)
- 진행률은 손실되어도 괜찮지만, 완료 이벤트는 손실되면 안 됨

---

## 🧪 테스트 방법

### 1️⃣ Redis CLI로 테스트

```bash
# 터미널 1: Redis 구독 (백엔드 역할)
redis-cli
127.0.0.1:6379> SUBSCRIBE video:progress:updates

# 터미널 2: 메시지 발행 (AI 서버 역할)
redis-cli
127.0.0.1:6379> PUBLISH video:progress:updates '{"video_id":123,"progress_percentage":50,"current_step":"AI 처리 중","timestamp":"2025-11-20T14:30:00Z"}'
```

### 2️⃣ Python 스크립트로 테스트

```python
import redis
import json
import time

redis_client = redis.Redis(host='localhost', port=6379, decode_responses=True)

# 진행률 시뮬레이션
video_id = 123

for progress in range(0, 101, 10):
    message = {
        "video_id": video_id,
        "progress_percentage": progress,
        "estimated_time_left_seconds": (100 - progress) * 3,
        "current_step": f"처리 중 ({progress}%)",
        "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    }

    redis_client.publish("video:progress:updates", json.dumps(message))
    print(f"Sent: {progress}%")
    time.sleep(2)  # 2초마다 전송
```

---

## 📱 클라이언트 수신 예시

클라이언트에서 SSE로 진행률을 받는 예제:

### JavaScript (브라우저)

```javascript
// SSE 연결
const eventSource = new EventSource('/notifications/subscribe');

// 진행률 업데이트 수신
eventSource.addEventListener('message', (event) => {
    const data = JSON.parse(event.data);

    // 진행률 메시지인지 확인
    if (data.type === 'progress') {
        updateProgressBar(
            data.videoId,
            data.progressPercentage,
            data.currentStep,
            data.estimatedTimeLeftSeconds
        );
    }
});

function updateProgressBar(videoId, percentage, step, timeLeft) {
    const progressBar = document.getElementById(`progress-${videoId}`);
    const statusText = document.getElementById(`status-${videoId}`);

    // 진행률 바 업데이트
    progressBar.style.width = `${percentage}%`;
    progressBar.textContent = `${percentage}%`;

    // 상태 텍스트 업데이트
    if (step) {
        statusText.textContent = step;
    }

    // 예상 시간 표시
    if (timeLeft) {
        const minutes = Math.floor(timeLeft / 60);
        const seconds = timeLeft % 60;
        statusText.textContent += ` (약 ${minutes}분 ${seconds}초 남음)`;
    }
}
```

---

## 🔍 트러블슈팅

### Q1: Redis 연결이 안 됩니다

**확인사항:**
- Redis 서버가 실행 중인지 확인: `redis-cli ping`
- 네트워크 방화벽 확인
- Redis 연결 정보 확인 (host, port)

### Q2: 메시지를 보냈는데 클라이언트에 안 옵니다

**확인사항:**
1. Redis 채널 이름 확인: `video:progress:updates` (정확히 일치해야 함)
2. 백엔드 로그 확인: `VideoProgressRedisListener`에서 메시지 수신 로그
3. 클라이언트 SSE 연결 확인: `/notifications/subscribe`에 연결되어 있는지

### Q3: 진행률이 100%가 안 됩니다

**해결:**
- 100% 완료는 **웹훅**으로 전송하세요
- Redis는 95%까지만 사용
- POST `/videos/webhook/processing-completed`로 완료 알림

### Q4: 메시지 형식 오류가 납니다

**해결:**
- JSON 형식이 올바른지 확인
- 필수 필드 포함 여부 확인: `video_id`, `progress_percentage`
- 진행률 범위 확인: 0-100

---

## 📊 성능 최적화

### 1️⃣ 배치 처리

여러 비디오를 동시에 처리할 때:

```python
# ❌ 나쁜 예: 각 비디오마다 개별 연결
for video_id in video_ids:
    redis_client = redis.Redis()  # 매번 새 연결
    redis_client.publish(...)

# ✅ 좋은 예: 연결 재사용
redis_client = redis.Redis()
for video_id in video_ids:
    redis_client.publish(...)
```

### 2️⃣ Pipeline 사용

```python
import redis

redis_client = redis.Redis()
pipe = redis_client.pipeline()

# 여러 메시지를 한 번에 전송
for i in range(10):
    message = {"video_id": i, "progress_percentage": 50}
    pipe.publish("video:progress:updates", json.dumps(message))

pipe.execute()
```

---

## 📞 문의

문제가 발생하거나 질문이 있으면:
- GitHub Issues: [프로젝트 Issues](https://github.com/...)
- 백엔드 팀: backend@example.com

---

**작성일**: 2025-11-20
**버전**: 1.0.0
