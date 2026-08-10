# TripBrain Platform Monitor

A **Spring Boot Admin** dashboard server that provides real-time visibility into all TripBrain application instances — health, memory, threads, HTTP traffic, log levels, and more.  
Accessible at: **`https://spring.cloud1.mooo.com`**

---

## CI/CD Workflow Structure

There are **5 GitHub Actions workflows**. Each has a specific trigger condition — knowing which one fires when is important for debugging pipeline issues.

### Workflow Overview

| Workflow file                 | Trigger                                         | Purpose                                   |
| ----------------------------- | ----------------------------------------------- | ----------------------------------------- |
| `pr-quality-check.yaml`       | PR opened/updated → `main`                      | Quality gate before merge                 |
| `python-scripts-check.yaml`   | PR with `scripts/**` changes → `main`           | Python script validation before merge     |
| `release-docker-on-main.yaml` | Push/merge → `main` (any path)                  | Build & publish main app Docker image     |
| `release-monitor-app.yaml`    | Push/merge → `main` (`monitor/**` changed)      | Build & publish monitor Docker image      |
| `release-render-proxy.yaml`   | Push/merge → `main` (`render-proxy/**` changed) | Build & publish render proxy Docker image |

---

### 1. `pr-quality-check.yaml` — PR Quality Gate

**Triggers on:** Every PR opened, updated, reopened, or ready for review targeting `main`. Also re-runs on review actions (approve/request changes/dismiss).  
**Does NOT run on:** Draft PRs. Direct pushes to `main`.

**Jobs:**

| Job                | What it does                                                                                                                                                                |
| ------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `validate-title`   | Enforces <a href="https://www.conventionalcommits.org/" target="_blank" rel="noopener noreferrer">Conventional Commits</a> PR title format (`feat:`, `fix:`, `chore:` etc.) |
| `frontend-quality` | `npm ci` → Prettier → ESLint → TypeScript type-check → `ng build` → Vitest tests                                                                                            |
| `backend-quality`  | Gradle `check` (compile + unit tests, skips frontend build)                                                                                                                 |
| `approval-gate`    | Blocks merge unless at least 1 non-author approval exists and no `CHANGES_REQUESTED` review is active                                                                       |

> **Must pass before merging.** If any job fails, GitHub branch protection prevents the merge.

---

### 2. `python-scripts-check.yaml` — Python Scripts Quality Gate

**Triggers on:** PRs targeting `main` that touch any file under `scripts/**`.  
**Does NOT run on:** Pushes to `main` (post-merge). Changes outside `scripts/`.

**Jobs:**

| Step              | What it does                                                                              |
| ----------------- | ----------------------------------------------------------------------------------------- |
| Byte-compile      | `python3 -m py_compile` on all `.py` files — catches syntax errors                        |
| CLI output check  | Runs `manage_cert.py list` — verifies the CLI entry point works                           |
| Import validation | Imports `DeploymentConfig` and `EmailReporter` from the deployer — catches broken imports |

> This runs **in addition to** `pr-quality-check.yaml` when `scripts/**` files change in a PR.

---

### 3. `release-docker-on-main.yaml` — Main App Release

**Triggers on:** Every push/merge to `main` (no path filter — always runs).  
**Does NOT run on:** PRs.

**Jobs:**

| Job                     | What it does                                                                                                                                              |
| ----------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `version-and-tag`       | Reads merged PR title, determines semver bump (`feat` → minor, `fix` → patch, `BREAKING CHANGE` → major), creates and pushes git tags `vX.Y.Z` + `latest` |
| `docker-build-and-push` | Builds multi-arch (`linux/amd64` + `linux/arm64`) Docker image, pushes `ikaushikpal/trip-brain:latest` + `ikaushikpal/trip-brain:vX.Y.Z` to Docker Hub    |

> Once this pushes a new image digest to Docker Hub, the **cron-based blue-green deployer** on the OCI server automatically picks it up within 1 minute.

---

### 4. `release-monitor-app.yaml` — Monitor App Release

**Triggers on:** Push/merge to `main` **only when files under `monitor/trip-brain-monitor/**` change**.  
**Does NOT run on:** PRs. Changes outside `monitor/`.

**Jobs:**

| Step         | What it does                                                                                        |
| ------------ | --------------------------------------------------------------------------------------------------- |
| Build & Push | Builds multi-arch Docker image, pushes `ikaushikpal/trip-brain-monitor-app:latest` + versioned tags |

> Changing the monitor application (Spring Boot code or `application.yaml`) triggers this. After it pushes, you must **manually restart** the container on the OCI server to pick up the new image (the monitor is not managed by the blue-green deployer).

---

### 5. `release-render-proxy.yaml` — Render Proxy Release

**Triggers on:** Push/merge to `main` **only when files under `render-proxy/**` change**.  
**Does NOT run on:** PRs. Changes outside `render-proxy/`.

**Jobs:**

