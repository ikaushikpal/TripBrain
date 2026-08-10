# GitHub Actions Workflows

This directory contains all CI/CD automation for the TripBrain platform.  
There are **5 workflows** — 2 run on Pull Requests (quality gates before merge) and 3 run after merge to `main` (release pipelines).

---

## Workflow Overview

| File                          | Trigger                                   | Runs on    | Purpose                                         |
| ----------------------------- | ----------------------------------------- | ---------- | ----------------------------------------------- |
| `pr-quality-check.yaml`       | PR → `main`                               | PRs only   | Full quality gate — lint, build, test, approval |
| `python-scripts-check.yaml`   | PR → `main` (`scripts/**` changed)        | PRs only   | Python script syntax & import validation        |
| `release-docker-on-main.yaml` | Push → `main` (any path)                  | Post-merge | Semver tagging + main app Docker image build    |
| `release-monitor-app.yaml`    | Push → `main` (`monitor/**` changed)      | Post-merge | Monitor app Docker image build                  |
| `release-render-proxy.yaml`   | Push → `main` (`render-proxy/**` changed) | Post-merge | Render proxy Docker image build                 |

---

## PR → Merge Flow

```
Developer opens Pull Request
         │
         ├─► pr-quality-check.yaml        ← always runs on every PR
         │       ├─ Validate PR title (Conventional Commits)
         │       ├─ Frontend: Prettier → ESLint → TypeCheck → ng build → Vitest
         │       ├─ Backend:  Gradle check (compile + unit tests)
         │       └─ Approval gate (≥1 approval, no CHANGES_REQUESTED)
         │
         ├─► python-scripts-check.yaml    ← only if scripts/** files changed
         │       ├─ Byte-compile all .py files (Python 3.9)
         │       ├─ Run cert-manager CLI: manage_cert.py list
         │       └─ Validate deployer imports (DeploymentConfig, EmailReporter)
         │
         ▼  All checks pass + PR approved → Merge to main
         │
         ├─► release-docker-on-main.yaml  ← always runs after every merge
         │       ├─ Compute semver bump from PR title (feat→minor, fix→patch)
         │       ├─ Create + push git tags (vX.Y.Z, latest)
         │       └─ Build multi-arch Docker image → push ikaushikpal/trip-brain
         │
         ├─► release-monitor-app.yaml     ← only if monitor/trip-brain-monitor/** changed
         │       └─ Build multi-arch Docker image → push ikaushikpal/trip-brain-monitor-app
         │
         └─► release-render-proxy.yaml   ← only if render-proxy/** changed
                 └─ Build multi-arch Docker image → push ikaushikpal/trip-brain-render-proxy
```

---

## Workflow Details

### 1. `pr-quality-check.yaml` — PR Quality Gate

**Triggers:** PR opened, updated (`synchronize`), reopened, title edited, marked ready for review, or any review action (approve / request-changes / dismiss).  
**Skips:** Draft PRs.

| Job                | Description                                                                                                  |
| ------------------ | ------------------------------------------------------------------------------------------------------------ |
| `validate-title`   | Enforces Conventional Commits format on the PR title (`feat:`, `fix:`, `chore:`, `docs:`, etc.)              |
| `frontend-quality` | Full Angular pipeline: `npm ci` → Prettier → ESLint → TypeScript type-check → `ng build` → Vitest unit tests |
| `backend-quality`  | Gradle `check` task with `-PskipFrontend=true` (compiles backend + runs unit tests only)                     |
| `approval-gate`    | Reads all PR reviews via GitHub API — fails if `CHANGES_REQUESTED` exists or no non-author approval          |

> ⚠️ All 4 jobs must pass before GitHub branch protection allows the merge button.

---

### 2. `python-scripts-check.yaml` — Python Scripts Quality Gate

**Triggers:** PRs targeting `main` **only when `scripts/**` files are changed**.  
**Does NOT run on:** Post-merge pushes to `main`. PRs that don't touch `scripts/`.

| Step         | Description                                                                          |
| ------------ | ------------------------------------------------------------------------------------ |
| Byte-compile | `python3 -m py_compile` on every `.py` in `cert-manager/` and `platform-deployer/`   |
| CLI check    | Runs `manage_cert.py list` end-to-end to verify the CLI entry point is functional    |
| Import check | `from deployer.config import DeploymentConfig` — catches broken cross-module imports |

