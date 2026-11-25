package org.dreamers.aipilot.services;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.dreamers.aipilot.entities.ResumeProfile;
import org.dreamers.aipilot.exceptions.AiProviderException;
import org.dreamers.aipilot.repositories.ResumeProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;

@Service
public class ResumeService {

    private final ResumeProfileRepository repository;
    private final Map<String, AiClientService> clients;
    private final JsonMapper jsonMapper;

    public ResumeService(
            ResumeProfileRepository repository,
            Map<String, AiClientService> clients,
            JsonMapper jsonMapper
    ) {
        this.repository = repository;
        this.clients = clients;
        this.jsonMapper = jsonMapper;
    }

    public ResumeProfile uploadAndParse(String userId, String provider, String apiKey, MultipartFile file) {
        if (userId == null || userId.isBlank()) {
            throw new AiProviderException(HttpStatus.BAD_REQUEST, "userId is required.");
        }
        if (file == null || file.isEmpty()) {
            throw new AiProviderException(HttpStatus.BAD_REQUEST, "Please select a PDF resume file.");
        }

        String rawText = extractPdfText(file);
        if (rawText.isBlank()) {
            throw new AiProviderException(HttpStatus.BAD_REQUEST, "Could not extract readable text from the PDF.");
        }

        String clientKey = (provider == null || provider.isBlank() ? "gemini" : provider.toLowerCase()) + "Client";
        AiClientService client = clients.getOrDefault(clientKey, clients.get("geminiClient"));

        String prompt = """
                Extract structured resume information from the text below and return ONLY a valid JSON object
                with this exact shape (use empty string "" or empty array [] if a field is missing):
                {
                  "name": "",
                  "email": "",
                  "phone": "",
                  "skills": [],
                  "experience": [
                    {
                      "role": "",
                      "company": "",
                      "duration": "",
                      "highlights": []
                    }
                  ],
                  "education": [
                    {
                      "degree": "",
                      "institution": "",
                      "year": ""
                    }
                  ]
                }

                Resume text:
                """ + rawText;

        String parsedJson = client.generateJson(prompt, apiKey);

        // Validate JSON structure before saving
        JsonNode node = jsonMapper.readTree(parsedJson);
        String normalizedJson = jsonMapper.writeValueAsString(node);

        ResumeProfile profile = repository.findByUserId(userId).orElseGet(ResumeProfile::new);
        profile.setUserId(userId);
        profile.setFileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "resume.pdf");
        profile.setRawText(rawText);
        profile.setParsedJson(normalizedJson);

        return repository.save(profile);
    }

    public ResumeProfile getByUserId(String userId) {
        return repository.findByUserId(userId)
                .orElseThrow(() -> new AiProviderException(
                        HttpStatus.NOT_FOUND,
                        "No resume profile found for user: " + userId
                ));
    }

    private String extractPdfText(MultipartFile file) {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        } catch (IOException ex) {
            throw new AiProviderException(HttpStatus.BAD_REQUEST, "Failed to read PDF file: " + ex.getMessage());
        }
    }
}