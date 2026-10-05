# Postmortem: Partners locked out after cipher hardening (Jun 2026)
Root cause category: TLS_HANDSHAKE
A planned change removed legacy CBC ciphers. Two partners on old client software only supported those ciphers and could no longer complete the TLS handshake. No partner advance notice had been sent.
Fix: time-boxed partner-specific exception approved by InfoSec; partners upgraded within two weeks.
