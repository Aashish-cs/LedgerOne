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

The follow-up [CI run for application revision 27988a8](https://github.com/Aashish-cs/LedgerOne/actions/runs/42161406716)
completed after these changes. Backend tests, frontend build/lint/audit, Kafka/DB
smoke and infrastructure validation all passed. The frontend image passed the
HIGH/CRITICAL scan; the backend scan fell from 21 findings to **two Spring MVC
findings** (CVE-2026-47884 and CVE-2026-47890). There were zero remaining backend OS
findings at that threshold. The overall workflow remains **failed**, not green.
[Actual job status](../tests/performance/results/ci-status.json) and
[remaining scan output](../tests/performance/results/ci-container-scan-after-updates.txt).
No exception was added to the gate. A clean-inventory release is still blocked.

`backend/pom.xml` overrides the final Boot 3.5 dependency set with compatible
security updates: Jackson BOM 2.21.8, Tomcat 10.1.61, HttpCore 5.4.3, PostgreSQL
42.7.12, LZ4 1.11.4 and Snappy 1.1.10.9. Versions were checked against Maven Central
and scanner fixed-version output. Full unit/PostgreSQL tests are rerun after the
changes; Kafka behavior is also exercised by the independent CI smoke job.

The patched dependency set passed 28 unit and 11 real PostgreSQL integration
cases locally and in CI. [Latest local log](../tests/performance/results/maven-security-update.txt).

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

## November 6 Application-Layer Hardening

`SecurityStorageUtils.resolveUploadFile` resolves names beneath the fixed
`/tmp/ledgerone_uploads` directory. It removes standard double-dot sequences,
accepts only bounded ASCII basenames, calls `File.getCanonicalPath()` on base and
target, and compares canonical Path components rather than a naive string prefix.
Invalid names and canonical symlink breakouts throw `SecurityException`.
No upload/local-file routes or services exist in the audited backend, so none
could be refactored to call it. This is a guard for a future file-handling feature,
not a claim that LedgerOne already supports uploads.

Canonical validation alone does not eliminate filesystem TOCTOU races. A future
upload feature must use a private service-owned base, server-generated unique
storage IDs, race-safe/no-follow file operations, size/type limits and explicit
overwrite rules. Removing dots can collapse distinct original names, so sanitized
names alone must not be used as unique storage identifiers.

`SecurityConfig` now uses ordered, case-sensitive `AntPathRequestMatcher` rules.
All Actuator paths, admin namespaces, Swagger and OpenAPI require ADMIN. Existing
auth operations and corresponding reserved v1 auth paths are public only for POST;
trading namespaces require authentication. The permissive global OPTIONS rule was
removed; the configured CORS filter still handles allowed preflights. Direct JSON
401/403 responses avoid a container error redispatch changing authorization status.
The requested Ant matcher is deprecated upstream; a future Spring 7 migration must
also migrate this matcher rather than assume the class remains available.

The actual application routes remain `/api/auth` and `/api/orders`; v1 URL rules
do not create new controllers. All Actuator health URLs require ADMIN, so Spring's
additional minimal GET `/livez` and `/readyz` probes are enabled and Render,
Kubernetes and CI probe configuration was updated together.

The full build passed 106 test cases (92 unit/web-slice including parameterized
invocations, 14 real PostgreSQL/server cases), with zero failures/errors/skips.
[Raw run](../tests/security/backend-verification.txt). These are defense-in-depth
changes, **not patches for CVE-2026-47884 or CVE-2026-47890**: they do not upgrade
the affected Spring library or address SSE view-fragment rendering. The existing
scanner gate and remaining framework-upgrade requirement are unchanged.

## Other Application Limitations

LocalStorage access/refresh tokens are exposed to same-origin XSS; prefer a reviewed
HttpOnly cookie/session design with CSRF protection when changing authentication.
Public seeded demo credentials are not production identities. Rate limits, MFA,
account lockout, managed secret rotation, CSP review, backup/restore drills and
broader authorization tests remain work. Data uses simulated money only.
