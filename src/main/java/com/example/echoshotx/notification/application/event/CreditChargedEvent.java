package com.example.echoshotx.notification.application.event;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 크레딧 충전 이벤트
 */
@Getter
public class CreditChargedEvent {
    private final Long memberId;
    private final Long creditHistoryId;
    private final Integer amount;
    private final LocalDateTime occurredAt;

    public CreditChargedEvent(Long memberId, Long creditHistoryId, Integer amount) {
        this.memberId = memberId;
        this.creditHistoryId = creditHistoryId;
        this.amount = amount;
        this.occurredAt = LocalDateTime.now();
    }
}
