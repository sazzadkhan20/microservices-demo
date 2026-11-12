# CI/CD — build once, promote by tag

Guide §9: *build each artifact once in CI and promote the same image digest.* The repository ships two
GitHub Actions workflows and a server-side deploy bundle. Nothing deploys until you switch it on.

## 1. What runs when

| Trigger | Workflow | What happens |
|---|---|---|
| Pull request, push to `main` / `develop` | `ci.yml` | Java build + all unit/contract tests, frontend lint + type-check + production build, Trivy dependency/secret/config scan, dependency review (PRs), CodeQL (Java + TS), **full-stack system test** (Postgres, RabbitMQ, Redis, MinIO, all services in Docker → `test-security-rbac.mjs` + load smoke). Reports are kept 30 days. |
| Tag `dev-*` | `release.yml` | Tests again at the tagged commit → jars built **once** → 9 images built (`docker/java-runtime.Dockerfile` + frontend), SBOM + provenance attestation, Trivy image scan (fails on fixable CRITICAL) → pushed to GHCR as `:sha-<commit>`, `:<tag>`, `:dev` → **deploy to `dev` environment**. |
| Tag `prod-*` | `release.yml` | **No rebuild.** Looks up the images built for that exact commit (`:sha-<commit>`), fails if the commit was never released to dev, re-tags the same digests `:<tag>` / `:prod` → **deploy to `production` environment** (protected by required reviewers). |

Release manifests (`service=image@sha256:…`) are uploaded as artifacts (dev 90 days, prod 365 days) — the
audit trail of exactly what ran where.

## 2. Releasing

```bash
# 1. ship to dev (after main is green)
git tag dev-2026.11.20-1
git push origin dev-2026.11.20-1

# 2. after dev sign-off, promote THE SAME COMMIT to production
git tag prod-2026.11.20-1 dev-2026.11.20-1^{}
git push origin prod-2026.11.20-1
```

Tag naming: `dev-<anything>` and `prod-<anything>`; using a date + counter keeps them sortable.
Rollback = re-run the release workflow for the previous `prod-*` tag (*Actions → Release → Run workflow*),
or on the server `deploy.sh` rolls back automatically when health checks fail.

## 3. One-time setup (when you are ready to deploy)

1. **GitHub → Settings → Environments**: create `dev` and `production`. On `production` add *Required
   reviewers* (technical lead) and restrict deployment branches/tags to `prod-*`.
2. **Variables** (per environment): `DEPLOY_ENABLED=true`, `DEPLOY_PATH` (default `/opt/qii-hrms`),
   `PUBLIC_URL` (e.g. `https://hrms-dev.qiicreative.com.au`) for the post-deploy smoke check.
3. **Secrets** (per environment): `DEPLOY_SSH_HOST`, `DEPLOY_SSH_USER`, `DEPLOY_SSH_KEY` (private key of a
   deploy-only user), `DEPLOY_SSH_KNOWN_HOSTS` (`ssh-keyscan -H host`), `GHCR_READ_TOKEN` (fine-grained PAT
   or GitHub App token with `read:packages`).
4. **Server** (Docker Engine + compose v2.20+): create `${DEPLOY_PATH}/.env` (mode 600) from your secrets
   manager. Required keys are the `${VAR:?}` entries in `deploy/docker-compose.deploy.yml`; set
   `PLATFORM_ENV=dev|prod`, `PUBLIC_HOST`, and for the dev server `COMPOSE_PROFILES=infra` (+ `MINIO_IMAGE`).
   Production points `*_DB_URL`, `RABBITMQ_HOST`, `REDIS_HOST`, `MINIO_ENDPOINT` at managed services
   (guide §1/§9) and leaves `COMPOSE_PROFILES` empty.
5. Open only 80/443 on the server firewall. Every service, DB, broker and cache stays on the private Docker
   network; Prometheus scrapes `/actuator/prometheus` with `Authorization: Bearer $PLATFORM_METRICS_KEY`.

Production start is refused when demo login is on, the gateway check is off, or a secret is a known
default / too short (`ProdSecretsGuard`, `PLATFORM_ENV=prod`).

## 4. Files

| File | Purpose |
|---|---|
| `.github/workflows/ci.yml` | PR / main quality gates and system test |
| `.github/workflows/release.yml` | `dev-*` build + deploy, `prod-*` promote + deploy |
| `.github/dependabot.yml` | weekly Gradle / npm / Docker / Actions updates (majors held back) |
| `docker/java-runtime.Dockerfile` (+ `.dockerignore`) | runtime-only, non-root image around a CI-built jar |
| `deploy/docker-compose.deploy.yml` | server stack pinned by digest; Caddy HTTPS ingress is the only public port |
| `deploy/deploy.sh` | health-gated roll-forward with automatic rollback, release history |
| `scripts/ci/generate-env.mjs` | throwaway random secrets for CI system tests |
