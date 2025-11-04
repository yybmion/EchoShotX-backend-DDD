package com.example.echoshotx.notification.presentation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnreadCountResponse {

    private Long unreadCount;

    public static UnreadCountResponse of(Long count) {
        return UnreadCountResponse.builder()
                .unreadCount(count)
                .build();
    }
}
