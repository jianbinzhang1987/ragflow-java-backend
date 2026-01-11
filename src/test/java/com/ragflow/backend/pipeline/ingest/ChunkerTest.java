package com.ragflow.backend.pipeline.ingest;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

public class ChunkerTest {

    @Test
    public void testChunking() {
        Chunker chunker = new Chunker();
        ReflectionTestUtils.setField(chunker, "chunkSize", 10);
        ReflectionTestUtils.setField(chunker, "chunkOverlap", 2);

        String text = "1234567890abcdefghij";
        // Expected:
        // Chunk 1: "1234567890" (0-10)
        // Overlap: 2 -> next start = 10 - 2 = 8
        // Chunk 2: "90abcdefgh" (8-18)
        // Next start = 18 - 2 = 16
        // Chunk 3: "ghij" (16-20)

        List<String> chunks = chunker.chunk(text);

        System.out.println(chunks);

        Assertions.assertEquals(3, chunks.size());
        Assertions.assertEquals("1234567890", chunks.get(0));
        Assertions.assertEquals("90abcdefgh", chunks.get(1));
        Assertions.assertEquals("ghij", chunks.get(2));
    }
}
