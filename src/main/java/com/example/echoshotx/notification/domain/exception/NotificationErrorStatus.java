package com.example.echoshotx.notification.domain.exception;

import com.example.echoshotx.shared.exception.common.BaseCode;
import com.example.echoshotx.shared.exception.common.Reason;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import static org.springframework.http.HttpStatus.*;

@Getter
@AllArgsConstructor
public enum NotificationErrorStatus implements BaseCode {
    // Notification errors (4400-4450 range)
    NOTIFICATION_NOT_FOUND(NOT_FOUND, 4400, "알림을 찾을 수 없습니다"),
    NOTIFICATION_ACCESS_DENIED(FORBIDDEN, 4401, "알림에 접근할 권한이 없습니다"),
    NOTIFICATION_ALREADY_READ(BAD_REQUEST, 4402, "이미 읽은 알림입니다"),
    NOTIFICATION_SEND_FAILED(INTERNAL_SERVER_ERROR, 4403, "알림 전송에 실패했습니다"),
    SSE_CONNECTION_FAILED(INTERNAL_SERVER_ERROR, 4404, "SSE 연결에 실패했습니다"),
    INVALID_NOTIFICATION_TYPE(BAD_REQUEST, 4405, "잘못된 알림 타입입니다"),
    NOTIFICATION_RETRY_EXCEEDED(BAD_REQUEST, 4406, "알림 재시도 횟수를 초과했습니다");

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
