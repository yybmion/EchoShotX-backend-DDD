# AI 서버 웹훅 연동 가이드

## 📌 개요

AI 서버가 영상 처리 완료 후 Spring 백엔드로 결과를 전송하는 웹훅 엔드포인트 연동 가이드입니다.

---

## 🔐 인증 (API Key)

### API Key 설정

웹훅 호출 시 **반드시** `X-API-Key` 헤더를 포함해야 합니다.

```bash
X-API-Key: your-secret-api-key-here
```

### API Key 발급

Spring 서버의 `application.yml`에 설정된 API Key를 사용합니다:

```yaml
ai:
  server:
    webhook:
      api-key: ${AI_WEBHOOK_API_KEY}  # 환경변수로 설정
```

### 인증 실패 시

- **401 Unauthorized**: API Key가 누락되거나 잘못된 경우
- **500 Internal Server Error**: 서버 설정 오류

---

## 🎯 엔드포인트

### 1. 처리 완료 웹훅

**URL**: `POST /videos/webhook/processing-completed`

**설명**: AI 서버에서 영상 처리 완료 시 호출합니다.

#### 요청 헤더

```http
POST /videos/webhook/processing-completed HTTP/1.1
Host: spring-server.example.com
Content-Type: application/json
X-API-Key: your-secret-api-key-here
```

#### 요청 본문 (JSON)

```json
{
  "videoId": 123,
  "aiJobId": "ai-job-uuid-12345",
  "requestId": "unique-request-id-67890",  // 선택사항: 멱등성 보장용
  "processedS3Key": "processed/videos/output.mp4",
  "processedFileSizeBytes": 10485760,
  "processedDurationSeconds": 120.5,
  "processedWidth": 1920,
  "processedHeight": 1080,
  "processedCodec": "h264",
  "processedBitrate": 5000000,
  "processedFrameRate": 30.0,
  "thumbnailS3Key": "thumbnails/video_123_thumb.jpg"  // 선택사항
}
```

#### 필수 필드

| 필드 | 타입 | 설명 |
|------|------|------|
| `videoId` | Long | Spring 서버의 Video ID |
| `aiJobId` | String | AI 서버의 작업 ID (멱등성 검증용) |
| `processedS3Key` | String | 처리된 영상의 S3 키 |
| `processedFileSizeBytes` | Long | 처리된 영상 파일 크기 (바이트) |

#### 선택 필드

| 필드 | 타입 | 설명 |
|------|------|------|
| `requestId` | String | 멱등성 보장을 위한 요청 ID |
| `processedDurationSeconds` | Double | 영상 길이 (초) |
| `processedWidth` | Integer | 영상 너비 (픽셀) |
| `processedHeight` | Integer | 영상 높이 (픽셀) |
| `processedCodec` | String | 비디오 코덱 (예: h264, h265) |
| `processedBitrate` | Long | 비트레이트 (bps) |
| `processedFrameRate` | Double | 프레임레이트 (fps) |
| `thumbnailS3Key` | String | 썸네일 S3 키 |

#### 응답

**성공 (200 OK)**:
```json
{
  "isSuccess": true,
  "code": "OK",
  "message": "요청이 성공적으로 처리되었습니다.",
  "result": null
}
```

**실패 (400 Bad Request)**: 요청 데이터 검증 실패
```json
{
  "isSuccess": false,
  "code": "BAD_REQUEST",
  "message": "videoId는 필수입니다.",
  "result": null
}
```

**실패 (401 Unauthorized)**: API Key 인증 실패
```json
{
  "isSuccess": false,
  "code": "UNAUTHORIZED",
  "message": "Invalid API Key",
  "result": null
}
```

**실패 (409 Conflict)**: 멱등성 충돌 (이미 처리됨)
```json
{
  "isSuccess": false,
  "code": "CONFLICT",
  "message": "Video already completed",
  "result": null
}
```

---

### 2. 처리 실패 웹훅

**URL**: `POST /videos/webhook/processing-failed`

**설명**: AI 서버에서 영상 처리 실패 시 호출합니다.

#### 요청 헤더

```http
POST /videos/webhook/processing-failed HTTP/1.1
Host: spring-server.example.com
Content-Type: application/json
X-API-Key: your-secret-api-key-here
```

#### 요청 본문 (JSON)

