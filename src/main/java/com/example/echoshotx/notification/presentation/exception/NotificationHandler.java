package com.example.echoshotx.notification.presentation.exception;

import com.example.echoshotx.notification.domain.exception.NotificationErrorStatus;
import com.example.echoshotx.shared.exception.common.GeneralException;

public class NotificationHandler extends GeneralException {

    public NotificationHandler(NotificationErrorStatus errorStatus) {
        super(errorStatus);
    }
}
