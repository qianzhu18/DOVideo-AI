#!/usr/bin/env python3
"""Golden-set evaluation harness for the cross-video knowledge search API.

Reads eval/golden.json, calls POST /knowledge/search for every case under each recall
strategy, and reports Recall@K, MRR, no-evidence refusal correctness and latency.

Metrics
- Recall@K: fraction of answerable cases whose expected evidence (mediaId match AND time
  overlap) appears within the top-K results.
- MRR: 1/rank of the first expected-evidence hit, averaged over answerable cases.
- Refusal correctness: fraction of unanswerable cases that returned zero results
  ("no supporting evidence" is the correct answer when the corpus has none).
- Latency: p50/p95 wall-clock of the search call.

Usage
    python3 eval/knowledge_eval.py --base-url http://127.0.0.1:9090 \
        --username e2eprobe --password '...' --golden eval/golden.json \
        --strategies vector keyword hybrid --k 1 3 5
"""

import argparse
import json
import statistics
import time
import urllib.request


def http_json(base_url, path, payload=None, token=None, method=None):
    url = base_url.rstrip("/") + path
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    request = urllib.request.Request(url, data=data, method=method or ("POST" if data else "GET"))
    request.add_header("Content-Type", "application/json")
    if token:
        request.add_header("Authorization", f"Bearer {token}")
    with urllib.request.urlopen(request, timeout=120) as response:
        return json.loads(response.read().decode("utf-8"))


def login(base_url, username, password):
    result = http_json(base_url, "/user/login",
                       {"username": username, "password": password})
    if result.get("code") != 0:
        raise SystemExit(f"login failed: {result}")
    return result["data"]["token"]


def overlap(hit, expected):
    return (hit.get("mediaId") == expected["mediaId"]
            and hit["startMs"] < expected["endMs"]
            and hit["endMs"] > expected["startMs"])


def hit_rank(hits, case):
    """1-based rank of the first hit matching the case's expected evidence, or None."""
    if case.get("expectEmpty"):
        return None
    expectations = case.get("expectAny") or [{
        "mediaId": case["expectMediaId"],
        "startMs": case["expectStartMs"],
        "endMs": case["expectEndMs"],
    }]
    for rank, hit in enumerate(hits, start=1):
        if any(overlap(hit, expected) for expected in expectations):
            return rank
    return None


def evaluate_case(base_url, token, space_id, query, strategy, top_k):
    started = time.perf_counter()
    result = http_json(base_url, "/knowledge/search",
                       {"spaceId": space_id, "query": query, "topK": top_k,
                        "strategy": strategy},
                       token=token)
    elapsed_ms = (time.perf_counter() - started) * 1000
    if result.get("code") != 0:
        raise SystemExit(f"search failed for {query!r}: {result}")
    return result.get("data") or [], elapsed_ms


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base-url", default="http://127.0.0.1:9090")
    parser.add_argument("--username", required=True)
    parser.add_argument("--password", required=True)
    parser.add_argument("--golden", default="eval/golden.json")
    parser.add_argument("--strategies", nargs="+",
                        default=["vector", "keyword", "hybrid"],
                        choices=["vector", "keyword", "hybrid"])
    parser.add_argument("--k", nargs="+", type=int, default=[1, 3, 5])
    args = parser.parse_args()

    with open(args.golden, encoding="utf-8") as handle:
        golden = json.load(handle)
    space_id = golden["spaceId"]
    cases = golden["cases"]
    token = login(args.base_url, args.username, args.password)

    report = {"generatedAt": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
              "golden": args.golden, "caseCount": len(cases), "strategies": {}}

    for strategy in args.strategies:
        max_k = max(args.k)
        rows = []
        for case in cases:
            hits, elapsed_ms = evaluate_case(
                base_url=args.base_url, token=token, space_id=space_id,
                query=case["question"], strategy=strategy, top_k=max_k)
            rows.append({"case": case, "hits": hits, "latencyMs": elapsed_ms})

        answerable = [row for row in rows if not row["case"].get("expectEmpty")]
        unanswerable = [row for row in rows if row["case"].get("expectEmpty")]

        recall_at_k = {}
        for k in args.k:
            recalled = sum(1 for row in answerable
                           if (rank := hit_rank(row["hits"], row["case"])) and rank <= k)
            recall_at_k[k] = round(recalled / len(answerable), 4) if answerable else 0.0

        reciprocal = [1.0 / (rank := hit_rank(row["hits"], row["case"]))
                      for row in answerable
                      if (rank := hit_rank(row["hits"], row["case"])) is not None]
        mrr = round(sum(reciprocal) / len(answerable), 4) if answerable else 0.0

        correct_refusals = sum(1 for row in unanswerable if not row["hits"])
        refusal_rate = round(correct_refusals / len(unanswerable), 4) if unanswerable else None

        latencies = sorted(row["latencyMs"] for row in rows)
        p50 = round(statistics.median(latencies), 1)
        p95 = round(latencies[max(0, int(len(latencies) * 0.95) - 1)], 1)

        report["strategies"][strategy] = {
            "recallAtK": recall_at_k, "mrr": mrr,
            "refusalCorrectRate": refusal_rate,
            "refusalCorrect": f"{correct_refusals}/{len(unanswerable)}",
            "p50LatencyMs": p50, "p95LatencyMs": p95,
        }
        misses = [row["case"]["id"] for row in answerable
                  if hit_rank(row["hits"], row["case"]) is None]
        false_positives = [row["case"]["id"] for row in unanswerable if row["hits"]]
        report["strategies"][strategy]["answerableMisses"] = misses
        report["strategies"][strategy]["falsePositives"] = false_positives

        print(f"\n=== strategy={strategy} ===")
        print(f"Recall@K: {recall_at_k}  MRR: {mrr}")
        print(f"无证据拒答正确率: {refusal_rate} ({correct_refusals}/{len(unanswerable)})")
        print(f"latency p50={p50}ms p95={p95}ms")
        print(f"answerable misses (case ids): {misses}")
        print(f"false positives on unanswerable (case ids): {false_positives}")

    output = f"eval/results-{time.strftime('%Y%m%d-%H%M%S')}.json"
    with open(output, "w", encoding="utf-8") as handle:
        json.dump(report, handle, ensure_ascii=False, indent=2)
    print(f"\nreport written to {output}")


if __name__ == "__main__":
    main()