> This acts as a secondary gate on top of `pr-quality-check` specifically for infra script changes.

---

### 3. `release-docker-on-main.yaml` — Main App Release

**Triggers:** Every push to `main` regardless of which files changed.  
**Does NOT run on:** PRs.

| Job                     | Description                                                                                                                                                     |
| ----------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `version-and-tag`       | Reads the merged PR title or commit message, determines semver bump type, creates and pushes `vX.Y.Z` and `latest` git tags                                     |
| `docker-build-and-push` | Builds `linux/amd64` + `linux/arm64` image using GitHub Actions cache, pushes `ikaushikpal/trip-brain:latest` and `ikaushikpal/trip-brain:vX.Y.Z` to Docker Hub |

**Semver bump rules:**

| PR title prefix           | Bump type | Example           |
| ------------------------- | --------- | ----------------- |
| `fix:`                    | Patch     | `1.2.3` → `1.2.4` |
| `feat:`                   | Minor     | `1.2.3` → `1.3.0` |
| `BREAKING CHANGE` in body | Major     | `1.2.3` → `2.0.0` |

> After this workflow pushes a new image digest, the **blue-green deployment cron job** on the OCI server detects the new digest within 1 minute and automatically deploys.

---

### 4. `release-monitor-app.yaml` — Monitor App Release

**Triggers:** Push to `main` **only when `monitor/trip-brain-monitor/**` files change**.  
**Does NOT run on:** PRs. Pushes that don't touch `monitor/`.

Builds and pushes:

- `ikaushikpal/trip-brain-monitor-app:latest`
- `ikaushikpal/trip-brain-monitor-app:X.Y.Z`
- `ikaushikpal/trip-brain-monitor-app:vX.Y.Z`

> ⚠️ Unlike the main app, the monitor container is **not managed by the auto-deployer**. After this workflow finishes, you must manually restart the container on the OCI server:
>
> ```bash
> sudo docker pull ikaushikpal/trip-brain-monitor-app:latest
> sudo docker stop trip-brain-monitor && sudo docker rm trip-brain-monitor
> sudo docker run -d --name trip-brain-monitor --restart unless-stopped \
>   --network platform-network -p 8085:8085 \
>   --env-file /opt/platform/.env \
>   ikaushikpal/trip-brain-monitor-app:latest
> ```

---

### 5. `release-render-proxy.yaml` — Render Proxy Release

**Triggers:** Push to `main` **only when `render-proxy/**` files change**.  
**Does NOT run on:** PRs. Pushes that don't touch `render-proxy/`.

Builds and pushes:

- `ikaushikpal/trip-brain-render-proxy:latest`
- `ikaushikpal/trip-brain-render-proxy:X.Y.Z`
- `ikaushikpal/trip-brain-render-proxy:vX.Y.Z`

> Render.com polls Docker Hub and auto-deploys when a new image is available.

---

## Required GitHub Secrets

Go to **Repository → Settings → Secrets and variables → Actions** and add:

| Secret               | Required by             | Description                                                                                              |
| -------------------- | ----------------------- | -------------------------------------------------------------------------------------------------------- |
| `DOCKERHUB_USERNAME` | All 3 release workflows | Docker Hub account username                                                                              |
| `DOCKERHUB_TOKEN`    | All 3 release workflows | Docker Hub access token (not your password — create one at hub.docker.com → Account Settings → Security) |

---

## Troubleshooting

### A PR check is failing but the code looks fine

1. Check the **Actions** tab on GitHub for the specific failing job and step
2. For `approval-gate` failures — ensure a non-author has approved and no reviewer has `CHANGES_REQUESTED`
3. For `validate-title` failures — rename the PR title to follow `type: description` format (e.g. `feat: add trip sharing`)

### Release workflow didn't run after merging

- Check that the merge commit actually touched a file in the relevant path filter
- `release-monitor-app` only runs if `monitor/trip-brain-monitor/**` changed
- `release-render-proxy` only runs if `render-proxy/**` changed
- `release-docker-on-main` always runs — if it didn't, check the Actions tab for cancellation or concurrency conflicts

### Docker Hub push failed

- Verify `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN` secrets are set correctly in the repo settings
- Check if the Docker Hub token has `Read & Write` scope for the repository
