package org.dreamers.aipilot.services;

import org.dreamers.aipilot.exceptions.AiProviderException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

@Service("geminiClient")
public class GeminiClient implements AiClientService {

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";

    private final RestClient restClient;
    private final JsonMapper jsonMapper;

    public GeminiClient(JsonMapper jsonMapper) {
        this.restClient = RestClient.create();
        this.jsonMapper = jsonMapper;
    }

    @Override
    public String generateJson(String prompt, String apiKey) {
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))
                ),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json"
                )
        );

        try {
            String rawResponse = restClient.post()
                    .uri(GEMINI_URL)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = jsonMapper.readTree(rawResponse);
            String jsonText = root
                    .path("candidates")
                    .path(0)
                    .path("content")
                    .path("parts")
                    .path(0)
                    .path("text")
                    .asText("");

            if (jsonText.isBlank()) {
                throw new AiProviderException(HttpStatus.BAD_GATEWAY, "Gemini returned an empty response.");
            }

            JsonNode parsedJson = jsonMapper.readTree(jsonText);
            return jsonMapper.writeValueAsString(parsedJson);

        } catch (RestClientResponseException ex) {
            throw mapHttpError(ex.getStatusCode(), "Gemini");
        } catch (AiProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AiProviderException(HttpStatus.BAD_GATEWAY, "Failed to parse Gemini JSON response.");
        }
    }

    public static AiProviderException mapHttpError(HttpStatusCode status, String provider) {
        int code = status.value();
        if (code == 401) {
            return new AiProviderException(HttpStatus.UNAUTHORIZED, provider + " API key is invalid or missing.");
        }
        if (code == 403) {
            return new AiProviderException(HttpStatus.FORBIDDEN, provider + " API access is forbidden. Check key permissions or billing.");
        }
        if (code == 429) {
            return new AiProviderException(HttpStatus.TOO_MANY_REQUESTS, provider + " rate limit or quota exceeded. Try again shortly.");
        }
        return new AiProviderException(HttpStatus.BAD_GATEWAY, provider + " request failed with status " + code + ".");
    }
}