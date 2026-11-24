package org.dreamers.aipilot.services;

import org.dreamers.aipilot.exceptions.AiProviderException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

@Service("openaiClient")
public class OpenAiClient implements AiClientService {

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";

    private final RestClient restClient;
    private final JsonMapper jsonMapper;

    public OpenAiClient(JsonMapper jsonMapper) {
        this.restClient = RestClient.create();
        this.jsonMapper = jsonMapper;
    }

    @Override
    public String generateJson(String prompt, String apiKey) {
        Map<String, Object> requestBody = Map.of(
                "model", "gpt-4o-mini",
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                )
        );

        try {
            String rawResponse = restClient.post()
                    .uri(OPENAI_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = jsonMapper.readTree(rawResponse);
            String jsonText = root
                    .path("choices")
                    .path(0)
                    .path("message")
                    .path("content")
                    .asText("");

            if (jsonText.isBlank()) {
                throw new AiProviderException(HttpStatus.BAD_GATEWAY, "OpenAI returned an empty response.");
            }

            JsonNode parsedJson = jsonMapper.readTree(jsonText);
            return jsonMapper.writeValueAsString(parsedJson);

        } catch (RestClientResponseException ex) {
            throw GeminiClient.mapHttpError(ex.getStatusCode(), "OpenAI");
        } catch (AiProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AiProviderException(HttpStatus.BAD_GATEWAY, "Failed to parse OpenAI JSON response.");
        }
    }
}