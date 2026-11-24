package org.dreamers.aipilot.exceptions;
import org.springframework.http.HttpStatusCode;

public class AiProviderException extends RuntimeException {
    private final HttpStatusCode statusCode;

    public AiProviderException(HttpStatusCode statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public HttpStatusCode getStatusCode() {
        return statusCode;
    }
}