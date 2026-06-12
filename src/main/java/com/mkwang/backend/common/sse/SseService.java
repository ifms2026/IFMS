package com.mkwang.backend.common.sse;

import com.mkwang.backend.common.dto.SseEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SseService {

    private static final long SSE_TIMEOUT_MS = 0L;
    private static final SseEventType DEFAULT_EVENT_TYPE = SseEventType.NOTIFICATION;

    private final Map<Long, Map<String, SseEmitter>> userEmitters = new ConcurrentHashMap<>();

    // walletKey ("PROJECT:5", "DEPARTMENT:2", "COMPANY_FUND:1") → {emitterId → userId}
    private final Map<String, Map<String, Long>> walletSubscriberEmitters = new ConcurrentHashMap<>();
    // emitterId → walletKey, used for cleanup on disconnect
    private final Map<String, String> emitterWallets = new ConcurrentHashMap<>();

    public SseEmitter connect(Long userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        String emitterId = UUID.randomUUID().toString();

        userEmitters
                .computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>())
                .put(emitterId, emitter);

        emitter.onCompletion(() -> removeEmitter(userId, emitterId));
        emitter.onTimeout(() -> removeEmitter(userId, emitterId));
        emitter.onError(ignored -> removeEmitter(userId, emitterId));

        try {
            emitter.send(SseEmitter.event()
                    .name(SseEventType.CONNECTED.getValue())
                    .data("SSE connected"));
        } catch (IOException e) {
            removeEmitter(userId, emitterId);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    /**
     * Subscribe userId to real-time events for a specific wallet (PROJECT, DEPARTMENT, COMPANY_FUND).
     * Only this dedicated emitter will receive wallet.updated / transaction.created for that wallet.
     *
     * @param walletKey format: "{OWNER_TYPE}:{ownerId}" e.g. "PROJECT:5", "DEPARTMENT:2"
     */
    public SseEmitter connectToWallet(Long userId, String walletKey) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        String emitterId = UUID.randomUUID().toString();

        userEmitters.computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>())
                .put(emitterId, emitter);
        emitterWallets.put(emitterId, walletKey);
        walletSubscriberEmitters.computeIfAbsent(walletKey, ignored -> new ConcurrentHashMap<>())
                .put(emitterId, userId);

        Runnable cleanup = () -> removeEmitter(userId, emitterId);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ignored -> removeEmitter(userId, emitterId));

        try {
            emitter.send(SseEmitter.event()
                    .name(SseEventType.CONNECTED.getValue())
                    .data("SSE connected for wallet " + walletKey));
        } catch (IOException e) {
            removeEmitter(userId, emitterId);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    /**
     * Push an event to all emitters that subscribed to the given wallet via connectToWallet.
     * Does NOT send to the user's general /stream emitter — only to wallet-specific emitters.
     */
    public void sendToWalletSubscribers(String walletKey, SseEvent sseEvent) {
        Map<String, Long> subscriberEmitters = walletSubscriberEmitters.get(walletKey);
        if (subscriberEmitters == null || subscriberEmitters.isEmpty()) return;

        String eventName = resolveEventName(sseEvent);
        Object data = sseEvent == null ? null : sseEvent.getData();

        for (Map.Entry<String, Long> entry : new ArrayList<>(subscriberEmitters.entrySet())) {
            String emitterId = entry.getKey();
            Long userId = entry.getValue();
            Map<String, SseEmitter> userEmitterMap = userEmitters.get(userId);
            if (userEmitterMap == null) continue;
            SseEmitter emitter = userEmitterMap.get(emitterId);
            if (emitter == null) continue;
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException | IllegalStateException ex) {
                log.debug("[SseService] Remove broken wallet emitter walletKey={} userId={} emitterId={}",
                        walletKey, userId, emitterId);
                removeEmitter(userId, emitterId);
            }
        }
    }

    public void sendToUser(Long userId, SseEvent sseEvent) {
        Map<String, SseEmitter> emitters = userEmitters.get(userId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        String eventName = resolveEventName(sseEvent);
        Object data = sseEvent == null ? null : sseEvent.getData();

        for (Map.Entry<String, SseEmitter> entry : emitters.entrySet()) {
            String emitterId = entry.getKey();
            SseEmitter emitter = entry.getValue();
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data));
            } catch (IOException | IllegalStateException ex) {
                log.debug("[SseService] Remove broken emitter userId={} emitterId={}", userId, emitterId);
                removeEmitter(userId, emitterId);
            }
        }
    }

    private String resolveEventName(SseEvent sseEvent) {
        if (sseEvent == null || sseEvent.getEvent() == null) {
            return DEFAULT_EVENT_TYPE.getValue();
        }
        return sseEvent.getEvent().getValue();
    }

    private void removeEmitter(Long userId, String emitterId) {
        Map<String, SseEmitter> emitters = userEmitters.get(userId);
        if (emitters != null) {
            emitters.remove(emitterId);
            if (emitters.isEmpty()) {
                userEmitters.remove(userId);
            }
        }

        String walletKey = emitterWallets.remove(emitterId);
        if (walletKey != null) {
            Map<String, Long> subscriberMap = walletSubscriberEmitters.get(walletKey);
            if (subscriberMap != null) {
                subscriberMap.remove(emitterId);
                if (subscriberMap.isEmpty()) {
                    walletSubscriberEmitters.remove(walletKey);
                }
            }
        }
    }
}


