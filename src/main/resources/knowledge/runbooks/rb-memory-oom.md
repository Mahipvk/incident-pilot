# Runbook: Pods OOMKilled / Java heap exhaustion

## Symptoms
- Pods restart repeatedly with reason OOMKilled, exit code 137.
- Logs show java.lang.OutOfMemoryError: Java heap space and very high GC overhead before the crash.
- Often correlated with processing very large files.

## Diagnosis
1. Find the stack frame in the OutOfMemoryError — which code was allocating.
2. Check whether large files are read fully into memory (e.g., readFully, readAllBytes, mode=in-memory).
3. Compare heap size (-Xmx) against container memory limit.

## Fix
- Immediate: raise memory limit/heap as a stopgap and restart.
- Real fix: stream large files (process in buffers, e.g., 8MB at a time) instead of loading them into memory; e.g., compute checksums with a streaming digest.
- Add a heap-usage alert at 85%.
