package com.ragflow.backend.controller;

import com.ragflow.backend.common.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    @Value("${rag.max-file-size:10MB}")
    private String maxFileSize;

    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("maxFileSize", maxFileSize);
        return ApiResponse.success(config);
    }
}
