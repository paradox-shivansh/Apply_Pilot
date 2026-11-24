package org.dreamers.aipilot.controllers;


import org.dreamers.aipilot.services.AiClientService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(originPatterns = "chrome-extension://*")
public class AiController {

    private final Map<String, AiClientService> clients;

    public AiController(Map<String, AiClientService> clients) {
        this.clients = clients;
    }

    public record GenerateRequest(String provider, String prompt, String apiKey) {}

    @PostMapping(value = "/generate", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> generate(@RequestBody GenerateRequest req) {
        String key = (req.provider() == null ? "gemini" : req.provider().toLowerCase()) + "Client";
        AiClientService client = clients.getOrDefault(key, clients.get("geminiClient"));
        String json = client.generateJson(req.prompt(), req.apiKey());
        return ResponseEntity.ok(json);
    }
}