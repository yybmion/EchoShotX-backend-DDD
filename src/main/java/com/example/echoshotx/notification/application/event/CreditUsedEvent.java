package com.example.echoshotx.notification.application.event;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 크레딧 사용 이벤트
 */
@Getter
public class CreditUsedEvent {
    private final Long memberId;
    private final Long creditHistoryId;
    private final Integer amount;
    private final Long videoId;
    private final LocalDateTime occurredAt;

    public CreditUsedEvent(Long memberId, Long creditHistoryId, Integer amount, Long videoId) {
        this.memberId = memberId;
        this.creditHistoryId = creditHistoryId;
        this.amount = amount;
        this.videoId = videoId;
        this.occurredAt = LocalDateTime.now();
    }
}
