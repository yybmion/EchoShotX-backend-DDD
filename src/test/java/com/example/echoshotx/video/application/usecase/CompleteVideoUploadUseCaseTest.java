package com.example.echoshotx.video.application.usecase;

import com.example.echoshotx.credit.application.service.CreditService;
import com.example.echoshotx.credit.domain.entity.CreditHistory;
import com.example.echoshotx.credit.domain.entity.TransactionType;
import com.example.echoshotx.member.domain.entity.Member;
import com.example.echoshotx.member.domain.entity.Role;
import com.example.echoshotx.video.application.adaptor.VideoAdaptor;
import com.example.echoshotx.video.application.service.VideoService;
import com.example.echoshotx.video.domain.entity.ProcessingType;
import com.example.echoshotx.video.domain.entity.Video;
import com.example.echoshotx.video.domain.entity.VideoStatus;
import com.example.echoshotx.video.domain.vo.VideoFile;
import com.example.echoshotx.video.domain.vo.VideoMetadata;
import com.example.echoshotx.video.presentation.dto.request.CompleteUploadRequest;
import com.example.echoshotx.video.presentation.dto.response.CompleteUploadResponse;
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
 * CompleteVideoUploadUseCase 단위 테스트
 *
 * 테스트 범위:
 * 1. 업로드 완료 워크플로우 검증
 * 2. 크레딧 차감 검증
 * 3. SQS 메시지 전송 및 큐잉 검증
 * 4. 이벤트 발행 및 알림 트리거 검증
 * 5. 메서드 호출 순서 검증
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CompleteVideoUploadUseCase 테스트")
class CompleteVideoUploadUseCaseTest {

    @Mock
    private VideoAdaptor videoAdaptor;

    @Mock
    private VideoService videoService;

    @Mock
    private CreditService creditService;

    @InjectMocks
    private CompleteVideoUploadUseCase completeVideoUploadUseCase;

    private Member testMember;
    private Video testVideo;
    private Video uploadCompletedVideo;
    private Video queuedVideo;
    private CompleteUploadRequest testRequest;
    private CreditHistory testCreditHistory;

    @BeforeEach
    void setUp() {
        // Given: 테스트 데이터 준비
        testMember = Member.builder()
                .id(1L)
                .username("testuser@example.com")
                .nickname("테스트유저")
                .email("testuser@example.com")
                .role(Role.USER)
                .currentCredits(1000)
                .build();

        testRequest = new CompleteUploadRequest(
                120.5,      // durationSeconds
                1920,       // width
                1080,       // height
                "h264",     // codec
                5000000L,   // bitrate
                30.0        // frameRate
        );

        // 초기 PENDING_UPLOAD 상태의 비디오
        testVideo = Video.builder()
                .id(100L)
                .memberId(1L)
                .originalFile(VideoFile.builder()
                        .fileName("test-video.mp4")
                        .fileSizeBytes(10_000_000L)
                        .s3Key("videos/1/original/upload-id/20250101120000_test-video.mp4")
                        .build())
                .status(VideoStatus.PENDING_UPLOAD)
                .processingType(ProcessingType.BASIC_ENHANCEMENT)
                .uploadId("upload-id-123")
                .retryCount(0)
                .build();

        // 업로드 완료된 비디오 (UPLOAD_COMPLETED 상태)
        uploadCompletedVideo = Video.builder()
                .id(100L)
                .memberId(1L)
                .originalFile(VideoFile.builder()
                        .fileName("test-video.mp4")
                        .fileSizeBytes(10_000_000L)
                        .s3Key("videos/1/original/upload-id/20250101120000_test-video.mp4")
                        .build())
                .status(VideoStatus.UPLOAD_COMPLETED)
                .processingType(ProcessingType.BASIC_ENHANCEMENT)
                .uploadId("upload-id-123")
                .originalMetadata(VideoMetadata.builder()
                        .durationSeconds(120.5)
                        .width(1920)
                        .height(1080)
                        .codec("h264")
                        .bitrate(5000000L)
                        .frameRate(30.0)
                        .build())
                .retryCount(0)
                .build();

        // 큐에 등록된 비디오 (QUEUED 상태)
        queuedVideo = Video.builder()
                .id(100L)
                .memberId(1L)
                .originalFile(VideoFile.builder()
                        .fileName("test-video.mp4")
                        .fileSizeBytes(10_000_000L)
                        .s3Key("videos/1/original/upload-id/20250101120000_test-video.mp4")
                        .build())
                .status(VideoStatus.QUEUED)
                .processingType(ProcessingType.BASIC_ENHANCEMENT)
                .uploadId("upload-id-123")
                .sqsMessageId("sqs-msg-uuid-12345")
                .originalMetadata(VideoMetadata.builder()
                        .durationSeconds(120.5)
                        .width(1920)
                        .height(1080)
                        .codec("h264")
                        .bitrate(5000000L)
                        .frameRate(30.0)
                        .build())
                .retryCount(0)
                .build();

        // 크레딧 사용 내역
        testCreditHistory = CreditHistory.builder()
                .id(1L)
                .memberId(1L)
                .videoId(100L)
                .transactionType(TransactionType.USAGE)
                .amount(-100)
                .description("Video processing for BASIC_ENHANCEMENT")
                .build();
    }

