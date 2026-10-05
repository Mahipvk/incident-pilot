# Runbook: 504 Gateway Timeout on file uploads

## Symptoms
- edge-gateway returns 504 after exactly 60 seconds (gateway upstream timeout).
- upload-service p95 latency near 60s; worker thread pool close to exhausted.
- Usually concentrated on one client sending very large files.

## Diagnosis
1. Check edge-gateway logs for "504 upstream timeout" and note client and file size.
2. Check upload-service logs for "not chunked" or "still reading body" — a single-part upload of multiple GB cannot finish inside the 60s gateway timeout.
3. Check whether the client is using the chunked upload API (/api/v2/files/chunks). Small files from other clients succeeding confirms it is client-specific.

## Fix
- Short term: ask the partner to switch to chunked uploads (max 100MB per part), or temporarily raise the gateway timeout for that route.
- Protect the service: cap single-part body size and return 413 so threads are not held for minutes.
- Long term: enforce chunking for files over 100MB and add an alert on thread pool saturation.
