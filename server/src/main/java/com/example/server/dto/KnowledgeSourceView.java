package com.example.server.dto;

import com.example.server.entity.KnowledgeSource;

import java.time.LocalDateTime;

public record KnowledgeSourceView(
        Long id,
        String sourceType,
        Long spaceId,
        Long collectionId,
        Long mediaId,
        String title,
        String status,
        int currentVersion,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static KnowledgeSourceView from(KnowledgeSource source) {
        return new KnowledgeSourceView(
                source.getId(),
                source.getSourceType(),
                source.getSpaceId(),
                source.getCollectionId(),
                source.getMediaId(),
                source.getTitle(),
                source.getStatus(),
                source.getCurrentVersion() == null ? 0 : source.getCurrentVersion(),
                source.getCreatedAt(),
                source.getUpdatedAt());
    }
}