    @Nested
    @DisplayName("execute 메서드 테스트")
    class ExecuteTest {

        @Test
        @DisplayName("성공: 업로드 완료 워크플로우 정상 실행")
        void execute_Success_WhenValidRequest() {
            // Given
            given(videoAdaptor.queryById(100L)).willReturn(testVideo);
            given(videoService.completeUpload(eq(testVideo), any(VideoMetadata.class)))
                    .willReturn(uploadCompletedVideo);
            given(creditService.useCreditsForVideoProcessing(eq(uploadCompletedVideo), eq(ProcessingType.BASIC_ENHANCEMENT)))
                    .willReturn(testCreditHistory);
            given(videoService.enqueueForProcessing(eq(uploadCompletedVideo), anyString()))
                    .willReturn(queuedVideo);

            // When
            CompleteUploadResponse response = completeVideoUploadUseCase.execute(100L, testRequest, testMember);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getVideoId()).isEqualTo(100L);
            assertThat(response.getStatus()).isEqualTo(VideoStatus.QUEUED);
            assertThat(response.getMessage()).isEqualTo("영상 처리가 시작되었습니다");
            assertThat(response.getSqsMessageId()).isEqualTo("sqs-msg-uuid-12345");

            verify(videoAdaptor).queryById(100L);
            verify(videoService).completeUpload(eq(testVideo), any(VideoMetadata.class));
            verify(creditService).useCreditsForVideoProcessing(eq(uploadCompletedVideo), eq(ProcessingType.BASIC_ENHANCEMENT));
            verify(videoService).enqueueForProcessing(eq(uploadCompletedVideo), anyString());
        }

        @Test
        @DisplayName("성공: VideoService.completeUpload가 올바른 메타데이터로 호출됨")
        void execute_CallsCompleteUpload_WithCorrectMetadata() {
            // Given
            given(videoAdaptor.queryById(100L)).willReturn(testVideo);
            given(videoService.completeUpload(eq(testVideo), any(VideoMetadata.class)))
                    .willReturn(uploadCompletedVideo);
            given(creditService.useCreditsForVideoProcessing(any(), any()))
                    .willReturn(testCreditHistory);
            given(videoService.enqueueForProcessing(any(), anyString()))
                    .willReturn(queuedVideo);

            ArgumentCaptor<VideoMetadata> metadataCaptor = ArgumentCaptor.forClass(VideoMetadata.class);

            // When
            completeVideoUploadUseCase.execute(100L, testRequest, testMember);

            // Then
            verify(videoService).completeUpload(eq(testVideo), metadataCaptor.capture());

            VideoMetadata capturedMetadata = metadataCaptor.getValue();
            assertThat(capturedMetadata.getDurationSeconds()).isEqualTo(120.5);
            assertThat(capturedMetadata.getWidth()).isEqualTo(1920);
            assertThat(capturedMetadata.getHeight()).isEqualTo(1080);
            assertThat(capturedMetadata.getCodec()).isEqualTo("h264");
            assertThat(capturedMetadata.getBitrate()).isEqualTo(5000000L);
            assertThat(capturedMetadata.getFrameRate()).isEqualTo(30.0);
        }

