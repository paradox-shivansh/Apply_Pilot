package org.dreamers.aipilot.services;

import org.dreamers.aipilot.entities.ResumeProfile;
import org.dreamers.aipilot.exceptions.AiProviderException;
import org.dreamers.aipilot.entities.FieldMapRequest;
import org.dreamers.aipilot.repositories.ResumeProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Service
public class FieldMappingService {

    private final ResumeProfileRepository resumeRepository;
    private final Map<String, AiClientService> clients;
    private final JsonMapper jsonMapper;

    public FieldMappingService(
            ResumeProfileRepository resumeRepository,
            Map<String, AiClientService> clients,
            JsonMapper jsonMapper
    ) {
        this.resumeRepository = resumeRepository;
        this.clients = clients;
        this.jsonMapper = jsonMapper;
    }

    public String mapFields(FieldMapRequest req, String apiKey) {
        if (req == null || req.fields() == null || req.fields().isEmpty()) {
            throw new AiProviderException(HttpStatus.BAD_REQUEST, "No form fields provided to map.");
        }

        String userId = (req.userId() == null || req.userId().isBlank())
                ? "default-user"
                : req.userId().trim();

        String resumeJson = resumeRepository.findByUserId(userId)
                .map(ResumeProfile::getParsedJson)
                .orElseGet(() -> {
                    if (req.resumeJson() == null || req.resumeJson().isBlank()) {
                        throw new AiProviderException(
                                HttpStatus.NOT_FOUND,
                                "No resume found for user: " + userId + ". Upload your resume first."
                        );
                    }
                    try {
                        JsonNode node = jsonMapper.readTree(req.resumeJson());
                        String normalized = jsonMapper.writeValueAsString(node);

                        ResumeProfile restored = new ResumeProfile();
                        restored.setUserId(userId);
                        restored.setFileName("browser-cache.json");
                        restored.setRawText(normalized);
                        restored.setParsedJson(normalized);
                        resumeRepository.save(restored);
                        return normalized;
                    } catch (Exception ex) {
                        throw new AiProviderException(HttpStatus.BAD_REQUEST, "Invalid cached resume JSON.");
                    }
                });

        String clientKey = (req.provider() == null || req.provider().isBlank()
                ? "gemini"
                : req.provider().trim().toLowerCase()) + "Client";
        AiClientService client = clients.get(clientKey);
        if (client == null) {
            throw new AiProviderException(HttpStatus.BAD_REQUEST, "Unsupported AI provider: " + req.provider());
        }

        String fieldsJson = jsonMapper.writeValueAsString(req.fields());
        List<String> missing = req.missingSkills() == null ? List.of() : req.missingSkills();
        String missingJson = jsonMapper.writeValueAsString(missing);

        String prompt = """
                You are a job application form autofill assistant.
                Map each form field's "fieldId" to the best matching value from the candidate's Resume JSON.
                Return ONLY a valid JSON object mapping "fieldId" -> "string value" (e.g., {"field_1": "Aarav Sharma"}).

                Rules:
                - Only include fields where you have a confident match or helpful draft; omit unknown/unrelated fields.
                - For <select> fields with "options", pick the closest matching option text/value from "options" when possible.
                - If "missingSkills" from JD gap analysis is non-empty and a field asks about skills, summary, cover note, or why you're a fit, weave relevant items from "missingSkills" naturally so the candidate can review and edit/remove anything they don't want to keep.
                - Keep values clean plain strings without markdown formatting.

                Job Title: %s
                Company: %s
                Missing Skills From Match Analysis (optional boost): %s

                Form Fields:
                %s

                Candidate Resume JSON:
                %s
                """.formatted(
                safe(req.jobTitle()),
                safe(req.companyName()),
                missingJson,
                fieldsJson,
                resumeJson
        );

        String llmJson = client.generateJson(prompt, apiKey);
        JsonNode parsed = jsonMapper.readTree(llmJson);

        ObjectNode result = jsonMapper.createObjectNode();
        if (parsed != null && parsed.isObject()) {
            for (Map.Entry<String, JsonNode> entry : parsed.properties()) {
                JsonNode value = entry.getValue();
                if (value == null || value.isNull() || value.isMissingNode()) {
                    continue;
                }

                // Scalars -> plain string; arrays/objects -> compact JSON string
                String val = value.isValueNode()
                        ? value.asString("").trim()
                        : value.toString().trim();

                if (!val.isEmpty()) {
                    result.put(entry.getKey(), val);
                }
            }
        }

        return jsonMapper.writeValueAsString(result);
    }

    private String safe(String val) {
        return val == null ? "" : val.trim();
    }
}