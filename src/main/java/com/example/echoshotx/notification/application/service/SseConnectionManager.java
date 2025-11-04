package com.example.echoshotx.notification.application.service;

import com.example.echoshotx.notification.domain.exception.NotificationErrorStatus;
import com.example.echoshotx.notification.presentation.exception.NotificationHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE (Server-Sent Events) 연결 관리자
 * 회원별 SSE 연결을 관리하고, 실시간 알림을 전송합니다.
 */
@Slf4j
@Component
public class SseConnectionManager {

    private static final Long DEFAULT_TIMEOUT = 60L * 1000 * 60; // 60분
    private static final String SSE_EVENT_NAME = "notification";

    // Key: memberId, Value: List of SseEmitters (다중 디바이스 지원)
    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * 새로운 SSE 연결 생성
     *
     * @param memberId 회원 ID
     * @return SseEmitter
     */
    public SseEmitter createConnection(Long memberId) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);

        // 연결 리스트에 추가
        emitters.computeIfAbsent(memberId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        log.info("SSE connection created for member: {}, total connections: {}",
                memberId, emitters.get(memberId).size());

        // 연결 완료 시 제거
        emitter.onCompletion(() -> {
            removeEmitter(memberId, emitter);
            log.info("SSE connection completed for member: {}", memberId);
        });

        // 타임아웃 시 제거
        emitter.onTimeout(() -> {
            removeEmitter(memberId, emitter);
            log.warn("SSE connection timeout for member: {}", memberId);
        });

        // 에러 시 제거
        emitter.onError((e) -> {
            removeEmitter(memberId, emitter);
            log.error("SSE connection error for member: {}, error: {}", memberId, e.getMessage());
        });

        // 연결 확인용 초기 이벤트 전송
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("SSE connection established"));
        } catch (IOException e) {
            log.error("Failed to send initial connection event to member: {}", memberId, e);
            removeEmitter(memberId, emitter);
            throw new NotificationHandler(NotificationErrorStatus.SSE_CONNECTION_FAILED);
        }

        return emitter;
    }

    /**
     * 특정 회원에게 알림 전송
     *
     * @param memberId 회원 ID
     * @param data     전송할 데이터
     * @return 전송 성공 여부
     */
    public boolean sendToMember(Long memberId, Object data) {
        List<SseEmitter> memberEmitters = emitters.get(memberId);

        if (memberEmitters == null || memberEmitters.isEmpty()) {
            log.warn("No active SSE connections for member: {}", memberId);
            return false;
        }

        boolean hasSuccessfulSend = false;
        List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();

        for (SseEmitter emitter : memberEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(SSE_EVENT_NAME)
                        .data(data));
                hasSuccessfulSend = true;
                log.info("Notification sent to member: {}", memberId);
            } catch (IOException e) {
                log.error("Failed to send notification to member: {}, removing dead emitter", memberId, e);
                deadEmitters.add(emitter);
            }
        }

        // 실패한 emitter 제거
        deadEmitters.forEach(emitter -> removeEmitter(memberId, emitter));

        return hasSuccessfulSend;
    }

    /**
     * 여러 회원에게 브로드캐스트
     *
     * @param memberIds 회원 ID 목록
     * @param data      전송할 데이터
     */
    public void broadcast(List<Long> memberIds, Object data) {
        log.info("Broadcasting notification to {} members", memberIds.size());
        for (Long memberId : memberIds) {
            sendToMember(memberId, data);
        }
    }

    /**
     * 모든 연결된 회원에게 브로드캐스트
     *
     * @param data 전송할 데이터
     */
    public void broadcastToAll(Object data) {
        log.info("Broadcasting notification to all {} members", emitters.size());
        emitters.keySet().forEach(memberId -> sendToMember(memberId, data));
    }

    /**
     * 특정 회원의 연결 해제
     *
     * @param memberId 회원 ID
     */
    public void disconnectMember(Long memberId) {
        List<SseEmitter> memberEmitters = emitters.remove(memberId);
        if (memberEmitters != null) {
            memberEmitters.forEach(SseEmitter::complete);
            log.info("Disconnected all SSE connections for member: {}, count: {}",
                    memberId, memberEmitters.size());
        }
    }

    /**
     * 특정 회원의 활성 연결 수 조회
     *
     * @param memberId 회원 ID
     * @return 연결 수
     */
    public int getConnectionCount(Long memberId) {
        List<SseEmitter> memberEmitters = emitters.get(memberId);
        return memberEmitters != null ? memberEmitters.size() : 0;
    }

    /**
     * 전체 활성 연결 수 조회
     *
     * @return 전체 연결 수
     */
    public int getTotalConnectionCount() {
        return emitters.values().stream()
                .mapToInt(List::size)
                .sum();
    }

    /**
     * 특정 회원이 연결되어 있는지 확인
     *
     * @param memberId 회원 ID
     * @return 연결 여부
     */
    public boolean isConnected(Long memberId) {
        List<SseEmitter> memberEmitters = emitters.get(memberId);
        return memberEmitters != null && !memberEmitters.isEmpty();
    }

    /**
     * Emitter 제거 (내부 메서드)
     */
    private void removeEmitter(Long memberId, SseEmitter emitter) {
        List<SseEmitter> memberEmitters = emitters.get(memberId);
        if (memberEmitters != null) {
            memberEmitters.remove(emitter);
            // 리스트가 비어있으면 키 자체를 제거
            if (memberEmitters.isEmpty()) {
                emitters.remove(memberId);
            }
        }
    }

    /**
     * 모든 연결 해제 (서버 종료 시 사용)
     */
    public void disconnectAll() {
        log.info("Disconnecting all SSE connections, total members: {}", emitters.size());
        emitters.forEach((memberId, emitterList) -> {
            emitterList.forEach(SseEmitter::complete);
        });
        emitters.clear();
    }
}
