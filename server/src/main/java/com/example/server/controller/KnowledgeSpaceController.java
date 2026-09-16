package com.example.server.controller;

import com.example.server.common.Result;
import com.example.server.dto.KnowledgeSpaceCreateRequest;
import com.example.server.dto.KnowledgeSpaceUpdateRequest;
import com.example.server.dto.KnowledgeSpaceView;
import com.example.server.service.AuthService;
import com.example.server.service.KnowledgeSpaceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/knowledge/spaces")
public class KnowledgeSpaceController {

    private final KnowledgeSpaceService knowledgeSpaceService;

    public KnowledgeSpaceController(KnowledgeSpaceService knowledgeSpaceService) {
        this.knowledgeSpaceService = knowledgeSpaceService;
    }

    @GetMapping
    public Result<List<KnowledgeSpaceView>> list(
            @RequestAttribute(AuthService.REQUEST_USER_ID) Long userId) {
        return Result.ok(knowledgeSpaceService.listOwnedSpaces(userId));
    }

    @PostMapping
    public Result<KnowledgeSpaceView> create(
            @RequestAttribute(AuthService.REQUEST_USER_ID) Long userId,
            @Valid @RequestBody KnowledgeSpaceCreateRequest request) {
        return Result.ok(knowledgeSpaceService.create(userId, request));
    }

    @PatchMapping("/{spaceId}")
    public Result<KnowledgeSpaceView> update(
            @RequestAttribute(AuthService.REQUEST_USER_ID) Long userId,
            @PathVariable Long spaceId,
            @Valid @RequestBody KnowledgeSpaceUpdateRequest request) {
        return Result.ok(knowledgeSpaceService.update(userId, spaceId, request));
    }
}
