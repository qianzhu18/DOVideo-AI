package com.example.server.controller;

import com.example.server.common.Result;
import com.example.server.dto.KnowledgeSourceLocationRequest;
import com.example.server.dto.KnowledgeSourceView;
import com.example.server.service.AuthService;
import com.example.server.service.KnowledgeSourceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/knowledge/sources")
public class KnowledgeSourceController {

    private final KnowledgeSourceService sourceService;

    public KnowledgeSourceController(KnowledgeSourceService sourceService) {
        this.sourceService = sourceService;
    }

    @GetMapping
    public Result<List<KnowledgeSourceView>> list(
            @RequestAttribute(AuthService.REQUEST_USER_ID) Long userId,
            @RequestParam Long spaceId,
            @RequestParam(required = false) Long collectionId) {
        return Result.ok(sourceService.list(userId, spaceId, collectionId));
    }

    @PostMapping("/media/{mediaId}")
    public Result<KnowledgeSourceView> attachExistingMedia(
            @RequestAttribute(AuthService.REQUEST_USER_ID) Long userId,
            @PathVariable Long mediaId,
            @Valid @RequestBody KnowledgeSourceLocationRequest request) {
        return Result.ok(sourceService.attachExistingMedia(userId, mediaId, request));
    }

    @PatchMapping("/{sourceId}/location")
    public Result<KnowledgeSourceView> move(
            @RequestAttribute(AuthService.REQUEST_USER_ID) Long userId,
            @PathVariable Long sourceId,
            @Valid @RequestBody KnowledgeSourceLocationRequest request) {
        return Result.ok(sourceService.move(userId, sourceId, request));
    }
}
