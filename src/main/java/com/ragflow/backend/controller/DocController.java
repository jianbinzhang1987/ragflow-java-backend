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
    public ApiResponse<List<com.ragflow.backend.entity.DocumentEntity>> list(
            @RequestParam(value = "collection", defaultValue = "default") String collection) {
        return ApiResponse.success(docService.list(collection));
    }

    @GetMapping("/collections")
    public ApiResponse<List<String>> listCollections() {
        return ApiResponse.success(docService.listCollections());
    }
}