```json
{
  "videoId": 123,
  "aiJobId": "ai-job-uuid-12345",
  "requestId": "unique-request-id-67890",  // 선택사항
  "errorMessage": "영상 인코딩 실패: 지원하지 않는 코덱",
  "errorCode": "ENCODING_ERROR"
}
```

#### 필수 필드

| 필드 | 타입 | 설명 |
|------|------|------|
| `videoId` | Long | Spring 서버의 Video ID |
| `aiJobId` | String | AI 서버의 작업 ID |
| `errorMessage` | String | 실패 사유 (사용자에게 표시됨) |

#### 선택 필드

| 필드 | 타입 | 설명 |
|------|------|------|
| `requestId` | String | 멱등성 보장을 위한 요청 ID |
| `errorCode` | String | 에러 코드 (내부 분류용) |

#### 응답

성공/실패 응답은 처리 완료 웹훅과 동일합니다.

---

## 🔄 멱등성 (Idempotency)

### 멱등성 보장 방식

1. **aiJobId 기반**: 같은 `aiJobId`에 대해 중복 웹훅이 오면 무시됩니다.
2. **상태 기반**: 이미 `COMPLETED` 또는 `FAILED` 상태인 영상은 재처리되지 않습니다.
3. **requestId 사용 (권장)**: `requestId`를 포함하면 더 명확한 멱등성 보장이 가능합니다.

### 재시도 시 주의사항

- **같은 `requestId` 사용**: 재시도 시 동일한 `requestId`를 사용하면 중복 처리가 방지됩니다.
- **aiJobId 검증**: Spring 서버는 `aiJobId`가 일치하는지 검증합니다.
- **로그 확인**: 중복 웹훅은 로그에 `WARN` 레벨로 기록됩니다.

### 멱등성 응답

중복 웹훅이 감지되면:
- **HTTP 200 OK** 반환 (에러가 아님)
- 로그에 `Skipping duplicate webhook` 메시지 기록
- 클라이언트에게는 알림을 보내지 않음

---

## 🔁 재시도 정책 (권장)

### AI 서버 측 재시도 로직

1. **네트워크 오류 (5xx, 타임아웃)**:
   - 최대 3회 재시도
   - 지수 백오프: 2초 → 4초 → 8초

2. **클라이언트 오류 (4xx)**:
   - **400, 404, 409**: 재시도 하지 않음 (데이터 오류)
   - **401**: API Key 확인 후 재시도
   - **429 (Rate Limit)**: 1분 후 재시도

3. **타임아웃**:
   - 연결 타임아웃: 30초
   - 읽기 타임아웃: 60초

### 예제 코드 (Python)

```python
import requests
import time

def send_webhook_with_retry(url, payload, api_key, max_retries=3):
    headers = {
        "Content-Type": "application/json",
        "X-API-Key": api_key
    }

    for attempt in range(max_retries):
        try:
            response = requests.post(
                url,
                json=payload,
                headers=headers,
                timeout=(30, 60)  # (connect, read) timeout
            )

            # 성공
            if response.status_code == 200:
                print(f"Webhook sent successfully: {payload['videoId']}")
                return True

            # 클라이언트 오류 (재시도 불필요)
            if 400 <= response.status_code < 500:
                print(f"Client error {response.status_code}: {response.text}")
                return False

            # 서버 오류 (재시도)
            print(f"Server error {response.status_code}, retrying...")

        except requests.exceptions.Timeout:
            print(f"Timeout on attempt {attempt + 1}")
        except requests.exceptions.RequestException as e:
            print(f"Request failed: {e}")

        # 지수 백오프
        if attempt < max_retries - 1:
            wait_time = 2 ** attempt
            time.sleep(wait_time)

    print(f"Failed to send webhook after {max_retries} attempts")
    return False
```

---

## 📊 로깅

### Spring 서버 로그

웹훅 처리 시 다음 정보가 로그에 기록됩니다:

**처리 완료**:
```
INFO  Processing completed webhook received: videoId=123, aiJobId=ai-job-uuid-12345, requestId=req-67890, currentStatus=PROCESSING
INFO  Video processing completed successfully: videoId=123, aiJobId=ai-job-uuid-12345, requestId=req-67890
```

