package com.example.server.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.server.common.ErrorCode;
import com.example.server.dto.KnowledgeSourceLocationRequest;
import com.example.server.dto.KnowledgeSourceView;
import com.example.server.entity.KnowledgeCollection;
import com.example.server.entity.KnowledgeSource;
import com.example.server.entity.KnowledgeSourceVersion;
import com.example.server.entity.KnowledgeSpace;
import com.example.server.entity.MediaFile;
import com.example.server.exception.BusinessException;
import com.example.server.mapper.KnowledgeSourceMapper;
import com.example.server.mapper.KnowledgeSourceVersionMapper;
import com.example.server.mapper.MediaFileMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class KnowledgeSourceService {

    public static final String SOURCE_TYPE_VIDEO = "VIDEO";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_DELETED = "DELETED";

    private final KnowledgeSourceMapper sourceMapper;
    private final KnowledgeSourceVersionMapper versionMapper;
    private final MediaFileMapper mediaFileMapper;
    private final KnowledgeSpaceService spaceService;
    private final KnowledgeCollectionService collectionService;

    public KnowledgeSourceService(KnowledgeSourceMapper sourceMapper,
                                  KnowledgeSourceVersionMapper versionMapper,
                                  MediaFileMapper mediaFileMapper,
                                  KnowledgeSpaceService spaceService,
                                  KnowledgeCollectionService collectionService) {
        this.sourceMapper = sourceMapper;
        this.versionMapper = versionMapper;
        this.mediaFileMapper = mediaFileMapper;
        this.spaceService = spaceService;
        this.collectionService = collectionService;
    }

    @Transactional
    public KnowledgeSource ensureMediaSource(MediaFile media) {
        KnowledgeSource existing = findByMediaId(media.getId());
        if (existing != null) return existing;

        KnowledgeSpace defaultSpace = spaceService.defaultSpaceForUser(media.getUserId());
        KnowledgeSource source = new KnowledgeSource();
        source.setSourceType(SOURCE_TYPE_VIDEO);
        source.setOwnerUserId(media.getUserId());
        source.setSpaceId(defaultSpace.getId());
        source.setMediaId(media.getId());
        source.setTitle(media.getFilename());
        source.setContentHash(media.getContentHash());
        source.setCurrentVersion(1);
        source.setStatus(STATUS_PENDING);
        try {
            sourceMapper.insert(source);
        } catch (DuplicateKeyException error) {
            KnowledgeSource concurrent = findByMediaId(media.getId());
            if (concurrent != null) return concurrent;
            throw error;
        }

        KnowledgeSourceVersion version = new KnowledgeSourceVersion();
        version.setSourceId(source.getId());
        version.setVersionNo(1);
        version.setContentHash(media.getContentHash());
        version.setStatus(STATUS_PENDING);
        versionMapper.insert(version);
        return source;
    }

    @Transactional
    public KnowledgeSourceView attachExistingMedia(Long userId, Long mediaId, KnowledgeSourceLocationRequest request) {
        MediaFile media = mediaFileMapper.selectById(mediaId);
        if (media == null) throw new BusinessException(ErrorCode.NOT_FOUND, "视频不存在");
        if (!userId.equals(media.getUserId())) throw new SecurityException("无权访问该视频");
        KnowledgeSource source = ensureMediaSource(media);
        return move(userId, source.getId(), request);
    }

    public List<KnowledgeSourceView> list(Long userId, Long spaceId, Long collectionId) {
        spaceService.requireOwnedSpace(userId, spaceId);
        if (collectionId != null) collectionService.requireCollectionInSpace(collectionId, spaceId);
        QueryWrapper<KnowledgeSource> query = new QueryWrapper<KnowledgeSource>()
                .eq("owner_user_id", userId)
                .eq("space_id", spaceId)
                .ne("status", STATUS_DELETED)
                .orderByDesc("updated_at");
        if (collectionId == null) query.isNull("collection_id");
        else query.eq("collection_id", collectionId);
        return sourceMapper.selectList(query).stream().map(KnowledgeSourceView::from).toList();
    }

    @Transactional
    public KnowledgeSourceView move(Long userId, Long sourceId, KnowledgeSourceLocationRequest request) {
        KnowledgeSource source = requireOwnedSource(userId, sourceId);
        spaceService.requireOwnedSpace(userId, request.spaceId());
        KnowledgeCollection collection = request.collectionId() == null
                ? null
                : collectionService.requireCollectionInSpace(request.collectionId(), request.spaceId());
        source.setSpaceId(request.spaceId());
        source.setCollectionId(collection == null ? null : collection.getId());
        sourceMapper.updateById(source);
        return KnowledgeSourceView.from(source);
    }

    @Transactional
    public void markMediaDeleted(Long userId, Long mediaId) {
        KnowledgeSource source = sourceMapper.selectOne(new QueryWrapper<KnowledgeSource>()
                .eq("owner_user_id", userId)
                .eq("media_id", mediaId));
        if (source == null || STATUS_DELETED.equals(source.getStatus())) return;
        source.setStatus(STATUS_DELETED);
        sourceMapper.updateById(source);
    }

    private KnowledgeSource requireOwnedSource(Long userId, Long sourceId) {
        KnowledgeSource source = sourceMapper.selectById(sourceId);
        if (source == null || STATUS_DELETED.equals(source.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "内容源不存在");
        }
        if (!userId.equals(source.getOwnerUserId())) throw new SecurityException("无权访问该内容源");
        return source;
    }

    private KnowledgeSource findByMediaId(Long mediaId) {
        return sourceMapper.selectOne(new QueryWrapper<KnowledgeSource>().eq("media_id", mediaId));
    }
}
