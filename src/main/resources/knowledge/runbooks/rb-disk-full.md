# Runbook: Host disk full (/var at 100%)

## Symptoms
- Service health shows /var at or near 100%.
- "No space left on device" / ENOSPC errors; sessions dropped right after login because audit records cannot be written.

## Diagnosis
1. Confirm with df -h /var on the host.
2. Find the largest files: du -sh /var/log/* | sort -h. Debug logs without rotation are the usual culprit.
3. Check whether logrotate is configured for every log file the service writes.

## Fix
- Immediate: compress or truncate the oversized debug log (do not delete open files — truncate them).
- Add logrotate config, turn debug logging off in production, and move application logs to a dedicated data mount.
- Add an alert at 80% disk usage.
