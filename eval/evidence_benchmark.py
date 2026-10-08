#!/usr/bin/env python3
"""Strict benchmark on an explicitly mapped, authenticated corpus; no production claims.

Credentials are read from env KB_EVAL_TOKEN or KB_EVAL_USERNAME / KB_EVAL_PASSWORD.
The corpus map records mediaKey, mediaId, sourceId and index/profile versions from bootstrap.
Holdout is opt-in and must not be used for tuning. Reports retain raw result identities/times.
"""
import argparse
import hashlib
import json
import math
import os
import statistics
import subprocess
import time
from pathlib import Path
from evidence_metrics import score_case, summarize, validate_golden
from knowledge_eval import http_json, login

def percentile(values, fraction):
    """Nearest-rank percentile; small samples must not silently discard the tail."""
    ordered = sorted(values)
    return ordered[max(0, math.ceil(len(ordered) * fraction) - 1)]

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--base-url', default='http://127.0.0.1:19090')
    parser.add_argument('--golden', type=Path, default=Path('eval/evidence-golden-v1.json'))
    parser.add_argument('--corpus-map', required=True, type=Path)
    parser.add_argument('--strategies', nargs='+', default=['vector','keyword','hybrid'])
    parser.add_argument('--split', choices=['dev','holdout','all'], default='dev')
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    golden = json.loads(args.golden.read_text())
    validate_golden(golden, root)
    mapping = json.loads(args.corpus_map.read_text())
    if mapping['corpusSha256'] != golden['corpus']['sha256']:
        raise SystemExit('Corpus mapping belongs to a different corpus version')
    token = os.environ.get('KB_EVAL_TOKEN') or login(args.base_url,
        os.environ['KB_EVAL_USERNAME'], os.environ['KB_EVAL_PASSWORD'])
    media_keys = {str(v['mediaId']):v['mediaKey'] for v in mapping['videos']}
    cases = [c for c in golden['cases'] if args.split == 'all' or c['split'] == args.split]
    rows = []
    for strategy in args.strategies:
        for case in cases:
            started = time.perf_counter()
            data = http_json(args.base_url, '/knowledge/search', {
                'spaceId':mapping['spaceId'],'query':case['question'],'topK':golden['defaultK'],
                'strategy':strategy}, token)
            if data.get('code') != 0: raise RuntimeError('Search failed: ' + case['id'])
            hits = data['data']
            row = score_case(case, hits, media_keys, golden['defaultK'])
            row.update(strategy=strategy, seconds=time.perf_counter()-started, hits=hits)
            rows.append(row)
    results = {}
    for strategy in args.strategies:
        selected = [r for r in rows if r['strategy']==strategy]
        latency = sorted(r['seconds'] for r in selected)
        results[strategy] = {**summarize(selected), 'p50Seconds':statistics.median(latency),
            'p95Seconds':percentile(latency, .95)}
    manifest = {'date':time.strftime('%Y-%m-%d'), 'gitSha':subprocess.check_output(
        ['git','rev-parse','HEAD'], cwd=root, text=True).strip(),
        'goldenSha256':hashlib.sha256(args.golden.read_bytes()).hexdigest(),
        'split':args.split,'mapping':mapping,'modelQualityClaim':mapping.get('realEmbedding',False),
        'productionCapacityClaim':False,'billingCost':None,'latencyPercentileMethod':'nearest-rank-ceil',
        'workingTreeDirty':bool(subprocess.check_output(['git','status','--porcelain'],cwd=root,text=True).strip())}
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps({'manifest':manifest,'metrics':results,'cases':rows},ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(results, ensure_ascii=False))

if __name__ == '__main__': main()
