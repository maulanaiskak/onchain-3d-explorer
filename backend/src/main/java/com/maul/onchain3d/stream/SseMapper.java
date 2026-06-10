package com.maul.onchain3d.stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Helper component that serialises arbitrary objects to JSON strings for SSE payloads.
 * Owns the shared {@link ObjectMapper} for the stream package.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseMapper {

    private final ObjectMapper objectMapper;

    /**
     * Serialises {@code value} to a compact JSON string.
     *
     * @param value any Jackson-serialisable object
     * @return JSON string, or {@code "{}"} on serialisation failure (logged at WARN)
     */
    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("SseMapper.toJson failed for type={}", value.getClass().getSimpleName(), e);
            return "{}";
        }
    }
}
