package com.ragflow.backend.service;

import com.ragflow.backend.dto.IndexResp;
import com.ragflow.backend.dto.UploadResp;
import com.ragflow.backend.embedding.EmbeddingClient;
import com.ragflow.backend.entity.ChunkEntity;
import com.ragflow.backend.entity.DocumentEntity;
import com.ragflow.backend.pipeline.ingest.Chunker;
import com.ragflow.backend.pipeline.ingest.FileParser;
import com.ragflow.backend.repository.ChunkRepository;
import com.ragflow.backend.repository.DocumentRepository;
import com.ragflow.backend.vectorstore.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DocService {

    private static final Logger log = LoggerFactory.getLogger(DocService.class);

    private final DocumentRepository docRepo;
    private final ChunkRepository chunkRepo;
    private final FileParser fileParser;
    private final Chunker chunker;
    private final EmbeddingClient embeddingClient;
    private final VectorStore vectorStore;

    @Value("${storage.upload-dir:./data/uploads}")
    private String uploadDir;

    public DocService(DocumentRepository docRepo, ChunkRepository chunkRepo, FileParser fileParser, Chunker chunker,
            EmbeddingClient embeddingClient, VectorStore vectorStore) {
        this.docRepo = docRepo;
        this.chunkRepo = chunkRepo;
        this.fileParser = fileParser;
        this.chunker = chunker;
        this.embeddingClient = embeddingClient;
        this.vectorStore = vectorStore;
    }

    @Transactional
    public UploadResp upload(MultipartFile file, String collection) throws IOException {
        Files.createDirectories(Paths.get(uploadDir));
        String originalFilename = file.getOriginalFilename();
        String storedFilename = System.currentTimeMillis() + "_" + originalFilename;
        Path targetPath = Paths.get(uploadDir, storedFilename);

        file.transferTo(targetPath);

        DocumentEntity doc = new DocumentEntity();
        doc.setCollection(collection);
        doc.setName(originalFilename);
        doc.setPath(targetPath.toAbsolutePath().toString());
        doc.setSize(file.getSize());
        doc.setStatus(DocumentEntity.Status.UPLOADED);

        doc = docRepo.save(doc);

        return new UploadResp(doc.getId(), doc.getName(), doc.getStatus().name());
    }

    @Transactional
    public IndexResp index(Long docId) {
        DocumentEntity doc = docRepo.findById(docId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        try {
            Path path = Paths.get(doc.getPath());
            String filename = doc.getName().toLowerCase();
            String text = "";

            if (filename.endsWith(".txt") || filename.endsWith(".md")) {
                text = Files.readString(path);
            } else {
                throw new UnsupportedOperationException("File type not supported for indexing: " + filename);
            }

            List<String> chunks = chunker.chunk(text);

            List<ChunkEntity> old = chunkRepo.findByDocId(docId);
            chunkRepo.deleteAll(old);

            if (!chunks.isEmpty()) {
                List<float[]> vectors = embeddingClient.embedBatch(chunks);

                for (int i = 0; i < chunks.size(); i++) {
                    String content = chunks.get(i);
                    float[] vec = vectors.get(i);

                    ChunkEntity entity = new ChunkEntity();
                    entity.setDocId(docId);
                    entity.setCollection(doc.getCollection());
                    entity.setChunkIndex(i);
                    entity.setContent(content);
                    entity.setContentHash(String.valueOf(content.hashCode()));

                    entity = chunkRepo.save(entity);

                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("docId", docId);
                    metadata.put("chunkId", entity.getId());
                    metadata.put("docName", doc.getName());

                    vectorStore.upsert(doc.getCollection(), entity.getId(), vec, metadata);
                }
            }

            vectorStore.save();

            doc.setStatus(DocumentEntity.Status.INDEXED);
            docRepo.save(doc);

            return new IndexResp(docId, chunks.size(), "INDEXED", null);

        } catch (Exception e) {
            log.error("Indexing failed", e);
            doc.setStatus(DocumentEntity.Status.FAILED);
            docRepo.save(doc);
            return new IndexResp(docId, 0, "FAILED", e.getMessage());
        }
    }

    public List<DocumentEntity> list(String collection) {
        return docRepo.findByCollection(collection);
    }

    public List<String> listCollections() {
        return docRepo.findAll().stream()
                .map(DocumentEntity::getCollection)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
    }
}
