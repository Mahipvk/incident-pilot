# Runbook: Message queue backlog / delayed notifications

## Symptoms
- Queue depth for topic file-delivered growing continuously; messages expiring to the DLQ.
- Downstream notifications (emails) arrive hours late.

## Diagnosis
1. Check consumer health — is notification-service running? Look for CrashLoopBackOff.
2. Read the consumer's startup errors: a dependency (SMTP relay, database) being unreachable stops consumption entirely.
3. Producers (transfer-worker) are usually healthy — the backlog is a consumer problem.

## Fix
- Restore the failing dependency (e.g., SMTP relay) or fix consumer config, then let the consumer drain the backlog.
- Scale consumers temporarily to drain faster.
- Replay messages from the DLQ after the backlog clears.
- Make the consumer start even if SMTP is down (retry sending instead of crashing).
