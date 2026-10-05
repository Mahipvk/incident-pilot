# Postmortem: Overnight delivery outage after secret rotation (Feb 2026)
Root cause category: CREDENTIAL_EXPIRED
The quarterly rotation of the outbound SFTP credential completed at 02:00, but transfer-workers cached the old secret until restart. All outbound deliveries failed with Auth fail for 5 hours until the morning on-call restarted the pods.
Fix: rolling restart; later migrated to an injector-based secret refresh.
Lesson: correlate the first auth failure with the rotation timestamp.