**처리 실패**:
```
WARN  Processing failed webhook received: videoId=123, aiJobId=ai-job-uuid-12345, requestId=req-67890, currentStatus=PROCESSING, error=영상 인코딩 실패
INFO  Video processing failed: videoId=123, aiJobId=ai-job-uuid-12345, requestId=req-67890, retryCount=0
INFO  Credits refunded: videoId=123, amount=50, aiJobId=ai-job-uuid-12345, requestId=req-67890
```

**중복 웹훅**:
```
WARN  Video already completed. Skipping duplicate webhook: videoId=123, aiJobId=ai-job-uuid-12345, requestId=req-67890
```

**인증 실패**:
```
WARN  Invalid API Key in webhook request: uri=/videos/webhook/processing-completed, ip=192.168.1.100, providedKey=abcd****5678
```

---

## 🧪 테스트

### cURL 예제

**처리 완료 웹훅**:
```bash
curl -X POST http://localhost:8080/videos/webhook/processing-completed \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-secret-api-key-here" \
  -d '{
    "videoId": 123,
    "aiJobId": "test-job-id",
    "requestId": "test-request-id",
    "processedS3Key": "processed/test.mp4",
    "processedFileSizeBytes": 1048576,
    "processedDurationSeconds": 60.0,
    "processedWidth": 1920,
    "processedHeight": 1080,
    "processedCodec": "h264",
    "processedBitrate": 5000000,
    "processedFrameRate": 30.0
  }'
```

**처리 실패 웹훅**:
```bash
curl -X POST http://localhost:8080/videos/webhook/processing-failed \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-secret-api-key-here" \
  -d '{
    "videoId": 123,
    "aiJobId": "test-job-id",
    "requestId": "test-request-id",
    "errorMessage": "테스트 실패 메시지",
    "errorCode": "TEST_ERROR"
  }'
```

---

## 🚨 에러 처리

### 일반적인 에러와 대응

| HTTP 코드 | 원인 | 대응 방법 |
|-----------|------|----------|
| **400** | 요청 데이터 검증 실패 | 필수 필드 확인, 데이터 타입 확인 |
| **401** | API Key 인증 실패 | API Key 확인, 헤더 이름 확인 (`X-API-Key`) |
| **404** | videoId가 존재하지 않음 | videoId 확인 |
| **409** | 이미 처리된 영상 | 로그 확인, 중복 요청 무시 |
| **500** | 서버 내부 오류 | 로그 확인, 재시도 |

### aiJobId 불일치 에러

```json
{
  "isSuccess": false,
  "code": "BAD_REQUEST",
  "message": "AiJobId mismatch for video 123. Expected: job-abc, Received: job-xyz",
  "result": null
}
```

**원인**: 다른 작업의 웹훅을 잘못 보낸 경우
**대응**: AI 서버에서 videoId와 aiJobId 매핑 확인

---

## 📝 체크리스트

### AI 서버 개발자

- [ ] API Key를 환경변수로 안전하게 관리
- [ ] 모든 웹훅 요청에 `X-API-Key` 헤더 포함
- [ ] `requestId`를 UUID로 생성하여 멱등성 보장
- [ ] 재시도 로직 구현 (5xx 오류, 타임아웃)
- [ ] 타임아웃 설정 (연결: 30초, 읽기: 60초)
- [ ] 웹훅 전송 실패 시 로그 기록
- [ ] SQS 메시지 처리 시 `videoId`와 `aiJobId` 추출
- [ ] S3에 처리 결과 업로드 후 웹훅 호출

### Spring 서버 운영자

- [ ] `application.yml`에 `AI_WEBHOOK_API_KEY` 환경변수 설정
- [ ] API Key를 AI 서버 팀에 안전하게 전달 (암호화된 채널)
- [ ] 웹훅 엔드포인트 방화벽 설정 (AI 서버 IP만 허용)
- [ ] 로그 모니터링 설정 (인증 실패, 중복 요청 등)
- [ ] SSE 알림이 클라이언트에게 정상 전송되는지 확인

---

## 🔗 관련 문서

- [Video API 문서](./VIDEO_API.md)
- [SSE 알림 가이드](./NOTIFICATION_SSE_GUIDE.md)
- [SQS 메시지 포맷](./SQS_MESSAGE_FORMAT.md)

---

## 📞 문의

웹훅 연동 관련 문의는 백엔드 팀에게 연락하세요.