        @Test
        @DisplayName("성공: CreditService가 올바른 처리 타입으로 호출됨")
        void execute_CallsCreditService_WithCorrectProcessingType() {
            // Given
            given(videoAdaptor.queryById(100L)).willReturn(testVideo);
            given(videoService.completeUpload(any(), any())).willReturn(uploadCompletedVideo);
            given(creditService.useCreditsForVideoProcessing(
                    eq(uploadCompletedVideo), eq(ProcessingType.BASIC_ENHANCEMENT)))
                    .willReturn(testCreditHistory);
            given(videoService.enqueueForProcessing(any(), anyString())).willReturn(queuedVideo);

            // When
            completeVideoUploadUseCase.execute(100L, testRequest, testMember);

            // Then
            verify(creditService).useCreditsForVideoProcessing(
                    eq(uploadCompletedVideo),
                    eq(ProcessingType.BASIC_ENHANCEMENT)
            );
        }

        @Test
        @DisplayName("성공: VideoService.enqueueForProcessing에 SQS 메시지 ID가 전달됨")
        void execute_CallsEnqueueForProcessing_WithSqsMessageId() {
            // Given
            given(videoAdaptor.queryById(100L)).willReturn(testVideo);
            given(videoService.completeUpload(any(), any())).willReturn(uploadCompletedVideo);
            given(creditService.useCreditsForVideoProcessing(any(), any())).willReturn(testCreditHistory);
            given(videoService.enqueueForProcessing(eq(uploadCompletedVideo), anyString()))
                    .willReturn(queuedVideo);

            ArgumentCaptor<String> sqsMessageIdCaptor = ArgumentCaptor.forClass(String.class);

            // When
            completeVideoUploadUseCase.execute(100L, testRequest, testMember);

            // Then
            verify(videoService).enqueueForProcessing(
                    eq(uploadCompletedVideo),
                    sqsMessageIdCaptor.capture()
            );

            String capturedSqsMessageId = sqsMessageIdCaptor.getValue();
            assertThat(capturedSqsMessageId).isNotNull();
            assertThat(capturedSqsMessageId).isNotEmpty();
            // Mock SQS는 UUID 형식이어야 함
            assertThat(capturedSqsMessageId).matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");
        }

