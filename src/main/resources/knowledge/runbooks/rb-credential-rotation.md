# Runbook: Outbound delivery failures after secret rotation

## Symptoms
- transfer-worker delivery failures jump to ~100% right after a scheduled rotation window.
- Logs show "Auth fail" from partner SFTP hosts or 401 from internal key-service.

## Diagnosis
1. Check auth-service logs for "secret rotation completed" and note the time.
2. Compare with the first transfer-worker "Auth fail" error — if it starts minutes after rotation, the workers are using a cached, now-invalid credential.
3. Look for "secret version mismatch" or credential cache age older than the rotation.

## Fix
- Rolling restart of transfer-worker pods to reload the current secret version.
- Replay the retry-queue once deliveries succeed.
- Permanent fix: use a secrets agent/injector that refreshes credentials on rotation instead of caching until restart.
