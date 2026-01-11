package com.ragflow.backend.controller;

import com.ragflow.backend.common.ApiResponse;
import com.ragflow.backend.dto.QueryReq;
import com.ragflow.backend.dto.QueryResp;
import com.ragflow.backend.entity.ChunkEntity;
import com.ragflow.backend.service.ChatService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/chat/query")
    public ApiResponse<QueryResp> query(@RequestBody QueryReq req) {
        return ApiResponse.success(chatService.query(req));
    }

    @PostMapping("/search")
    public ApiResponse<List<ChunkEntity>> search(@RequestBody QueryReq req) {
        return ApiResponse.success(chatService.searchOnly(req));
    }
}
