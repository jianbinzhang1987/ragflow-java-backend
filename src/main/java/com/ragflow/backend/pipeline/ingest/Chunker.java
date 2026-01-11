package com.ragflow.backend.pipeline.ingest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class Chunker {

    @Value("${rag.chunk-size:800}")
    private int chunkSize;

    @Value("${rag.chunk-overlap:120}")
    private int chunkOverlap;

    public List<String> chunk(String text) {
        if (text == null || text.isEmpty())
            return new ArrayList<>();

        List<String> chunks = new ArrayList<>();
        int len = text.length();
        int start = 0;

        while (start < len) {
            int end = Math.min(start + chunkSize, len);
            chunks.add(text.substring(start, end));

            if (end == len)
                break;

            start += (chunkSize - chunkOverlap);
        }
        return chunks;
    }
}
