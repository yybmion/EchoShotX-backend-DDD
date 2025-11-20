package com.example.echoshotx.video.domain.exception;

import com.example.echoshotx.shared.exception.payload.code.BaseCode;
import com.example.echoshotx.shared.exception.payload.code.Reason;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

import static org.springframework.http.HttpStatus.*;

@Getter
@RequiredArgsConstructor
public enum VideoErrorStatus implements BaseCode {

    // Video Error (4301 ~ 4310)
    VIDEO_PROCESSING_FAILED(INTERNAL_SERVER_ERROR, 4301, "비디오 처리에 실패했습니다."),
    VIDEO_NOT_FOUND(NOT_FOUND, 4302, "비디오를 찾을 수 없습니다."),
    VIDEO_UNSUPPORTED_FORMAT(BAD_REQUEST, 4303, "지원되지 않는 비디오 형식입니다."),
    VIDEO_THUMBNAIL_GENERATION_FAILED(INTERNAL_SERVER_ERROR, 4304, "비디오 썸네일 생성에 실패했습니다."),
    VIDEO_METADATA_EXTRACTION_FAILED(INTERNAL_SERVER_ERROR, 4305, "비디오 메타데이터 추출에 실패했습니다."),
    VIDEO_INVALID_STATUS(BAD_REQUEST, 4306, "잘못된 비디오 상태입니다."),
    VIDEO_MEMBER_MISMATCH(BAD_REQUEST, 4307, "비디오 소유자와 요청한 사용자가 일치하지 않습니다."),
    VIDEO_STORAGE_ERROR(INTERNAL_SERVER_ERROR, 4308, "비디오 저장 중 오류가 발생했습니다."),
    VIDEO_INVALID_FILE_NAME(BAD_REQUEST, 4309, "잘못된 비디오 파일 이름입니다."),
    
    // 도메인 규칙 관련 에러 (4310 ~ 4320)
    VIDEO_INVALID_MEMBER_ID(BAD_REQUEST, 4310, "유효하지 않은 회원 ID입니다."),
    VIDEO_EMPTY_FILE(BAD_REQUEST, 4311, "빈 파일은 업로드할 수 없습니다."),
    VIDEO_INVALID_S3_KEY(BAD_REQUEST, 4312, "S3 키가 유효하지 않습니다."),
    VIDEO_INVALID_PROCESSING_TYPE(BAD_REQUEST, 4313, "처리 타입이 지정되지 않았습니다."),
    VIDEO_INVALID_STATUS_TRANSITION(BAD_REQUEST, 4314, "잘못된 상태 전환입니다."),
    VIDEO_SAME_STATUS_TRANSITION(BAD_REQUEST, 4315, "동일한 상태로의 전환은 허용되지 않습니다."),
    VIDEO_INVALID_PROCESSED_KEY(BAD_REQUEST, 4316, "처리된 영상 키가 유효하지 않습니다."),
    VIDEO_NOT_PROCESSED_STATUS(BAD_REQUEST, 4317, "처리 완료된 영상이 아닙니다."),
    VIDEO_INVALID_THUMBNAIL_KEY(BAD_REQUEST, 4318, "썸네일 키가 유효하지 않습니다."),
    
    // 입력 검증 관련 에러 (4320 ~ 4330)
    VIDEO_FILE_TOO_SMALL(BAD_REQUEST, 4320, "파일 크기가 너무 작습니다."),
    VIDEO_FILE_TOO_LARGE(BAD_REQUEST, 4321, "파일 크기가 너무 큽니다."),
    VIDEO_EXTENSION_NOT_FOUND(BAD_REQUEST, 4322, "파일 확장자를 찾을 수 없습니다."),

    // 처리 에러 (4330 ~ 4340)
    VIDEO_NOT_COMPLETED(BAD_REQUEST, 4330, "비디오 처리가 완료되지 않았습니다."),
    VIDEO_PROCESSED_FILE_NOT_EXISTS(BAD_REQUEST, 4331, "처리된 비디오 파일이 존재하지 않습니다."),

    // 진행률 관련 에러 (4340 ~ 4350)
    VIDEO_INVALID_STATUS_FOR_PROGRESS_UPDATE(BAD_REQUEST, 4340, "진행률을 업데이트할 수 없는 상태입니다."),
    VIDEO_INVALID_PROGRESS_PERCENTAGE(BAD_REQUEST, 4341, "진행률은 0에서 100 사이여야 합니다.")
    ;

    private final HttpStatus httpStatus;
    private final Integer code;
    private final String message;

    @Override
    public Reason getReason() {
        return Reason.builder()
                .message(message)
                .code(code)
                .isSuccess(false)
                .build();
    }

    @Override
    public Reason getReasonHttpStatus() {
        return Reason.builder()
                .message(message)
                .code(code)
                .isSuccess(false)
                .httpStatus(httpStatus)
                .build();
    }
}