        @Test
        @DisplayName("성공: 메서드 호출 순서가 올바름")
        void execute_CallsMethodsInCorrectOrder() {
            // Given
            given(videoAdaptor.queryById(100L)).willReturn(testVideo);
            given(videoService.completeUpload(any(), any())).willReturn(uploadCompletedVideo);
            given(creditService.useCreditsForVideoProcessing(any(), any())).willReturn(testCreditHistory);
            given(videoService.enqueueForProcessing(any(), anyString())).willReturn(queuedVideo);

            // When
            completeVideoUploadUseCase.execute(100L, testRequest, testMember);

            // Then
            var inOrder = inOrder(videoAdaptor, videoService, creditService);
            inOrder.verify(videoAdaptor).queryById(100L);                              // 1. 비디오 조회
            inOrder.verify(videoService).completeUpload(eq(testVideo), any());         // 2. 업로드 완료 처리
            inOrder.verify(creditService).useCreditsForVideoProcessing(any(), any());  // 3. 크레딧 차감
            inOrder.verify(videoService).enqueueForProcessing(any(), anyString());     // 4. 큐에 등록 및 이벤트 발행
        }
    }

    @Nested
    @DisplayName("다양한 처리 타입 테스트")
    class DifferentProcessingTypesTest {

        @Test
        @DisplayName("성공: AI_UPSCALING 처리 타입으로 업로드 완료")
        void execute_Success_WithAiUpscaling() {
            // Given
            Video aiVideo = Video.builder()
                    .id(100L)
                    .memberId(1L)
                    .originalFile(testVideo.getOriginalFile())
                    .status(VideoStatus.PENDING_UPLOAD)
                    .processingType(ProcessingType.AI_UPSCALING)
                    .uploadId("upload-id-123")
                    .retryCount(0)
                    .build();

            Video aiUploadCompleted = Video.builder()
                    .id(100L)
                    .memberId(1L)
                    .originalFile(testVideo.getOriginalFile())
                    .status(VideoStatus.UPLOAD_COMPLETED)
                    .processingType(ProcessingType.AI_UPSCALING)
                    .uploadId("upload-id-123")
                    .retryCount(0)
                    .build();

            Video aiQueued = Video.builder()
                    .id(100L)
                    .memberId(1L)
                    .originalFile(testVideo.getOriginalFile())
                    .status(VideoStatus.QUEUED)
                    .processingType(ProcessingType.AI_UPSCALING)
                    .uploadId("upload-id-123")
                    .sqsMessageId("sqs-msg-uuid")
                    .retryCount(0)
                    .build();

            given(videoAdaptor.queryById(100L)).willReturn(aiVideo);
            given(videoService.completeUpload(any(), any())).willReturn(aiUploadCompleted);
            given(creditService.useCreditsForVideoProcessing(eq(aiUploadCompleted), eq(ProcessingType.AI_UPSCALING)))
                    .willReturn(testCreditHistory);
            given(videoService.enqueueForProcessing(any(), anyString())).willReturn(aiQueued);

            // When
            CompleteUploadResponse response = completeVideoUploadUseCase.execute(100L, testRequest, testMember);

            // Then
            assertThat(response).isNotNull();
            verify(creditService).useCreditsForVideoProcessing(
                    eq(aiUploadCompleted),
                    eq(ProcessingType.AI_UPSCALING)
            );
        }
    }

    @Nested
    @DisplayName("다양한 비디오 메타데이터 테스트")
    class DifferentVideoMetadataTest {

        @Test
        @DisplayName("성공: 4K 해상도 비디오 업로드 완료")
        void execute_Success_With4KResolution() {
            // Given
            CompleteUploadRequest request4K = new CompleteUploadRequest(
                    180.0,      // 3분
                    3840,       // 4K width
                    2160,       // 4K height
                    "h265",
                    15000000L,  // 15Mbps
                    60.0        // 60fps
            );

            given(videoAdaptor.queryById(100L)).willReturn(testVideo);
            given(videoService.completeUpload(any(), any())).willReturn(uploadCompletedVideo);
            given(creditService.useCreditsForVideoProcessing(any(), any())).willReturn(testCreditHistory);
            given(videoService.enqueueForProcessing(any(), anyString())).willReturn(queuedVideo);

            ArgumentCaptor<VideoMetadata> metadataCaptor = ArgumentCaptor.forClass(VideoMetadata.class);

            // When
            completeVideoUploadUseCase.execute(100L, request4K, testMember);

            // Then
            verify(videoService).completeUpload(any(), metadataCaptor.capture());

            VideoMetadata metadata = metadataCaptor.getValue();
            assertThat(metadata.getWidth()).isEqualTo(3840);
            assertThat(metadata.getHeight()).isEqualTo(2160);
            assertThat(metadata.getCodec()).isEqualTo("h265");
            assertThat(metadata.getFrameRate()).isEqualTo(60.0);
        }

        @Test
        @DisplayName("성공: HD 해상도 비디오 업로드 완료")
        void execute_Success_WithHDResolution() {
            // Given
            CompleteUploadRequest requestHD = new CompleteUploadRequest(
                    60.0,       // 1분
                    1280,       // HD width
                    720,        // HD height
                    "h264",
                    3000000L,   // 3Mbps
                    24.0        // 24fps
            );

            given(videoAdaptor.queryById(100L)).willReturn(testVideo);
            given(videoService.completeUpload(any(), any())).willReturn(uploadCompletedVideo);
            given(creditService.useCreditsForVideoProcessing(any(), any())).willReturn(testCreditHistory);
            given(videoService.enqueueForProcessing(any(), anyString())).willReturn(queuedVideo);

            ArgumentCaptor<VideoMetadata> metadataCaptor = ArgumentCaptor.forClass(VideoMetadata.class);

            // When
            completeVideoUploadUseCase.execute(100L, requestHD, testMember);

            // Then
            verify(videoService).completeUpload(any(), metadataCaptor.capture());

            VideoMetadata metadata = metadataCaptor.getValue();
            assertThat(metadata.getWidth()).isEqualTo(1280);
            assertThat(metadata.getHeight()).isEqualTo(720);
            assertThat(metadata.getDurationSeconds()).isEqualTo(60.0);
        }
    }

    @Nested
    @DisplayName("Response 매핑 테스트")
    class ResponseMappingTest {

        @Test
        @DisplayName("성공: Response에 모든 필수 필드가 올바르게 매핑됨")
        void response_ContainsAllRequiredFields() {
            // Given
            given(videoAdaptor.queryById(100L)).willReturn(testVideo);
            given(videoService.completeUpload(any(), any())).willReturn(uploadCompletedVideo);
            given(creditService.useCreditsForVideoProcessing(any(), any())).willReturn(testCreditHistory);
            given(videoService.enqueueForProcessing(any(), anyString())).willReturn(queuedVideo);

            // When
            CompleteUploadResponse response = completeVideoUploadUseCase.execute(100L, testRequest, testMember);

            // Then
            assertThat(response.getVideoId()).isNotNull();
            assertThat(response.getStatus()).isNotNull();
            assertThat(response.getMessage()).isNotNull();
            assertThat(response.getSqsMessageId()).isNotNull();
        }

        @Test
        @DisplayName("성공: Response 상태가 QUEUED로 설정됨")
        void response_StatusIsQueued() {
            // Given
            given(videoAdaptor.queryById(100L)).willReturn(testVideo);
            given(videoService.completeUpload(any(), any())).willReturn(uploadCompletedVideo);
            given(creditService.useCreditsForVideoProcessing(any(), any())).willReturn(testCreditHistory);
            given(videoService.enqueueForProcessing(any(), anyString())).willReturn(queuedVideo);

            // When
            CompleteUploadResponse response = completeVideoUploadUseCase.execute(100L, testRequest, testMember);

            // Then
            assertThat(response.getStatus()).isEqualTo(VideoStatus.QUEUED);
        }

        @Test
        @DisplayName("성공: Response 메시지가 올바름")
        void response_MessageIsCorrect() {
            // Given
            given(videoAdaptor.queryById(100L)).willReturn(testVideo);
            given(videoService.completeUpload(any(), any())).willReturn(uploadCompletedVideo);
            given(creditService.useCreditsForVideoProcessing(any(), any())).willReturn(testCreditHistory);
            given(videoService.enqueueForProcessing(any(), anyString())).willReturn(queuedVideo);

            // When
            CompleteUploadResponse response = completeVideoUploadUseCase.execute(100L, testRequest, testMember);

            // Then
            assertThat(response.getMessage()).isEqualTo("영상 처리가 시작되었습니다");
        }
    }
}
