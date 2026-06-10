package com.maul.onchain3d.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * Global WebFlux exception handler that returns structured JSON error responses.
 *
 * <p>{@code WebExchangeBindException} (validation failures) maps to HTTP 400.
 * All other exceptions map to HTTP 500.
 *
 * <p>Ordered at {@code -2} to run before the default Spring Boot error handler
 * ({@code DefaultErrorWebExceptionHandler} is ordered at {@code -1}).
 */
@Slf4j
@Order(-2)
@Component
@RequiredArgsConstructor
public class GlobalErrorHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        HttpStatus status = resolveStatus(ex);

        log.error("GlobalErrorHandler: {} {} -> {} {}",
                exchange.getRequest().getMethod(),
                exchange.getRequest().getPath(),
                status.value(),
                ex.getMessage(),
                ex instanceof WebExchangeBindException ? null : ex);

        Map<String, Object> body = Map.of(
                "error",     status.getReasonPhrase(),
                "message",   ex.getMessage() != null ? ex.getMessage() : "unexpected error",
                "timestamp", Instant.now().toString());

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            bytes = "{\"error\":\"Internal Server Error\"}".getBytes();
        }

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private HttpStatus resolveStatus(Throwable ex) {
        if (ex instanceof WebExchangeBindException) {
            return HttpStatus.BAD_REQUEST;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
