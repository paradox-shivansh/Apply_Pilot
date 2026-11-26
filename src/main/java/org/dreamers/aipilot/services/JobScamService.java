package org.dreamers.aipilot.services;

import org.dreamers.aipilot.exceptions.AiProviderException;
import org.dreamers.aipilot.entities.JobAnalysisRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class JobScamService {

    private static final List<String> FREE_EMAIL_DOMAINS = List.of(
            "@gmail.com", "@yahoo.com", "@yahoo.in", "@hotmail.com", "@outlook.com", "@rediffmail.com"
    );

    private static final Pattern FEE_PATTERN = Pattern.compile(
            "\\b(registration fee|training fee|security deposit|refundable deposit|pay\\s+(?:₹|rs\\.?|inr|\\$)?\\s*\\d+|upfront fee|processing fee)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern WHATSAPP_ONLY_PATTERN = Pattern.compile(
            "\\b(whatsapp\\s+only|only\\s+on\\s+whatsapp|dm\\s+on\\s+whatsapp|ping\\s+on\\s+whatsapp|telegram\\s+only)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern URGENCY_PATTERN = Pattern.compile(
            "\\b(urgent hiring|immediate joining|apply within\\s+\\d+\\s+hours|limited seats|act fast|hiring today only|selected candidates must pay)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private final Map<String, AiClientService> clients;
    private final JsonMapper jsonMapper;

    public JobScamService(Map<String, AiClientService> clients, JsonMapper jsonMapper) {
        this.clients = clients;
        this.jsonMapper = jsonMapper;
    }

    public String analyzeJob(JobAnalysisRequest req) {
        if (req == null || (req.descriptionText() == null || req.descriptionText().isBlank())) {
            throw new AiProviderException(HttpStatus.BAD_REQUEST, "Job description text is required for analysis.");
        }

        // Layer 1: Deterministic heuristic checks
        List<String> heuristicFlags = runHeuristics(req);

        String clientKey = (req.provider() == null || req.provider().isBlank()
                ? "gemini"
                : req.provider().trim().toLowerCase()) + "Client";
        AiClientService client = clients.get(clientKey);
        if (client == null) {
            throw new AiProviderException(HttpStatus.BAD_REQUEST, "Unsupported AI provider: " + req.provider());
        }

        // Layer 2: LLM semantic evaluation combining JD + heuristic flags
        String prompt = """
                You are a job fraud analyst. Evaluate this job posting using the scraped details and heuristic flags.
                Return ONLY a valid JSON object matching this exact structure:
                {
                  "scamScore": 0,
                  "verdict": "LEGIT",
                  "reasons": []
                }

                Rules:
                - "scamScore" must be an integer from 0 (completely safe) to 100 (almost certainly a scam).
                - "verdict" must be one of "LEGIT", "SUSPICIOUS", or "SCAM".
                - "reasons" should be concise bullet-style strings explaining the score.

                Job URL: %s
                Job Title: %s
                Company: %s
                Recruiter Contact: %s
                Salary: %s
                Heuristic Flags: %s

                Job Description:
                %s
                """.formatted(
                safe(req.jobUrl()),
                safe(req.jobTitle()),
                safe(req.companyName()),
                safe(req.recruiterContact()),
                safe(req.salary()),
                heuristicFlags.isEmpty() ? "None" : String.join("; ", heuristicFlags),
                req.descriptionText()
        );

        String llmJson = client.generateJson(prompt, req.apiKey());
        JsonNode parsed = jsonMapper.readTree(llmJson);

        int score = parsed.path("scamScore").isNumber()
                ? Math.max(0, Math.min(100, parsed.path("scamScore").asInt()))
                : Math.min(100, heuristicFlags.size() * 25);

        String verdict = parsed.path("verdict").asText("SUSPICIOUS").toUpperCase();
        if (!List.of("LEGIT", "SUSPICIOUS", "SCAM").contains(verdict)) {
            verdict = score >= 70 ? "SCAM" : (score >= 35 ? "SUSPICIOUS" : "LEGIT");
        }

        ArrayNode reasonsNode = jsonMapper.createArrayNode();
        if (parsed.path("reasons").isArray()) {
            parsed.path("reasons").forEach(r -> reasonsNode.add(r.asText()));
        }
        if (reasonsNode.isEmpty() && !heuristicFlags.isEmpty()) {
            heuristicFlags.forEach(reasonsNode::add);
        }

        ObjectNode result = jsonMapper.createObjectNode();
        result.put("scamScore", score);
        result.put("verdict", verdict);
        result.set("reasons", reasonsNode);

        ArrayNode flagsNode = jsonMapper.createArrayNode();
        heuristicFlags.forEach(flagsNode::add);
        result.set("heuristicFlags", flagsNode);

        return jsonMapper.writeValueAsString(result);
    }

    private List<String> runHeuristics(JobAnalysisRequest req) {
        List<String> flags = new ArrayList<>();
        String combinedText = String.join(" ",
                safe(req.jobTitle()),
                safe(req.companyName()),
                safe(req.recruiterContact()),
                safe(req.salary()),
                safe(req.descriptionText())
        );
        String lowerCombined = combinedText.toLowerCase();

        for (String domain : FREE_EMAIL_DOMAINS) {
            if (lowerCombined.contains(domain) || safe(req.recruiterContact()).toLowerCase().contains(domain)) {
                flags.add("Free email domain detected (" + domain + ") instead of a company domain.");
                break;
            }
        }

        if (FEE_PATTERN.matcher(combinedText).find()) {
            flags.add("Mentions potential fee, deposit, or upfront payment.");
        }

        if (WHATSAPP_ONLY_PATTERN.matcher(combinedText).find()) {
            flags.add("Directs applicants to WhatsApp or Telegram-only communication.");
        }

        if (URGENCY_PATTERN.matcher(combinedText).find()) {
            flags.add("Uses high-pressure urgency or immediate-joining phrasing.");
        }

        return flags;
    }

    private String safe(String val) {
        return val == null ? "" : val.trim();
    }
}