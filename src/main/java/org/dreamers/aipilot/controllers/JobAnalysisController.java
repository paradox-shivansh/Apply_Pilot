package org.dreamers.aipilot.controllers;

import org.dreamers.aipilot.entities.JobAnalysisRequest;
import org.dreamers.aipilot.services.JobScamService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analyze-job")
@CrossOrigin(originPatterns = "chrome-extension://*")
public class JobAnalysisController {

    private final JobScamService jobScamService;

    public JobAnalysisController(JobScamService jobScamService) {
        this.jobScamService = jobScamService;
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> analyzeJob(@RequestBody JobAnalysisRequest request) {
        String jsonResult = jobScamService.analyzeJob(request);
        return ResponseEntity.ok(jsonResult);
    }
}