| Step         | What it does                                                                                         |
| ------------ | ---------------------------------------------------------------------------------------------------- |
| Build & Push | Builds multi-arch Docker image, pushes `ikaushikpal/trip-brain-render-proxy:latest` + versioned tags |

> Render.com auto-deploys when Docker Hub receives a new image.

---

### Full PR → Merge Flow

```
Developer opens PR
       │
       ├─► pr-quality-check.yaml     (always — validate title, build, test, approval gate)
       ├─► python-scripts-check.yaml (only if scripts/** changed)
       │
       ▼  PR approved + all checks pass → Merge to main
       │
       ├─► release-docker-on-main.yaml   (always — version + build main app image)
       ├─► release-monitor-app.yaml      (only if monitor/** changed)
       └─► release-render-proxy.yaml     (only if render-proxy/** changed)
```

---

### Required GitHub Secrets

| Secret               | Used by               | Description             |
| -------------------- | --------------------- | ----------------------- |
| `DOCKERHUB_USERNAME` | All release workflows | Docker Hub login        |
| `DOCKERHUB_TOKEN`    | All release workflows | Docker Hub access token |

---

## Purpose

Spring Boot Admin aggregates the Actuator data exposed by each registered Spring Boot application into a single, live web dashboard. For TripBrain, it monitors:

- **`tripbrain`** — the main backend (blue or green slot, whichever is currently active)
- **`trip-brain-monitor`** — the monitor itself (self-registers)

The dashboard gives you instant visibility into:

- Application UP/DOWN status
- JVM memory, heap usage, GC activity
- Active HTTP request traces
- Environment variables and config properties
- Live log-level changes without restart
- Thread dump inspection
- Health indicator breakdown (DB, Redis, disk, etc.)

---

## Architecture

```
Browser
  │
  ▼  HTTPS (port 443)
Nginx on OCI  (/etc/nginx/sites-available/spring-cloud1)
  │  ssl_certificate: /etc/letsencrypt/live/spring.cloud1.mooo.com/fullchain.pem
  │
  ▼  HTTP proxy_pass (port 8085, internal)
trip-brain-monitor Docker container
  │  Spring Boot Admin SERVER (de.codecentric:spring-boot-admin-starter-server)
  │  Spring Boot Admin CLIENT (self-registers)
  │  platform-network (shared Docker bridge)
  │
  ◄─── tripbrain-blue / tripbrain-green registers via HTTP on platform-network
        management-url: http://tripbrain-{blue|green}:8080/actuator
        health-url:     http://tripbrain-{blue|green}:8080/actuator/health
```

---

## Nginx Configuration

The monitor is served via the `spring-cloud1` Nginx virtual host on the OCI server.

```nginx
# /etc/nginx/sites-available/spring-cloud1

server {
    listen 80;
    server_name spring.cloud1.mooo.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    server_name spring.cloud1.mooo.com;

    ssl_certificate     /etc/letsencrypt/live/spring.cloud1.mooo.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/spring.cloud1.mooo.com/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:8085;

        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;

        proxy_http_version 1.1;
        proxy_set_header Upgrade    $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

> **Important**: `forward-headers-strategy: framework` is set in `application.yaml` so that Spring Boot Admin correctly reads the `X-Forwarded-Proto: https` header from Nginx and generates HTTPS links in the UI.

---

## Environment Variables

All variables are loaded from `/opt/platform/.env`. See <a href="../../.env.example" target="_blank" rel="noopener noreferrer">`.env.example`</a> at the project root.

| Variable                       | Default                          | Description                            |
| ------------------------------ | -------------------------------- | -------------------------------------- |
| `ADMIN_DASHBOARD_PORT`         | `8085`                           | Port the Spring Boot server listens on |
| `SPRING_ADMIN_USERNAME`        | `admin`                          | Dashboard login username               |
| `SPRING_ADMIN_PASSWORD`        | `admin123`                       | Dashboard login password               |
| `SPRING_BOOT_ADMIN_PUBLIC_URL` | `https://spring.cloud1.mooo.com` | Public base URL shown in the UI        |

---

## Running the Container

```bash
# Stop and remove any existing instance
sudo docker stop trip-brain-monitor && sudo docker rm trip-brain-monitor

# Start fresh — reads all config from /opt/platform/.env
sudo docker run -d \
  --name trip-brain-monitor \
  --restart unless-stopped \
  --network platform-network \
  -p 8085:8085 \
  --env-file /opt/platform/.env \
  ikaushikpal/trip-brain-monitor-app:latest
```

> **`--network platform-network` is mandatory.** Without it the monitor container cannot resolve `tripbrain-blue` / `tripbrain-green` by DNS name, and registered instances will immediately show `OFFLINE`.

---

## Building Locally

```bash
cd monitor/trip-brain-monitor

# Build the JAR
./gradlew bootJar

# Build the Docker image
docker build -t ikaushikpal/trip-brain-monitor-app:latest .

# Push to Docker Hub
docker push ikaushikpal/trip-brain-monitor-app:latest
```

---

## What Can Break

### 1. SSL Certificate for `spring.cloud1.mooo.com` Expires

