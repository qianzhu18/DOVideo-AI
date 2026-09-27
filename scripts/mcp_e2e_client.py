#!/usr/bin/env python3
"""End-to-end MCP client for the DoVideo knowledge adapter.

Speaks the stateless subset of MCP Streamable HTTP against POST /mcp:
initialize -> notifications/initialized -> tools/list -> tools/call.

Usage:
    python3 scripts/mcp_e2e_client.py --url http://127.0.0.1:9091/mcp \
        --token <MCP client token> [--query "语音助手能听懂哪些指令"]

Exit code 0 means every step passed; this script is the P5 acceptance test.
"""

import argparse
import json
import sys
import urllib.request


class McpClient:
    def __init__(self, url, token):
        self.url = url
        self.token = token
        self.next_id = 1

    def post(self, payload, expect_status=200):
        request = urllib.request.Request(
            self.url,
            data=json.dumps(payload).encode(),
            headers={
                "Content-Type": "application/json",
                "Accept": "application/json, text/event-stream",
                "Authorization": f"Bearer {self.token}",
            },
            method="POST",
        )
        with urllib.request.urlopen(request, timeout=60) as response:
            assert response.status == expect_status, f"HTTP {response.status}"
            body = response.read().decode()
            return json.loads(body) if body.strip() else None

    def request(self, method, params=None):
        payload = {"jsonrpc": "2.0", "id": self.next_id, "method": method}
        self.next_id += 1
        if params is not None:
            payload["params"] = params
        response = self.post(payload)
        assert "error" not in response, f"{method} failed: {response['error']}"
        return response["result"]

    def notify(self, method):
        self.post({"jsonrpc": "2.0", "method": method}, expect_status=202)

    def initialize(self):
        result = self.request("initialize", {
            "protocolVersion": "2025-03-26",
            "capabilities": {},
            "clientInfo": {"name": "dovideo-e2e-client", "version": "0.1.0"},
        })
        self.notify("notifications/initialized")
        return result

    def tools_list(self):
        return self.request("tools/list")["tools"]

    def call_tool(self, name, arguments):
        result = self.request("tools/call", {"name": name, "arguments": arguments})
        assert not result.get("isError"), f"tool {name} errored: {result}"
        return json.loads(result["content"][0]["text"])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", default="http://127.0.0.1:9091/mcp")
    parser.add_argument("--token", required=True)
    parser.add_argument("--query", default="语音助手能听懂哪些动作指令")
    parser.add_argument("--space-id", type=int, default=None)
    args = parser.parse_args()

    client = McpClient(args.url, args.token)

    init = client.initialize()
    print(f"[1] initialize ok: {init['serverInfo']['name']} v{init['serverInfo']['version']}, "
          f"protocol {init['protocolVersion']}")

    tools = client.tools_list()
    print(f"[2] tools/list ok: {[t['name'] for t in tools]}")
    assert {t["name"] for t in tools} >= {
        "list_knowledge_spaces", "search_video_knowledge", "get_video_evidence"}

    spaces = client.call_tool("list_knowledge_spaces", {})
    print(f"[3] list_knowledge_spaces ok: {[(s['id'], s['name']) for s in spaces]}")
    # No --space-id means "search all spaces" — the assistant-friendly default.
    search_args = {"query": args.query, "topK": 3}
    if args.space_id:
        search_args["spaceId"] = args.space_id

    hits = client.call_tool("search_video_knowledge", search_args)
    print(f"[4] search_video_knowledge ok: {len(hits)} hit(s)")
    for hit in hits:
        print(f"    [{hit['startSec']}-{hit['endSec']}s] {hit['title']} "
              f"({hit['matchType']}, {hit['score']:.3f})")
    assert hits, "expected at least one evidence hit for the demo query"

    # get_video_evidence 只适用于视频命中；脚本命中没有 mediaId。
    video_hits = [h for h in hits if h.get("mediaId") is not None]
    assert video_hits, "expected at least one VIDEO hit with mediaId"
    evidence = client.call_tool("get_video_evidence", {
        "mediaId": video_hits[0]["mediaId"],
        "startMs": video_hits[0]["startMs"],
        "endMs": video_hits[0]["endMs"]})
    print(f"[5] get_video_evidence ok: {len(evidence)} row(s) for media {video_hits[0]['mediaId']} ({video_hits[0]['sourceType']})")
    for row in evidence:
        print(f"    [{row['startSec']}-{row['endSec']}s] {(row.get('transcript') or '')[:60]}")
    assert evidence, "expected at least one raw evidence row"

    print("\nE2E PASSED: external assistant listed spaces, searched evidence, "
          "and pulled timestamped raw evidence through MCP.")


if __name__ == "__main__":
    try:
        main()
    except AssertionError as e:
        print(f"E2E FAILED: {e}", file=sys.stderr)
        sys.exit(1)
