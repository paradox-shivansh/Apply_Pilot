package org.dreamers.aipilot.controllers;

import org.dreamers.aipilot.entities.ResumeProfile;
import org.dreamers.aipilot.services.ResumeService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/resume")
@CrossOrigin(originPatterns = "chrome-extension://*")
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadResume(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "userId", defaultValue = "default-user") String userId,
            @RequestParam(value = "provider", defaultValue = "gemini") String provider,
            @RequestParam(value = "apiKey", defaultValue = "") String apiKey
    ) {
        ResumeProfile saved = resumeService.uploadAndParse(userId, provider, apiKey, file);
        return ResponseEntity.ok(Map.of(
                "userId", saved.getUserId(),
                "fileName", saved.getFileName(),
                "parsedJson", saved.getParsedJson(),
                "updatedAt", saved.getUpdatedAt().toString()
        ));
    }

    @GetMapping
    public ResponseEntity<?> getResume(
            @RequestParam(value = "userId", defaultValue = "default-user") String userId
    ) {
        ResumeProfile saved = resumeService.getByUserId(userId);
        return ResponseEntity.ok(Map.of(
                "userId", saved.getUserId(),
                "fileName", saved.getFileName(),
                "parsedJson", saved.getParsedJson(),
                "rawText", saved.getRawText(),
                "updatedAt", saved.getUpdatedAt().toString()
        ));
    }
}