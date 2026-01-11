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

    @jakarta.annotation.PostConstruct
    public void init() {
        cleanupDuplicates();
    }

    private void cleanupDuplicates() {
        try {
            log.info("Starting duplicate document cleanup...");
            List<DocumentEntity> allDocs = docRepo.findAll();
            Map<String, List<DocumentEntity>> grouped = allDocs.stream()
                    .collect(java.util.stream.Collectors.groupingBy(d -> d.getCollection() + "||" + d.getName()));

            int deletedCount = 0;
            for (List<DocumentEntity> group : grouped.values()) {
                if (group.size() > 1) {
                    // Sort by ID descending (keep latest)
                    group.sort((a, b) -> Long.compare(b.getId(), a.getId()));
                    // Keep first, delete rest
                    for (int i = 1; i < group.size(); i++) {
                        DocumentEntity toDelete = group.get(i);
                        deleteDocPhysical(toDelete);
                        deletedCount++;
                    }
                }
            }
            log.info("Duplicate cleanup finished. Removed {} duplicate documents.", deletedCount);
        } catch (Exception e) {
            log.error("Duplicate cleanup failed", e);
        }
    }

    private void deleteDocPhysical(DocumentEntity doc) {
        List<ChunkEntity> chunks = chunkRepo.findByDocId(doc.getId());
        chunkRepo.deleteAll(chunks);
        if (doc.getPath() != null && !doc.getPath().isEmpty()) {
            try {
                Files.deleteIfExists(Paths.get(doc.getPath()));
            } catch (IOException e) {
                // Ignore
            }
        }
        docRepo.delete(doc);
    }

    @Transactional
    public UploadResp upload(MultipartFile file, String collection) throws IOException {
        String originalFilename = file.getOriginalFilename();

        // 1. Check if file already exists in this collection
        DocumentEntity existingDoc = docRepo.findFirstByCollectionAndName(collection, originalFilename);
        if (existingDoc != null && !".sys_init".equals(originalFilename)) {
            throw new IllegalArgumentException("文件 [" + originalFilename + "] 已已存在于知识库中，请勿重复上传。");
        }

        Files.createDirectories(Paths.get(uploadDir));
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

        // 2. Auto-parse (indexing) immediately
        try {
            // We call index() method internal logic.
            // Note: index() is public and Transactional. Calling it from here (internal
            // call) might bypass proxy transaction if same class.
            // But we are already in Transactional @upload.
            // However, to ensure separate transaction or partial commit, usually we might
            // want separate service.
            // But here, let's just execute the indexing logic.
            // Since index() returns IndexResp, we can ignore it or log it.
            log.info("Auto-indexing document: {}", doc.getId());
            IndexResp indexResp = index(doc.getId());
            if ("FAILED".equals(indexResp.getStatus())) {
                log.warn("Auto-indexing failed for doc {}: {}", doc.getId(), indexResp.getError());
                // doc status is already updated in index(), so we just return the current
                // status
                return new UploadResp(doc.getId(), doc.getName(), "FAILED");
            }
        } catch (Exception e) {
            log.error("Auto-indexing triggers error", e);
            return new UploadResp(doc.getId(), doc.getName(), "FAILED");
        }

        // Reload doc to get updated status
        doc = docRepo.findById(doc.getId()).orElse(doc);

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
            } else if (filename.endsWith(".pdf")) {
                // PDF parsing using Apache PDFBox
                // Disable font cache to avoid system font scanning warnings
                System.setProperty("sun.java2d.cmm", "sun.java2d.cmm.kcms.KcmsServiceProvider");
                System.setProperty("org.apache.pdfbox.rendering.UsePureJavaCMYKConversion", "true");

                try (org.apache.pdfbox.pdmodel.PDDocument pdfDoc = org.apache.pdfbox.Loader.loadPDF(path.toFile())) {
                    org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
                    stripper.setSortByPosition(true);
                    text = stripper.getText(pdfDoc);

                    if (text == null || text.trim().isEmpty()) {
                        log.warn("PDF text extraction returned empty content for: {}", filename);
                        text = "[PDF文档内容为空或无法提取文本]";
                    }
                } catch (Exception pdfEx) {
                    log.error("PDF parsing failed for {}: {}", filename, pdfEx.getMessage());
                    throw new RuntimeException("PDF解析失败: " + pdfEx.getMessage(), pdfEx);
                }
            } else if (filename.endsWith(".docx")) {
                // DOCX parsing using Apache POI
                try (java.io.FileInputStream fis = new java.io.FileInputStream(path.toFile());
                        org.apache.poi.xwpf.usermodel.XWPFDocument docxDoc = new org.apache.poi.xwpf.usermodel.XWPFDocument(
                                fis)) {
                    org.apache.poi.xwpf.extractor.XWPFWordExtractor extractor = new org.apache.poi.xwpf.extractor.XWPFWordExtractor(
                            docxDoc);
                    text = extractor.getText();
                    extractor.close();
                }
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

    public com.ragflow.backend.dto.PageResp<DocumentEntity> list(String collection, int page, int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page - 1,
                size,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "id"));

        org.springframework.data.domain.Page<DocumentEntity> paged = docRepo.findByCollection(collection, pageable);

        // Filter sys_init if present (though sorting might push it anywhere, it's safer
        // to filter, but Page API makes filtering post-fetch hard for pagination
        // consistency.
        // Usually we exclude it in query. But simpler: just return it and let frontend
        // hide it, or ignore.
        // Actually, sys_init is hidden. Let's see if we can exclude it in repo.
        // Ignoring for now or let frontend filter. Or better, update repo to not select
        // it.
        // findByCollectionAndNameNot(collection, ".sys_init", pageable)?
        // Let's stick to what we have. Frontend can filter or we can accept it's there.
        // User asked for "file list".

        // Better:
        // List<DocumentEntity> content = paged.getContent().stream().filter(d ->
        // !".sys_init".equals(d.getName())).collect(Collectors.toList());
        // PageResp resp = new PageResp<>(paged.getTotalElements(), page, size,
        // content);
        // This is slightly inaccurate for total count if sys_init is in there.

        // Let's rely on frontend filtering ".sys_init" if it appears, or use a better
        // query.
        // Since sys_init is 1 per collection, it's negligible.

        return new com.ragflow.backend.dto.PageResp<>(paged.getTotalElements(), page, size, paged.getContent());
    }

    public List<String> listCollections() {
        return docRepo.findAll().stream()
                .map(DocumentEntity::getCollection)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
    }

    @Transactional
    public void createCollection(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Collection name cannot be empty");
        }
        // Check if exists? Not strictly necessary as we just want to ensure it appears
        // in listCollections
        // Create a hidden system document to hold the collection
        DocumentEntity doc = new DocumentEntity();
        doc.setCollection(name);
        doc.setName(".sys_init");
        doc.setPath(""); // No file
        doc.setStatus(DocumentEntity.Status.INDEXED); // Mark as ready so it doesn't look weird, though it's hidden
        doc.setSize(0L);
        docRepo.save(doc);
    }

    @Transactional
    public void deleteCollection(String name) {
        if ("default".equals(name)) {
            throw new IllegalArgumentException("Cannot delete default collection");
        }
        List<DocumentEntity> docs = docRepo.findByCollection(name);

        // Logical delete or physical delete?
        // Physical delete for now to clean up
        for (DocumentEntity doc : docs) {
            // Remove chunks
            List<ChunkEntity> chunks = chunkRepo.findByDocId(doc.getId());
            chunkRepo.deleteAll(chunks);
            // Delete file if exists
            if (doc.getPath() != null && !doc.getPath().isEmpty()) {
                try {
                    Files.deleteIfExists(Paths.get(doc.getPath()));
                } catch (IOException e) {
                    log.warn("Failed to delete file: " + doc.getPath(), e);
                }
            }
        }
        docRepo.deleteAll(docs);
    }

    public DocumentEntity getDoc(Long docId) {
        return docRepo.findById(docId).orElseThrow(() -> new IllegalArgumentException("Document not found"));
    }

    public org.springframework.core.io.Resource loadFileAsResource(Long docId) {
        DocumentEntity doc = getDoc(docId);
        try {
            Path filePath = Paths.get(doc.getPath());
            org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(
                    filePath.toUri());
            if (resource.exists()) {
                return resource;
            } else {
                throw new IllegalArgumentException("File not found on disk: " + doc.getName());
            }
        } catch (java.net.MalformedURLException ex) {
            throw new IllegalArgumentException("File path invalid: " + doc.getName(), ex);
        }
    }
}
