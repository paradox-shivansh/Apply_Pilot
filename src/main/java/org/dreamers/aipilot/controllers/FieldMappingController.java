package org.dreamers.aipilot.controllers;

import org.dreamers.aipilot.entities.FieldMapRequest;
import org.dreamers.aipilot.services.FieldMappingService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/map-fields")
@CrossOrigin(originPatterns = "chrome-extension://*")
public class FieldMappingController {

    private final FieldMappingService fieldMappingService;

    public FieldMappingController(FieldMappingService fieldMappingService) {
        this.fieldMappingService = fieldMappingService;
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> mapFields(
            @RequestHeader(value = "X-AI-API-Key", defaultValue = "") String apiKey,
            @RequestBody FieldMapRequest request
    ) {
        String mappedJson = fieldMappingService.mapFields(request, apiKey);
        return ResponseEntity.ok(mappedJson);
    }
}