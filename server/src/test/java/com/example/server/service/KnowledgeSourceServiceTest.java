package com.example.server.service;

import com.example.server.dto.KnowledgeSourceLocationRequest;
import com.example.server.dto.KnowledgeSourceView;
import com.example.server.entity.KnowledgeCollection;
import com.example.server.entity.KnowledgeSource;
import com.example.server.entity.KnowledgeSourceVersion;
import com.example.server.entity.KnowledgeSpace;
import com.example.server.entity.MediaFile;
import com.example.server.mapper.KnowledgeSourceMapper;
import com.example.server.mapper.KnowledgeSourceVersionMapper;
import com.example.server.mapper.MediaFileMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeSourceServiceTest {

    @Test
    void createsPendingVersionedSourceForUploadedVideo() {
        KnowledgeSourceMapper sourceMapper = mock(KnowledgeSourceMapper.class);
        KnowledgeSourceVersionMapper versionMapper = mock(KnowledgeSourceVersionMapper.class);
        MediaFileMapper mediaMapper = mock(MediaFileMapper.class);
        KnowledgeSpaceService spaceService = mock(KnowledgeSpaceService.class);
        KnowledgeCollectionService collectionService = mock(KnowledgeCollectionService.class);
        when(sourceMapper.selectOne(any())).thenReturn(null);
        when(spaceService.defaultSpaceForUser(7L)).thenReturn(space(3L, 7L));
        when(sourceMapper.insert(any(KnowledgeSource.class))).thenAnswer(invocation -> {
            KnowledgeSource source = invocation.getArgument(0);
            source.setId(21L);
            return 1;
        });
        when(versionMapper.insert(any(KnowledgeSourceVersion.class))).thenAnswer(invocation -> 1);

        KnowledgeSourceService service = new KnowledgeSourceService(
                sourceMapper, versionMapper, mediaMapper, spaceService, collectionService, mock(KnowledgeAuditService.class));
        KnowledgeSource source = service.ensureMediaSource(media(9L, 7L, "jvm.mp4", "aabb"));

        assertEquals(21L, source.getId());
        assertEquals(KnowledgeSourceService.SOURCE_TYPE_VIDEO, source.getSourceType());
        assertEquals(3L, source.getSpaceId());
        assertEquals(KnowledgeSourceService.STATUS_PENDING, source.getStatus());
        verify(versionMapper).insert(any(KnowledgeSourceVersion.class));
    }

    @Test
    void movesOwnedSourceIntoTargetCollection() {
        KnowledgeSourceMapper sourceMapper = mock(KnowledgeSourceMapper.class);
        KnowledgeSourceVersionMapper versionMapper = mock(KnowledgeSourceVersionMapper.class);
        MediaFileMapper mediaMapper = mock(MediaFileMapper.class);
        KnowledgeSpaceService spaceService = mock(KnowledgeSpaceService.class);
        KnowledgeCollectionService collectionService = mock(KnowledgeCollectionService.class);
        KnowledgeSource source = new KnowledgeSource();
        source.setId(21L);
        source.setOwnerUserId(7L);
        source.setSpaceId(3L);
        source.setStatus(KnowledgeSourceService.STATUS_PENDING);
        when(sourceMapper.selectById(21L)).thenReturn(source);
        KnowledgeCollection collection = new KnowledgeCollection();
        collection.setId(31L);
        collection.setSpaceId(5L);
        when(collectionService.requireCollectionInSpace(31L, 5L)).thenReturn(collection);

        KnowledgeSourceService service = new KnowledgeSourceService(
                sourceMapper, versionMapper, mediaMapper, spaceService, collectionService, mock(KnowledgeAuditService.class));
        KnowledgeSourceView moved = service.move(7L, 21L, new KnowledgeSourceLocationRequest(5L, 31L));

        assertEquals(5L, moved.spaceId());
        assertEquals(31L, moved.collectionId());
        verify(sourceMapper).updateById(source);
    }

    private static KnowledgeSpace space(Long id, Long ownerId) {
        KnowledgeSpace space = new KnowledgeSpace();
        space.setId(id);
        space.setOwnerUserId(ownerId);
        return space;
    }

    private static MediaFile media(Long id, Long ownerId, String name, String hash) {
        MediaFile media = new MediaFile();
        media.setId(id);
        media.setUserId(ownerId);
        media.setFilename(name);
        media.setContentHash(hash);
        return media;
    }
}
