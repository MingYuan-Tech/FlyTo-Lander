# Security Policy

FlyTo Lander handles a security-sensitive Android capability (mock location).
We take security reports seriously and appreciate responsible disclosure.

## Supported Versions

Only the latest released version receives security fixes. Older versions are
considered unsupported.

| Version | Supported |
|---------|-----------|
| latest  | Yes       |
| older   | No        |

## Reporting a Vulnerability

**Please do NOT open a public GitHub issue for security vulnerabilities.**

Public issues are visible to everyone the moment they are filed, which gives
attackers a head start before a fix can be shipped. Instead, please report
privately through one of the channels below.

### Preferred: GitHub Security Advisory

Use the private vulnerability reporting flow built into GitHub:

  https://github.com/MingYuan-Tech/FlyTo-Lander/security/advisories/new

This creates a private discussion visible only to the maintainers and yourself,
and lets us coordinate a fix and CVE (if applicable) before public disclosure.

### What to include

To help us reproduce and triage quickly, please include:

- A clear description of the issue and its impact
- Steps to reproduce (commands, payloads, device model, Android version)
- FlyTo Lander version (from app `versionName` / Release tag)
- FlyTo macOS app version, if relevant
- Any proof-of-concept code or logs (please redact personal data)
- Your name / handle for credit, if you want to be acknowledged

## Our Commitment

| Phase | Target |
|-------|--------|
| Acknowledge receipt | within **7 days** of report |
| Initial assessment | within **14 days** of report |
| Fix for critical issues | within **30 days** of confirmation |
| Public disclosure | coordinated with reporter; default 90 days after fix released |

If we cannot meet these targets (e.g. complex root-cause analysis required),
we will keep you updated.

## Scope

In scope:

- The FlyTo Lander Android application source in this repository
- Build / release pipeline configuration under `.github/workflows/`
- Signed APK artifacts published in this repository's Releases

Out of scope:

- The FlyTo macOS application (private repository — report there separately)
- Android OS itself (report to Google via the Android Security Bulletin program)
- `adb` / Android platform-tools (report to Google)
- Third-party applications that detect or block mock locations (this is expected
  Android behaviour and not a FlyTo Lander defect)

## Safe Harbor

We will not pursue legal action against researchers who:

- Make a good-faith effort to follow this policy
- Do not access, modify, or destroy data belonging to others
- Do not degrade service for other users
- Give us reasonable time to fix the issue before public disclosure

## PGP Key

A PGP key for encrypted communication outside GitHub Security Advisory will be
published here once generated.

```
[TBD]
```

Until then, please rely on GitHub Security Advisory, which provides encryption
in transit and at rest via GitHub.

---

Maintained by MingYuan Tech Studio.
