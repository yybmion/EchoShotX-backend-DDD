package com.example.echoshotx.notification.application.event;

import com.example.echoshotx.notification.application.service.NotificationService;
import com.example.echoshotx.notification.domain.entity.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 크레딧 관련 이벤트를 수신하여 알림을 생성하는 리스너
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreditNotificationEventListener {

    private final NotificationService notificationService;

    @Async
    @EventListener
    public void handleCreditCharged(CreditChargedEvent event) {
        log.info("Handling CreditChargedEvent for member: {}, amount: {}",
                event.getMemberId(), event.getAmount());

        notificationService.createAndSendCreditNotification(
                event.getMemberId(),
                event.getCreditHistoryId(),
                NotificationType.CREDIT_CHARGED,
                "크레딧 충전 완료",
                String.format("%d 크레딧이 충전되었습니다.", event.getAmount())
        );
    }

    @Async
    @EventListener
    public void handleCreditUsed(CreditUsedEvent event) {
        log.info("Handling CreditUsedEvent for member: {}, amount: {}",
                event.getMemberId(), event.getAmount());

        notificationService.createAndSendCreditNotification(
                event.getMemberId(),
                event.getCreditHistoryId(),
                NotificationType.CREDIT_USED,
                "크레딧 사용",
                String.format("%d 크레딧이 사용되었습니다. (영상 처리)", event.getAmount())
        );
    }

    @Async
    @EventListener
    public void handleCreditRefunded(CreditRefundedEvent event) {
        log.info("Handling CreditRefundedEvent for member: {}, amount: {}",
                event.getMemberId(), event.getAmount());

        notificationService.createAndSendCreditNotification(
                event.getMemberId(),
                event.getCreditHistoryId(),
                NotificationType.CREDIT_REFUNDED,
                "크레딧 환불 완료",
                String.format("%d 크레딧이 환불되었습니다. 사유: %s",
                        event.getAmount(), event.getReason())
        );
    }
}
