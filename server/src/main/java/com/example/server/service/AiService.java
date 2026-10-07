package com.example.server.service;

import com.example.server.dto.AgentFeedback;
import com.example.server.dto.AgentState;
import com.example.server.dto.AnalysisMode;
import com.example.server.dto.TaskStatus;
import com.example.server.dto.TaskStage;
import com.example.server.dto.VideoContext;
import com.example.server.dto.VideoEvidenceHit;
import com.example.server.entity.MediaFile;
import com.example.server.mapper.MediaFileMapper;
import com.example.server.service.mode.ModeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/** 视频分析的应用层入口，负责串起上下文构建、AgentLoop 和结果落库。 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    private final MediaFileMapper mediaFileMapper;
    private final VideoPreparationService preparation;
    private final LongVideoContextService longVideoContextService;
    private final AgentLoopService agentLoopService;
    private final AgentCheckpointService checkpointService;
    private final AgentTelemetry telemetry;
    private final MediaService mediaService;
    private final TaskEventService taskEventService;
    private final ModeRegistry modeRegistry;
    private final KnowledgeIngestJobService ingestJobs;
    private final KnowledgeSourceService knowledgeSources;

    public AiService(MediaFileMapper mediaFileMapper,
                     VideoPreparationService preparation,
                     LongVideoContextService longVideoContextService,
                     AgentLoopService agentLoopService,
                     AgentCheckpointService checkpointService,
                     AgentTelemetry telemetry,
                     MediaService mediaService,
                     TaskEventService taskEventService,
                     ModeRegistry modeRegistry,
                     KnowledgeIngestJobService ingestJobs, KnowledgeSourceService knowledgeSources) {
        this.mediaFileMapper = mediaFileMapper;
        this.preparation = preparation;
        this.longVideoContextService = longVideoContextService;
        this.agentLoopService = agentLoopService;
        this.checkpointService = checkpointService;
        this.telemetry = telemetry;
        this.mediaService = mediaService;
        this.taskEventService = taskEventService;
        this.modeRegistry = modeRegistry;
        this.ingestJobs = ingestJobs;
        this.knowledgeSources = knowledgeSources;
    }

    /** 兼容旧调用方:未指定模式时按 GENERAL 分析。 */
    public void asyncAnalyze(Long mediaId, String userGoal) {
        asyncAnalyze(mediaId, userGoal, AnalysisMode.GENERAL);
    }

    public void asyncAnalyze(Long mediaId, String userGoal, AnalysisMode mode) {
        AnalysisMode resolvedMode = mode == null ? AnalysisMode.GENERAL : mode;
        String traceId = telemetry.start(mediaId, userGoal, resolvedMode);
        telemetry.bind(traceId);
        TaskStage currentStage = TaskStage.VIDEO_CONTEXT;
        MediaFile mediaFile = mediaFileMapper.selectById(mediaId);
        if (mediaFile == null) {
            telemetry.flush(traceId);
            telemetry.clear();
            throw new IllegalArgumentException("media does not exist: " + mediaId);
        }

        try {
            // Checkpoint 键 = (mediaId, goalDigest(goal, mode)):不同模式的同一目标互不串键,
            // 因此这里带 mode 读取,不会误取别的模式已完成的结果。
            AgentState agentState = checkpointService.loadResult(mediaId, userGoal, resolvedMode);
            if (agentState != null && agentState.result() != null) {
                persistResult(mediaFile, agentState);
                telemetry.increment(traceId, "checkpointHits", 1);
                indexKnowledge(mediaId);
                return;
            }

            VideoContext videoContext = preparation.prepare(mediaFile, userGoal, traceId, resolvedMode);
            mediaFile.setTranscriptText(videoContext.transcriptText());
            // Knowledge runs on its own durable job; the report only shares the ASR/OCR checkpoint.
            indexKnowledge(mediaId);
            currentStage = TaskStage.AGENT_LOOP;
            taskEventService.publishAnalysis(mediaId, userGoal, resolvedMode,
                    TaskStatus.of(TaskStatus.State.PROCESSING, "多模态上下文已就绪，Agent 开始分析"),
                    TaskStage.AGENT_LOOP);
            long agentStarted = System.nanoTime();
            try {
                agentState = agentLoopService.run(mediaId, videoContext, modeRegistry.of(resolvedMode));
                telemetry.stage(traceId, TaskStage.AGENT_LOOP.name(), agentStarted, true);
            } catch (RuntimeException e) {
                telemetry.stage(traceId, TaskStage.AGENT_LOOP.name(), agentStarted, false);
                throw e;
            }
            persistResult(mediaFile, agentState);
            log.info("agent_analysis_completed traceId={} mediaId={} rounds={}",
                    traceId, mediaId, agentState.round());
        } catch (Exception e) {
            try {
                checkpointService.saveFailure(mediaId, userGoal, resolvedMode, currentStage, e);
            } catch (RuntimeException checkpointError) {
                e.addSuppressed(checkpointError);
                log.error("agent_failure_checkpoint_write_failed traceId={} mediaId={}",
                        traceId, mediaId, checkpointError);
            }
            log.error("agent_analysis_failed traceId={} mediaId={}", traceId, mediaId, e);
            if (e instanceof AgentLoopService.BudgetExceededException budgetExceeded) {
                throw budgetExceeded;
            }
            throw new IllegalStateException("AI analysis failed", e);
        } finally {
            telemetry.flush(traceId);
            telemetry.clear();
        }
    }

    /** Compatibility entry point for legacy report consumers: enqueue knowledge work only. */
    public void indexKnowledge(Long mediaId) {
        ingestJobs.enqueue(knowledgeSources.requireSourceByMediaId(mediaId));
    }

    public String followUp(Long mediaId, String originalGoal, String question) {
        return followUp(mediaId, originalGoal, question, AnalysisMode.GENERAL);
    }

    public String followUp(Long mediaId,
                           String originalGoal,
                           String question,
                           AnalysisMode mode) {
        AnalysisMode resolvedMode = mode == null ? AnalysisMode.GENERAL : mode;
        VideoContext context = checkpointService.loadContext(mediaId);
        if (context == null) throw new VideoContextNotReadyException();

        String traceId = telemetry.start(mediaId, question, resolvedMode);
        telemetry.bind(traceId);
        try {
            AgentState previous = originalGoal == null
                    ? null : checkpointService.loadResult(mediaId, originalGoal, resolvedMode);
            String followUpGoal = contextualQuestion(originalGoal, previous, question);
            VideoContext followUpContext = new VideoContext(
                    context.source(), followUpGoal, context.segments());
            return agentLoopService.run(
                    mediaId, followUpContext, modeRegistry.of(resolvedMode)).result().toMarkdown();
        } finally {
            telemetry.flush(traceId);
            telemetry.clear();
        }
    }

    public List<VideoEvidenceHit> searchEvidence(Long mediaId, String query) {
        VideoContext context = checkpointService.loadContext(mediaId);
        if (context == null) throw new VideoContextNotReadyException();

        String traceId = telemetry.start(mediaId, query);
        telemetry.bind(traceId);
        long started = System.nanoTime();
        try {
            VideoContext searchContext = new VideoContext(
                    context.source(), query, context.segments());
            List<VideoEvidenceHit> hits =
                    longVideoContextService.searchEvidence(mediaId, searchContext);
            telemetry.stage(traceId, TaskStage.RETRIEVAL.name(), started, true);
            return hits;
        } catch (RuntimeException e) {
            telemetry.stage(traceId, TaskStage.RETRIEVAL.name(), started, false);
            throw e;
        } finally {
            telemetry.flush(traceId);
            telemetry.clear();
        }
    }

    /** 兼容旧调用方:未指定模式时按 GENERAL 暂存修正。 */
    public void stageRevision(AgentFeedback feedback) {
        stageRevision(feedback, AnalysisMode.GENERAL);
    }

    public void stageRevision(AgentFeedback feedback, AnalysisMode mode) {
        AnalysisMode resolvedMode = mode == null ? AnalysisMode.GENERAL : mode;
        AgentFeedback normalized = feedback.normalized(resolvedMode);
        checkpointService.saveFeedback(normalized);

        String goal = normalized.correctedGoal() == null || normalized.correctedGoal().isBlank()
                ? normalized.goal()
                : normalized.correctedGoal().trim();
        AgentState.AgentPlan correctedPlan = normalized.correctedTasks().isEmpty()
                ? null
                : new AgentState.AgentPlan(goal, normalized.correctedTasks());
        checkpointService.stageRevision(normalized.mediaId(), goal, resolvedMode, correctedPlan);
    }

    public String revisionGoal(AgentFeedback feedback) {
        AgentFeedback normalized = feedback.normalized();
        return normalized.correctedGoal() == null || normalized.correctedGoal().isBlank()
                ? normalized.goal()
                : normalized.correctedGoal();
    }

    public void cancelStagedRevision(Long mediaId, String goal) {
        cancelStagedRevision(mediaId, goal, AnalysisMode.GENERAL);
    }

    public void cancelStagedRevision(Long mediaId, String goal, AnalysisMode mode) {
        checkpointService.cancelStagedRevision(mediaId, goal, mode);
    }

    /** 兼容旧调用方:未指定模式时按 GENERAL 复用。 */
    public boolean reuseResult(Long mediaId, Long sourceMediaId, AgentState state) {
        return reuseResult(mediaId, sourceMediaId, state, AnalysisMode.GENERAL);
    }

    public boolean reuseResult(Long mediaId, Long sourceMediaId, AgentState state, AnalysisMode mode) {
        MediaFile mediaFile = mediaFileMapper.selectById(mediaId);
        if (mediaFile == null) throw new IllegalArgumentException("media does not exist: " + mediaId);

        VideoContext sourceContext = checkpointService.loadContext(sourceMediaId);
        if (sourceContext == null) return false;
        checkpointService.saveContext(mediaId, reusableContext(mediaFile.getFilePath(), sourceContext));
        checkpointService.saveResult(mediaId, new AgentState(
                state.goal(), state.plan(), state.result(), state.critique(), state.round()), mode);
        persistResult(mediaFile, state);
        return true;
    }

    private VideoContext reusableContext(String targetSource, VideoContext sourceContext) {
        return new VideoContext(targetSource, "", sourceContext.segments().stream()
                .map(segment -> new VideoContext.VideoSegment(
                        segment.startMs(),
                        segment.endMs(),
                        segment.transcript(),
                        segment.ocrTexts(),
                        segment.evidenceFrames().isEmpty()
                                ? java.util.List.of()
                                : java.util.List.of(targetSource + "#timestampMs=" + segment.startMs())))
                .toList());
    }

    private String contextualQuestion(String originalGoal, AgentState previous, String question) {
        if (originalGoal == null || previous == null || previous.result() == null) return question;
        String previousResult = previous.result().toMarkdown();
        if (previousResult.length() > 4_000) previousResult = previousResult.substring(0, 4_000);
        return """
                这是对同一视频的继续追问。请结合原始视频证据和已有分析回答当前问题。
                原始目标：%s
                已有分析：%s
                当前追问：%s
                """.formatted(originalGoal, previousResult, question);
    }

    private void persistResult(MediaFile mediaFile, AgentState agentState) {
        if (agentState.result() == null) throw new IllegalStateException("Agent 未生成分析结果");
        mediaFile.setAiSummary(agentState.result().toMarkdown());
        mediaFileMapper.updateById(mediaFile);
        mediaService.invalidateUserList(mediaFile.getUserId());
    }
}
