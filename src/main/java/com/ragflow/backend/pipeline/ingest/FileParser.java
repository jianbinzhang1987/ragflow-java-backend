package com.ragflow.backend.pipeline.ingest;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class FileParser {

    public String parse(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null)
            return "";

        String lower = filename.toLowerCase();
        if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".markdown")) {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } else if (lower.endsWith(".pdf")) {
            // PDF parsing requires pdfbox, keeping it simple as requested:
            throw new UnsupportedOperationException("PDF parsing currently not supported (Placeholder).");
        } else {
            throw new UnsupportedOperationException("Unsupported file type: " + filename);
        }
    }
}
