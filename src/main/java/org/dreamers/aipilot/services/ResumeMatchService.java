package org.dreamers.aipilot.services;

import org.dreamers.aipilot.entities.ResumeProfile;
import org.dreamers.aipilot.exceptions.AiProviderException;
import org.dreamers.aipilot.entities.ResumeMatchRequest;
import org.dreamers.aipilot.repositories.ResumeProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Map;

@Service
public class ResumeMatchService {

    private final ResumeProfileRepository resumeRepository;
    private final Map<String, AiClientService> clients;
    private final JsonMapper jsonMapper;

    public ResumeMatchService(
            ResumeProfileRepository resumeRepository,
            Map<String, AiClientService> clients,
            JsonMapper jsonMapper
    ) {
        this.resumeRepository = resumeRepository;
        this.clients = clients;
        this.jsonMapper = jsonMapper;
    }

    public String matchResume(ResumeMatchRequest req, String apiKey) {
        if (req == null || req.descriptionText() == null || req.descriptionText().isBlank()) {
            throw new AiProviderException(HttpStatus.BAD_REQUEST, "Job description text is required.");
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
                                "No uploaded resume found for user: " + userId + ". Upload your resume first."
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

        String prompt = """
                You are an expert technical recruiter and ATS resume analyst.
                Compare the candidate's parsed Resume JSON against the Job Description below.
                Return ONLY a valid JSON object matching this exact schema:
                {
                  "matchScore": 0,
                  "matchingSkills": [],
                  "missingSkills": [],
                  "suggestions": []
                }

                Rules:
                - "matchScore" must be an integer from 0 to 100 reflecting realistic skill, experience, and domain alignment.
                - "matchingSkills" must list concrete skills, tools, or keywords present in BOTH the resume and the JD.
                - "missingSkills" must list important skills, frameworks, or keywords required/preferred by the JD that are missing or weak in the resume.
                - "suggestions" must be 2 to 4 actionable, specific bullet points on what to add or emphasize in the resume before applying.

                Job Title: %s
                Company: %s

                Job Description:
                %s

                Candidate Resume JSON:
                %s
                """.formatted(
                safe(req.jobTitle()),
                safe(req.companyName()),
                req.descriptionText(),
                resumeJson
        );

        String llmJson = client.generateJson(prompt, apiKey);
        JsonNode parsed = jsonMapper.readTree(llmJson);

        int score = parsed.path("matchScore").isNumber()
                ? Math.max(0, Math.min(100, parsed.path("matchScore").asInt()))
                : 0;

        ObjectNode normalized = jsonMapper.createObjectNode();
        normalized.put("matchScore", score);
        normalized.set("matchingSkills", extractStringArray(parsed.path("matchingSkills")));
        normalized.set("missingSkills", extractStringArray(parsed.path("missingSkills")));
        normalized.set("suggestions", extractStringArray(parsed.path("suggestions")));

        return jsonMapper.writeValueAsString(normalized);
    }

    private ArrayNode extractStringArray(JsonNode node) {
        ArrayNode arr = jsonMapper.createArrayNode();
        if (node != null && node.isArray()) {
            node.forEach(item -> {
                String text = item.asText("").trim();
                if (!text.isEmpty()) arr.add(text);
            });
        }
        return arr;
    }

    private String safe(String val) {
        return val == null ? "" : val.trim();
    }
}