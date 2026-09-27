package com.example.server.controller;

import com.example.server.common.Result;
import com.example.server.dto.KnowledgeAnswer;
import com.example.server.dto.KnowledgeAskRequest;
import com.example.server.service.AuthService;
import com.example.server.service.KnowledgeAnswerService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Cross-video RAG generation endpoint; all returned claims must carry verified evidence. */
@RestController
@RequestMapping("/knowledge")
public class KnowledgeAnswerController {

    private final KnowledgeAnswerService answerService;

    public KnowledgeAnswerController(KnowledgeAnswerService answerService) {
        this.answerService = answerService;
    }

    @PostMapping("/ask")
    public Result<KnowledgeAnswer> ask(
            @RequestAttribute(AuthService.REQUEST_USER_ID) Long userId,
            @Valid @RequestBody KnowledgeAskRequest request) {
        return Result.ok(answerService.ask(userId, request));
    }
}
