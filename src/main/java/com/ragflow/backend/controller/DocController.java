package com.ragflow.backend.controller;

import com.ragflow.backend.common.ApiResponse;
import com.ragflow.backend.dto.IndexResp;
import com.ragflow.backend.dto.UploadResp;
import com.ragflow.backend.service.DocService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/docs")
public class DocController {

    private final DocService docService;

    public DocController(DocService docService) {
        this.docService = docService;
    }

    @PostMapping("/upload")
    public ApiResponse<UploadResp> upload(@RequestParam("file") MultipartFile file,
            @RequestParam(value = "collection", defaultValue = "default") String collection) throws IOException {
        return ApiResponse.success(docService.upload(file, collection));
    }

    @PostMapping("/{docId}/index")
    public ApiResponse<IndexResp> index(@PathVariable Long docId) {
        return ApiResponse.success(docService.index(docId));
    }

    @GetMapping("/list")
    public ApiResponse<com.ragflow.backend.dto.PageResp<com.ragflow.backend.entity.DocumentEntity>> list(
            @RequestParam(value = "collection", defaultValue = "default") String collection,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ApiResponse.success(docService.list(collection, page, size));
    }

    @GetMapping("/collections")
    public ApiResponse<List<String>> listCollections() {
        return ApiResponse.success(docService.listCollections());
    }

    @PostMapping("/kb")
    public ApiResponse<Void> createKb(@RequestParam("name") String name) {
        docService.createCollection(name);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/kb")
    public ApiResponse<Void> deleteKb(@RequestParam("name") String name) {
        docService.deleteCollection(name);
        return ApiResponse.success(null);
    }

    @GetMapping("/{docId}/preview")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> preview(
            @PathVariable Long docId) {
        org.springframework.core.io.Resource resource = docService.loadFileAsResource(docId);
        com.ragflow.backend.entity.DocumentEntity doc = docService.getDoc(docId);

        String contentType = "application/octet-stream";
        String name = doc.getName().toLowerCase();
        if (name.endsWith(".pdf"))
            contentType = "application/pdf";
        else if (name.endsWith(".png"))
            contentType = "image/png";
        else if (name.endsWith(".jpg") || name.endsWith(".jpeg"))
            contentType = "image/jpeg";
        else if (name.endsWith(".txt"))
            contentType = "text/plain";
        else if (name.endsWith(".md"))
            contentType = "text/markdown";
        else if (name.endsWith(".html"))
            contentType = "text/html";
        else if (name.endsWith(".css"))
            contentType = "text/css";
        else if (name.endsWith(".js"))
            contentType = "application/javascript";
        else if (name.endsWith(".json"))
            contentType = "application/json";

        // Encode filename for Content-Disposition header to support Chinese characters
        String encodedFilename;
        try {
            encodedFilename = java.net.URLEncoder.encode(doc.getName(), "UTF-8")
                    .replaceAll("\\+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            encodedFilename = doc.getName();
        }

        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename*=UTF-8''" + encodedFilename)
                .body(resource);
    }
}
