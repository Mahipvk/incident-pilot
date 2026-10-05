# Runbook: TLS handshake failures for specific partners

## Symptoms
- Only some external partners fail to connect; most are fine.
- edge-gateway logs "TLS handshake failed: no shared cipher" or protocol version errors.
- Often starts right after a security hardening / cipher change.

## Diagnosis
1. Check edge-gateway config reload logs for cipher or protocol changes and their timestamp.
2. Compare failing clients' offered ciphers and TLS versions with the new allowed list.
3. Confirm healthy clients negotiate TLSv1.2+/TLSv1.3 with GCM ciphers.

## Fix
- Do NOT simply re-enable weak ciphers globally. Contact the affected partners to upgrade to TLS 1.2+ with modern ciphers.
- If business-critical, apply a time-boxed exception approved by Information Security, scoped to that partner only.
- Before future cipher changes, scan partner handshakes for weak-cipher usage and notify them in advance.
