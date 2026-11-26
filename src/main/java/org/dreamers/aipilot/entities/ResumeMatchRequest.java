package org.dreamers.aipilot.entities;

public record ResumeMatchRequest(
        String userId,
        String jobUrl,
        String jobTitle,
        String companyName,
        String descriptionText,
        String provider,
        String resumeJson
) {}