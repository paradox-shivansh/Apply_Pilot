package org.dreamers.aipilot.controllers;

import org.dreamers.aipilot.entities.ResumeMatchRequest;
import org.dreamers.aipilot.services.ResumeMatchService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/match-resume")
@CrossOrigin(originPatterns = "chrome-extension://*")
public class ResumeMatchController {

    private final ResumeMatchService resumeMatchService;

    public ResumeMatchController(ResumeMatchService resumeMatchService) {
        this.resumeMatchService = resumeMatchService;
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> matchResume(
            @RequestHeader(value = "X-AI-API-Key", defaultValue = "") String apiKey,
            @RequestBody ResumeMatchRequest request
    ) {
        String resultJson = resumeMatchService.matchResume(request, apiKey);
        return ResponseEntity.ok(resultJson);
    }
}