package com.example.echoshotx.notification.domain.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationStatus {
    PENDING("전송 대기"),
    SENT("전송 완료"),
    FAILED("전송 실패");

    private final String description;
}
