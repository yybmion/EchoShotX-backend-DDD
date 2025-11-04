package com.example.echoshotx.notification.application.event;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 크레딧 환불 이벤트
 */
@Getter
public class CreditRefundedEvent {
    private final Long memberId;
    private final Long creditHistoryId;
    private final Integer amount;
    private final String reason;
    private final LocalDateTime occurredAt;

    public CreditRefundedEvent(Long memberId, Long creditHistoryId, Integer amount, String reason) {
        this.memberId = memberId;
        this.creditHistoryId = creditHistoryId;
        this.amount = amount;
        this.reason = reason;
        this.occurredAt = LocalDateTime.now();
    }
}
