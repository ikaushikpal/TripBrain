# TripBrain Platform Deployer

Automated **Blue-Green deployment pipeline** for the TripBrain Spring Boot application running on Oracle Cloud (OCI) with Podman/Docker.  
Every minute, cron triggers this script. It pulls the latest Docker image, compares its digest against the currently running one, and only deploys if something actually changed — giving you **zero-downtime rolling updates** with automatic Nginx traffic switching and email alerts.

---

## How It Works

```
cron (every 1 min)
    └─► deploy.py
            ├─ Load /opt/platform/.env
            ├─ Pull latest image from Docker Hub
            ├─ Compare image digest with last deployed digest
            │      └─ SAME digest + container healthy → skip, no email
            ├─ Determine active slot  (blue=8081 / green=8082)
            ├─ Start NEW container in the idle slot
            ├─ Health-check /actuator/health (30 retries × 5s)
            │      └─ FAIL → rollback + send FAILED email
            ├─ Reload Nginx upstream → switch traffic
            ├─ Persist new active slot + digest to state files
            ├─ Stop & remove old container
            └─ Send SUCCESS email
```

### Blue-Green Port Mapping

| Slot  | Host Port | Container Port |
|-------|-----------|----------------|
| Blue  | `8081`    | `8080`         |
| Green | `8082`    | `8080`         |

Nginx always points to whichever slot just passed the health check.

---

## Directory Structure

```
scripts/platform-deployer/
├── deploy.py                      # CLI entry point
└── deployer/
    ├── config.py                  # All constants & port config
    ├── docker_manager.py          # Pull, start, stop containers
    ├── health_checker.py          # /actuator/health poller
    ├── nginx_manager.py           # Reload Nginx upstream file
    ├── orchestrator.py            # Blue-green workflow logic
    ├── state_manager.py           # Persist active slot & digest
    ├── log_manager.py             # Save execution logs to disk
    └── email_reporter.py          # Gmail SMTP HTML report
```

---

## Prerequisites

| Requirement | Notes |
|---|---|
| Python 3.8+ | Available as `python3` |
| Docker / Podman | Script uses `docker` — Podman alias is fine |
| Nginx | Must be installed, running, and writable by root |
| `/opt/platform/.env` | All secrets and config (see below) |
| `/opt/platform/state/` | Created automatically on first run |

---

## Environment Variables

All variables are loaded automatically from `/opt/platform/.env`.  
See `.env.example` at the project root for a full reference.

| Variable | Required | Description |
|---|---|---|
| `GMAIL_PASSWORD_TOKEN` | ✅ | Gmail App Password for email reports |
| `IMAGE_NAME` | ❌ | Docker image to deploy (default: `ikaushikpal/trip-brain:latest`) |
| `SPRING_ADMIN_USERNAME` | ❌ | Spring Boot Admin dashboard username |
| `SPRING_ADMIN_PASSWORD` | ❌ | Spring Boot Admin dashboard password |

---

## Running Manually

> All commands must be run as **root** (or with `sudo`) because the script controls Docker, Nginx, and writes to `/opt/platform/state/`.

### One-off deployment trigger

```bash
sudo python3 /opt/platform/platform-deployer/deploy.py
```

### Force a fresh deployment (clear the cached digest)

```bash
# Wipe the stored digest so the next run always re-deploys
sudo rm -f /opt/platform/state/tripbrain-digest

# Then trigger manually
sudo python3 /opt/platform/platform-deployer/deploy.py
```

### Check what slot is currently active

```bash
cat /opt/platform/state/tripbrain-active   # prints: blue or green
cat /opt/platform/state/tripbrain-digest   # prints: sha256:...
```

### Check running containers

```bash
sudo docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

### Tail live deployment logs

```bash
# Cron output log (stdout/stderr from every cron run)
sudo tail -f /var/log/tripbrain-deploy.log