The certificate is managed by the cert-manager cron job (see <a href="../../scripts/cert-manager/README.md" target="_blank" rel="noopener noreferrer">`scripts/cert-manager/README.md`</a>).

| Layer                    | What breaks                                                                                                |
| ------------------------ | ---------------------------------------------------------------------------------------------------------- |
| Browser → Nginx          | `NET::ERR_CERT_DATE_INVALID` — dashboard is completely inaccessible                                        |
| Nginx startup            | Nginx refuses to start if the cert file is unreadable                                                      |
| `tripbrain` registration | If the admin URL points to `https://` and cert is invalid, registration fails with `SSLHandshakeException` |

**Fix:**

```bash
# Renew the cert
sudo python3 /opt/platform/cert-manager/manage_cert.py spring

# Restore SELinux labels (Oracle Linux requirement)
sudo restorecon -RFv /etc/letsencrypt

# Reload Nginx
sudo nginx -t && sudo systemctl reload nginx
```

---

### 2. Monitor Container Not on `platform-network`

Registered instances show `OFFLINE` immediately after registration. The monitor can't poll `http://tripbrain-blue:8080/actuator/health` because DNS resolution fails.

**Check:**

```bash
sudo docker inspect trip-brain-monitor \
  --format '{{json .NetworkSettings.Networks}}' | python3 -m json.tool
# Must show "platform-network" in the output
```

**Fix:**

```bash
sudo docker stop trip-brain-monitor && sudo docker rm trip-brain-monitor
# Re-run the docker run command with --network platform-network
```

---

### 3. `tripbrain` Not Appearing on Dashboard

Registration fails silently if credentials don't match or the admin URL is unreachable.

**Check:**

```bash
# Confirm tripbrain container can reach the monitor by name
sudo docker exec tripbrain-green wget -qO- http://trip-brain-monitor:8085/actuator/health

# Check tripbrain logs for registration errors
sudo docker logs tripbrain-green 2>&1 | grep -i "admin\|register\|401\|connect" | tail -30
```

**Common causes:**

| Cause                                          | Fix                                                                                       |
| ---------------------------------------------- | ----------------------------------------------------------------------------------------- |
| Wrong credentials in `.env`                    | Ensure `SPRING_ADMIN_USERNAME`/`SPRING_ADMIN_PASSWORD` match on both sides                |
| Monitor container not on `platform-network`    | Re-run with `--network platform-network`                                                  |
| `SPRING_BOOT_ADMIN_URL` pointing to public URL | Must be `http://trip-brain-monitor:8085` (internal), not `https://spring.cloud1.mooo.com` |

---

### 4. Dashboard Accessible But Shows All Instances as `UNKNOWN`

The monitor is up but can't reach back to the instances to poll health.

**Check:**

```bash
# Test that monitor can reach a tripbrain container's actuator
sudo docker exec trip-brain-monitor \
  wget -qO- http://tripbrain-green:8080/actuator/health
```

**Common causes:**

- `tripbrain` containers are on `platform-network` but monitor is not (or vice versa)
- `SPRING_BOOT_MANAGEMENT_URL` was set to `127.0.0.1` (old config) — must be the container name

---

### 5. Dashboard UI Loads But Shows HTTP Instead of HTTPS Links

The `X-Forwarded-Proto` header is not being passed correctly by Nginx, or `forward-headers-strategy: framework` is missing from `application.yaml`.

**Check:**

```bash
curl -I https://spring.cloud1.mooo.com | grep -i forwarded
```

Verify `application.yaml` has:

```yaml
server:
  forward-headers-strategy: framework
```

---

### 6. Port 8085 Not Reachable on Host

```bash
# Check if the container is running and port is bound
sudo docker ps | grep trip-brain-monitor

# Check if port 8085 is listening on the host
sudo ss -tlnp | grep 8085

# Check OCI firewall — VCN Security List must allow port 8085 inbound
# (or restrict to localhost only since Nginx proxies it)
```

---

## Troubleshooting Checklist

```bash
# 1. Is the container running?
sudo docker ps | grep trip-brain-monitor

# 2. Is it on platform-network?
sudo docker inspect trip-brain-monitor --format '{{json .NetworkSettings.Networks}}'

# 3. Is port 8085 listening?
sudo ss -tlnp | grep 8085

# 4. Is Nginx passing traffic correctly?
sudo nginx -t
curl -I https://spring.cloud1.mooo.com

# 5. Is the SSL cert valid?
echo | openssl s_client -connect spring.cloud1.mooo.com:443 \
  -servername spring.cloud1.mooo.com 2>/dev/null | openssl x509 -noout -dates

# 6. Can the monitor reach tripbrain internally?
sudo docker exec trip-brain-monitor \
  wget -qO- http://tripbrain-green:8080/actuator/health

# 7. Can tripbrain reach the monitor?
sudo docker exec tripbrain-green \
  wget -qO- http://trip-brain-monitor:8085/actuator/health

# 8. View monitor container logs
sudo docker logs --tail 100 trip-brain-monitor
```
