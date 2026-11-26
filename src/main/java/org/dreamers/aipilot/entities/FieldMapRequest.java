package org.dreamers.aipilot.entities;

import java.util.List;

public record FieldMapRequest(
        String userId,
        String jobUrl,
        String jobTitle,
        String companyName,
        String provider,
        String resumeJson,
        List<String> missingSkills,
        List<FormFieldDescriptor> fields
) {
    public record FormFieldDescriptor(
            String fieldId,
            String tag,
            String type,
            String id,
            String name,
            String placeholder,
            String ariaLabel,
            String labelText,
            List<String> options
    ) {}
}