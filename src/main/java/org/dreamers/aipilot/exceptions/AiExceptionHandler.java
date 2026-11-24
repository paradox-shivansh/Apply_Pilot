package org.dreamers.aipilot.exceptions;

import org.dreamers.aipilot.services.GeminiClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class AiExceptionHandler {

    @ExceptionHandler(AiProviderException.class)
    public ResponseEntity<?> handleAiProviderException(AiProviderException ex) {
        int code = ex.getStatusCode().value();
        HttpStatus responseStatus = switch (code) {
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 429 -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.BAD_GATEWAY;
        };

        return ResponseEntity.status(responseStatus).body(Map.of(
                "error", responseStatus.getReasonPhrase(),
                "status", responseStatus.value(),
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<?> handleRestClientError(RestClientResponseException ex) {
        AiProviderException mapped = GeminiClient.mapHttpError(ex.getStatusCode(), "AI Provider");
        return handleAiProviderException(mapped);
    }
}