package swyp.paperdot.document.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class DocumentSseService {

    private static final long DEFAULT_TIMEOUT_MS = 0L;

    private final Map<Long, List<SseEmitter>> emittersByDocumentId = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long documentId) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT_MS);
        emittersByDocumentId
                .computeIfAbsent(documentId, id -> new CopyOnWriteArrayList<>())
                .add(emitter);

        emitter.onCompletion(() -> removeEmitter(documentId, emitter));
        emitter.onTimeout(() -> removeEmitter(documentId, emitter));
        emitter.onError(e -> removeEmitter(documentId, emitter));

        sendEvent(documentId, "subscribed", Map.of("documentId", documentId));
        return emitter;
    }

    public void sendProgress(Long documentId, int translatedCount, int totalCount) {
        sendEvent(documentId, "progress", Map.of(
                "documentId", documentId,
                "translated", translatedCount,
                "total", totalCount
        ));
    }

    public void sendState(Long documentId, String state, String message) {
        sendEvent(documentId, "state", Map.of(
                "documentId", documentId,
                "state", state,
                "message", message
        ));
    }

    public void sendBatchFailed(Long documentId, int start, int end, String reason) {
        sendEvent(documentId, "batch_failed", Map.of(
                "documentId", documentId,
                "start", start,
                "end", end,
                "reason", reason
        ));
    }

    private void sendEvent(Long documentId, String eventName, Object data) {
        List<SseEmitter> emitters = emittersByDocumentId.get(documentId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException e) {
                removeEmitter(documentId, emitter);
            }
        }
    }

    private void removeEmitter(Long documentId, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersByDocumentId.get(documentId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersByDocumentId.remove(documentId);
        }
    }
}
