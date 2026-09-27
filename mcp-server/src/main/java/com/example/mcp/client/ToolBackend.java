package com.example.mcp.client;

/**
 * The three read-only capabilities exposed as MCP tools. Implementations call the
 * authenticated DoVideo API; the protocol layer never depends on the transport.
 * Each method returns pre-formatted JSON text ready for an LLM tool result.
 */
public interface ToolBackend {

    /** Knowledge spaces the upstream account can see. */
    String listSpaces() throws Exception;

    /**
     * Cross-video evidence search inside one space. A null spaceId resolves to the
     * account's default space so casual assistant prompts still work.
     */
    String searchKnowledge(String query, Long spaceId, Integer topK, String strategy) throws Exception;

    /** Raw evidence rows (transcript/OCR/summary per time window) of one media. */
    String videoEvidence(Long mediaId, Long startMs, Long endMs) throws Exception;
}
