# Security Scan and Remaining Upgrade Work

## Actual CI Result

The first expanded [GitHub Actions run](https://github.com/Aashish-cs/LedgerOne/actions/runs/42160643613)
passed backend unit/PostgreSQL tests, frontend build/lint/npm audit, Kafka/DB smoke
and Terraform/Kubernetes validation. The image security job **failed**. Its raw
[scanner log](../tests/performance/results/ci-container-scan-first.txt) is retained.

It reported 2 OS-package and 19 Java findings in the backend image, and 51 OS
findings in the frontend, at the configured HIGH/CRITICAL threshold. Findings are
not the same as proven exploitable application paths. They are not ignored or
changed to passing just to make CI green.

## Compatible Remediation

`backend/pom.xml` overrides the final Boot 3.5 dependency set with compatible
security updates: Jackson BOM 2.21.8, Tomcat 10.1.61, HttpCore 5.4.3, PostgreSQL
42.7.12, LZ4 1.11.4 and Snappy 1.1.10.9. Versions were checked against Maven Central
and scanner fixed-version output. Full unit/PostgreSQL tests are rerun after the
changes; Kafka behavior is also exercised by the independent CI smoke job.

The backend runtime runs an OS package upgrade while building the Temurin image;
the Nginx runtime runs `apk upgrade --no-cache`. Both stay non-root at runtime.
This refreshes distro security packages without changing distribution major lines.
Container rebuilds are needed for later package fixes; passing one scan would not
be a permanent security guarantee. Reproducible image digests/SBOM attestations and
regular dependency updates are future work.

## Spring MVC Findings Not Silently Suppressed

Spring 6.2.19 is the latest public 6.2 artifact observed in Maven Central. The
scanner flags two advisories whose public fixed version is Spring 7.0.9 (6.2.20
is enterprise-only). Do **not** force Spring 7 jars into Boot 3.5 without a proper
framework migration.

- [CVE-2026-47884](https://spring.io/security/cve-2026-47884/): the vendor describes
  an XSLT-view rendering scenario with an implicit view name and wildcard mapping.
  This codebase uses REST controllers/DTOs and contains no XsltView, ViewResolver
  configuration or plain `@Controller` view endpoints. Current source review did
  not identify the described prerequisite path. This is an applicability assessment,
  not a patched-library or penetration-test claim.
- [CVE-2026-47890](https://spring.io/security/cve-2026-47890/): the vendor describes
  SSE view fragments carrying attacker-controlled content. This application has
  no SSE/view-fragment endpoints. Again, no affected path was found in the reviewed
  source; this does not remove the affected package version from the image.

The vendor rates these MEDIUM/LOW, while the scanner's selected data source
classifies them more severely. The existing HIGH/CRITICAL CI gate is deliberately
left unchanged, with no ignore list, VEX override or `continue-on-error` added.
A clean-scan claim is not made. Before requiring a clean library inventory for
release, migrate to a supported Boot/Spring line and revalidate Jackson, SpringDoc,
JPA, security, Kafka, migrations and all frontend flows, or review licensed vendor
patch access. That is a separate cross-framework change, not a safe one-line fix.

## Other Application Limitations

LocalStorage access/refresh tokens are exposed to same-origin XSS; prefer a reviewed
HttpOnly cookie/session design with CSRF protection when changing authentication.
Public seeded demo credentials are not production identities. Rate limits, MFA,
account lockout, managed secret rotation, CSP review, backup/restore drills and
broader authorization tests remain work. Data uses simulated money only.
