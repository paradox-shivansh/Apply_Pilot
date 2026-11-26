package org.dreamers.aipilot.entities;

public record JobAnalysisRequest(
        String jobUrl,
        String jobTitle,
        String companyName,
        String recruiterContact,
        String salary,
        String descriptionText,
        String provider,
        String apiKey
) {}