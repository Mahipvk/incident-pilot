# Postmortem: Large uploads timing out for one partner (Nov 2025)
Root cause category: UPSTREAM_TIMEOUT
A partner migrated to a new client tool that sent 2-3GB files as a single multipart part. Each request held an upload-service thread for over 60s, the gateway timed out with 504, and thread pool saturation slowed other clients.
Fix: partner moved to the chunked upload API; we added a 413 for single parts over 500MB.
Lesson: a 504 at exactly the gateway timeout points to slow upstream request processing, not a network outage.