# Detailed per-deployment log files (one file per deployment)
ls -lt /data/tripbrain/platform-deployer-logs/
sudo tail -f /data/tripbrain/platform-deployer-logs/<latest>.log
```

---

## Cron Setup

The deployment poller is managed by `cron-manager.sh` (located at `scripts/cron-manager.sh`).

### Install cron jobs

```bash
sudo bash /opt/platform/cron-manager.sh install
```

This provisions two cron jobs under root's crontab:

| Job | Schedule | Description |
|---|---|---|
| `deploy.py` | Every minute `* * * * *` | Blue-green deployment poller |
| `manage_cert.py` | `0 3 * * *` (03:00 AM) | SSL certificate auto-renewal |

### View installed cron jobs

```bash
sudo bash /opt/platform/cron-manager.sh list

# Or inspect crontab directly
sudo crontab -l
```

### Remove cron jobs

```bash
sudo bash /opt/platform/cron-manager.sh remove
```

### Manually edit crontab

```bash
sudo crontab -e
```

The deployment entry looks like this:

```
* * * * * python3 /opt/platform/platform-deployer/deploy.py >> /var/log/tripbrain-deploy.log 2>&1
```

---

## Email Notifications

Emails are sent **only** when something actually happens:

| Event | Email sent? |
|---|---|
| Image unchanged, container healthy | ❌ No |
| New image deployed successfully | ✅ Yes — SUCCESS |
| Health check failed / any error | ✅ Yes — FAILED |

All emails are marked **high-priority** (`X-Priority: 1`) so they appear flagged as important in Gmail and Outlook.

Gmail setup: generate an **App Password** at [myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords) and put it in your `.env` as `GMAIL_PASSWORD_TOKEN`.

---

## Troubleshooting

### Deployment is not triggering

```bash
# 1. Confirm cron job exists
sudo crontab -l | grep deploy.py

# 2. Check if cron daemon is running
sudo systemctl status crond    # Oracle Linux / RHEL
sudo systemctl status cron     # Ubuntu / Debian

# 3. Check the cron output log for errors
sudo tail -50 /var/log/tripbrain-deploy.log
```

---

### Container fails health check

```bash
# Check if the container started at all
sudo docker ps -a | grep tripbrain

# View the container's own logs
sudo docker logs tripbrain-blue   # or tripbrain-green
sudo docker logs --tail 100 tripbrain-green

# Manually test the health endpoint from the host
curl -s http://127.0.0.1:8081/actuator/health   # blue slot
curl -s http://127.0.0.1:8082/actuator/health   # green slot
```

---

### Nginx not switching traffic

```bash
# View what port Nginx is currently pointing to
cat /etc/nginx/conf.d/tripbrain-upstream.conf

# Test Nginx config
sudo nginx -t

# Reload Nginx manually
sudo systemctl reload nginx
```

---

### Script crashes with "No such file or directory"

```bash
# Ensure required directories exist
sudo mkdir -p /opt/platform/state
sudo mkdir -p /data/tripbrain/platform-deployer-logs
```

---

### Email not being sent

```bash
# Confirm the variable is present in .env
grep GMAIL_PASSWORD_TOKEN /opt/platform/.env

# Check if the deploy log mentions "Skipping Gmail notification"
grep -i "gmail\|email\|smtp" /var/log/tripbrain-deploy.log | tail -20
```

---

### Force re-deploy the same image (bypass digest check)

```bash
sudo rm -f /opt/platform/state/tripbrain-digest
sudo python3 /opt/platform/platform-deployer/deploy.py
```

---

### Check which image digest is currently deployed

```bash
# What the state file says
cat /opt/platform/state/tripbrain-digest

# What Docker actually has locally
sudo docker inspect --format='{{.Id}}' ikaushikpal/trip-brain:latest
```

If these two values differ, the next cron run will trigger a fresh deployment automatically.

---

## State Files Reference

| File | Description |
|---|---|
| `/opt/platform/state/tripbrain-active` | Current active slot (`blue` or `green`) |
| `/opt/platform/state/tripbrain-digest` | SHA256 digest of the last deployed image |
| `/opt/platform/.env` | All secrets and environment variables |
| `/var/log/tripbrain-deploy.log` | Rolling cron stdout/stderr log |
| `/data/tripbrain/platform-deployer-logs/` | Per-deployment detailed log files |
