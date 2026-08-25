# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability in GITOFY, please report it responsibly.

**Do NOT file a public GitHub Issue for security vulnerabilities.**

### How to Report

1. Email the maintainer directly with details of the vulnerability.
2. Include:
   - Description of the vulnerability
   - Steps to reproduce
   - Potential impact
   - Suggested fix (if any)
   - Affected version

### Response Timeline

- Acknowledgement: within 48 hours
- Initial assessment: within 5 business days
- Fix or mitigation: depends on severity

## Supported Versions

| Version | Supported |
|---------|----------|
| 2.x     | Yes      |
| < 2.0   | No       |

## Security Measures

GITOFY implements:
- Android Keystore-backed credential storage
- HTTPS-only network traffic
- Automatic credential redaction in logs
- ZIP Slip / path traversal protection
- No credentials in Git remote URLs
- Sensitive data excluded from backups
- Secret detection before push
