# Postmortem: SFTP proxy outage from full /var (Apr 2026)
Root cause category: DISK_FULL
Debug logging was left on after a troubleshooting session. The debug log had no rotation and grew to fill /var. The proxy could not write audit records, so every new session was closed after login.
Fix: truncated the log, disabled debug, added logrotate, moved logs to a data mount.
