# TripBrain Certificate Manager

Automated **Let's Encrypt SSL certificate issuer and renewal manager** for all TripBrain domains.  
Certbot runs inside a **Docker container** (no host installation needed). The script handles stopping Nginx, running the ACME HTTP-01 challenge, restoring SELinux file labels, and restarting Nginx — all automatically.  
On completion it sends a **high-priority email** report (success or failure).

---

## Infrastructure Context

> This script is designed specifically for an **Oracle Cloud Infrastructure (OCI) ARM-based VM** running **Oracle Linux (minimal install)**. Several decisions in this codebase exist _because of_ that environment.

| Property          | Value                                           |
| ----------------- | ----------------------------------------------- |
| OS                | Oracle Linux 9 (minimal) — RHEL-compatible      |
| Architecture      | `aarch64` (ARM64 Ampere A1)                     |
| Init system       | `systemd`                                       |
| MAC enforcer      | **SELinux** (enforcing by default)              |
| Package manager   | `dnf`                                           |
| Container runtime | **Podman** (aliased as `docker` via `nodocker`) |

---

## Why Certbot Runs Inside Docker

Oracle Linux minimal does **not** ship with Certbot, and installing it via `dnf` on an ARM64 box can pull in mismatched Python dependencies or snap packages that are unavailable on minimal installs.

Running the official `certbot/certbot` Docker image solves all of this:

- No host Python dependency conflicts
- The image supports `aarch64` natively
- Certbot is always the latest stable release
- Zero cleanup needed — the container is ephemeral (`--rm`)

```bash
# What the script runs under the hood for every certbot operation
sudo docker run --rm \
  -p 80:80 \
  -v /etc/letsencrypt:/etc/letsencrypt:Z \
  -v /var/lib/letsencrypt:/var/lib/letsencrypt:Z \
  -v /var/log/letsencrypt:/var/log/letsencrypt:Z \
  docker.io/certbot/certbot:latest \
  certonly --standalone ...
```

The `:Z` volume flag tells Podman/SELinux to relabel the mounted directory as `container_file_t` — which is exactly what causes the SELinux issue described below.

---

## The SELinux Problem (and Why `restorecon` Is Critical)

This is the **most common failure mode** on Oracle Linux.

### What happens

1. Certbot container runs and writes certificates into `/etc/letsencrypt/`
2. Podman's `:Z` volume flag relabels `/etc/letsencrypt` from `httpd_config_t` → `container_file_t`
3. Nginx restarts and tries to read `fullchain.pem` and `privkey.pem`
4. SELinux **denies** Nginx access because the files are now labelled for container access, not httpd
5. Nginx fails with: `(13: Permission denied) while reading ... fullchain.pem`

### What the script does

After **every** Certbot operation (register, renew, dry-run), the script automatically runs:

```bash
restorecon -RFv /etc/letsencrypt
```

This resets the SELinux labels back to `httpd_config_t` so Nginx can read the certificates again.

### Manual fix if Nginx fails after a cert operation

```bash
# Step 1: Restore SELinux labels
sudo restorecon -RFv /etc/letsencrypt

# Step 2: Verify the label is correct
ls -lZ /etc/letsencrypt/live/tripbrain.mooo.com/

# Step 3: Reload Nginx
sudo nginx -t && sudo systemctl reload nginx
```

---

## How It Works

```
cron (daily 03:00 AM)
    └─► manage_cert.py tripbrain
            ├─ Load /opt/platform/.env
            ├─ Check if fullchain.pem + privkey.pem exist
            │      └─ Not found → run install flow
            ├─ Check days remaining (openssl x509 -enddate)
            │      └─ > 30 days remaining → skip, no email
            ├─ Stop Nginx (required for port-80 standalone challenge)
            ├─ Run Certbot Docker container (HTTP-01 ACME challenge)
            ├─ Restore SELinux context (restorecon -RFv /etc/letsencrypt)
            ├─ Start Nginx
            └─ Send SUCCESS or FAILED email (high-priority)
```

Renewal threshold: **30 days** before expiry (configurable in `config.py`).

---

## Directory Structure

```
scripts/cert-manager/
├── manage_cert.py                  # CLI entry point
└── cert_manager/
    ├── config.py                   # Domain presets & path constants
    ├── certificate_manager.py      # Certbot runner & expiry inspector
    ├── nginx_manager.py            # Stop / start / reload Nginx via systemd
    ├── workflow.py                 # Orchestration: check → renew → notify
    ├── email_reporter.py           # Gmail SMTP high-priority email report
    └── utils.py                    # Logging & subprocess runner
```

---

## Configured Domain Shortcuts

Defined in `cert_manager/config.py`:

| Shortcut    | Domain                    |
| ----------- | ------------------------- |
| `tripbrain` | `tripbrain.mooo.com`      |
| `netdata`   | `netdata.cloud1.mooo.com` |
| `spring`    | `spring.cloud1.mooo.com`  |

---

## Running Manually

> All commands require **root** (`sudo`) — Certbot binds to port 80 and writes to `/etc/letsencrypt`.

### Issue / renew using a predefined shortcut

```bash
sudo python3 /opt/platform/cert-manager/manage_cert.py tripbrain
sudo python3 /opt/platform/cert-manager/manage_cert.py spring
sudo python3 /opt/platform/cert-manager/manage_cert.py netdata
```

### Issue a certificate for any arbitrary domain

```bash
sudo python3 /opt/platform/cert-manager/manage_cert.py issue \
  --domain example.com \
  --email you@example.com
```

### Dry-run (test the ACME challenge without issuing a real cert)

```bash
# Using a shortcut
sudo python3 /opt/platform/cert-manager/manage_cert.py tripbrain dry-run

# Using explicit domain
sudo python3 /opt/platform/cert-manager/manage_cert.py issue \
  --domain example.com \
  --email you@example.com \
  --dry-run
```

### List configured domain shortcuts

```bash
sudo python3 /opt/platform/cert-manager/manage_cert.py list
```

### Check certificate expiry manually

```bash
sudo openssl x509 \
  -in /etc/letsencrypt/live/tripbrain.mooo.com/fullchain.pem \
  -noout -dates
```

---

## Cron Setup

The certificate renewal job is managed by `cron-manager.sh` (at `scripts/cron-manager.sh`).

### Install the renewal cron job

```bash
sudo bash /opt/platform/cron-manager.sh install
```

This adds the following to root's crontab:

```
0 3 * * * python3 /opt/platform/cert-manager/manage_cert.py tripbrain >> /var/log/tripbrain-cert.log 2>&1
```

Runs daily at **03:00 AM**. The script does nothing if the certificate still has more than 30 days remaining — so running it daily is safe and cheap.

### View cron status

```bash
sudo bash /opt/platform/cron-manager.sh list
sudo crontab -l
```

### Tail the cron log

```bash
sudo tail -f /var/log/tripbrain-cert.log
```

---

## Email Notifications

Emails are sent **only when an operation is actually triggered** (renewal or registration attempt):

| Event                               | Email sent?             |
| ----------------------------------- | ----------------------- |
| Certificate still valid (> 30 days) | ❌ No                   |
| Renewal triggered — succeeded       | ✅ Yes — SUCCESS        |
| Renewal triggered — failed          | ✅ Yes — FAILED         |
| Dry-run completed                   | ✅ Yes — result of test |

All emails are marked **high-priority** (`X-Priority: 1`) and show the domain, operation type, new expiry date and error details if applicable.

Set `GMAIL_PASSWORD_TOKEN` in `/opt/platform/.env`. Generate one at <a href="https://myaccount.google.com/apppasswords" target="_blank" rel="noopener noreferrer">myaccount.google.com/apppasswords</a>.

---

## Challenges & Troubleshooting

### Port 80 is already in use (Nginx not stopped in time)

Certbot uses the standalone HTTP-01 challenge which needs port 80. The script stops Nginx before running Certbot and restarts it in `finally`. But if something else holds port 80:

```bash
# Find what's using port 80
sudo ss -tlnp | grep :80

# Force kill if needed
sudo fuser -k 80/tcp
```

---

### Nginx fails to start after renewal (SELinux denial)

```bash
# Check SELinux denials
sudo ausearch -m AVC -ts recent | grep nginx

# Fix: restore labels and restart
sudo restorecon -RFv /etc/letsencrypt
sudo nginx -t && sudo systemctl start nginx
```

---

### `docker: command not found`

Oracle Linux uses Podman. Verify the alias exists:

```bash
which docker    # should resolve to /usr/bin/podman or a wrapper
cat /etc/containers/nodocker   # if this file exists, the alias is suppressed — delete it
sudo ln -s /usr/bin/podman /usr/local/bin/docker
```

---

### Certificate files exist but Nginx still shows SSL error

The cert files may have wrong permissions or SELinux labels:

```bash
# Check labels
ls -lZ /etc/letsencrypt/live/tripbrain.mooo.com/

# Should show: system_u:object_r:httpd_config_t:s0
# If it shows container_file_t — run:
sudo restorecon -RFv /etc/letsencrypt

# Check file permissions
sudo ls -la /etc/letsencrypt/live/tripbrain.mooo.com/
sudo ls -la /etc/letsencrypt/archive/tripbrain.mooo.com/
```

---

### Certbot rate-limited by Let's Encrypt

Let's Encrypt enforces **5 failed validation attempts per hour** and **50 certificates per domain per week**. Always use `dry-run` to test before a real issuance:

```bash
sudo python3 /opt/platform/cert-manager/manage_cert.py tripbrain dry-run
```

Check current rate limit status at: <a href="https://crt.sh/?q=tripbrain.mooo.com" target="_blank" rel="noopener noreferrer">crt.sh/?q=tripbrain.mooo.com</a>

---

### Certbot container fails to pull on first run

On Oracle Linux minimal, the Podman image store may be empty and pulling from Docker Hub can time out if the VM has no outbound internet allowed:

```bash
# Test Docker Hub connectivity
curl -I https://registry-1.docker.io

# If OCI firewall is blocking — check the VCN Security List / NSG
# Allow outbound HTTPS (port 443) to 0.0.0.0/0
```

---

### Script exits immediately with "Unknown application or domain argument"

```bash
# Check available shortcuts
sudo python3 /opt/platform/cert-manager/manage_cert.py list

# Use the exact shortcut name: tripbrain / spring / netdata
```

---

### Email not being sent after renewal

```bash
# Confirm the variable is present
grep GMAIL_PASSWORD_TOKEN /opt/platform/.env

# Check the cert log for email warnings
grep -i "gmail\|email\|smtp\|WARNING" /var/log/tripbrain-cert.log | tail -20
```

---

## State & Log Files Reference

| Path                                 | Description                                  |
| ------------------------------------ | -------------------------------------------- |
| `/etc/letsencrypt/live/<domain>/`    | Active cert symlinks (used by Nginx)         |
| `/etc/letsencrypt/archive/<domain>/` | All cert versions (Certbot manages rotation) |
| `/var/log/letsencrypt/`              | Certbot's own verbose logs                   |
| `/var/log/tripbrain-cert.log`        | Cron stdout/stderr rolling log               |

---

## Oracle Linux ARM — Known Gotchas Summary

| Gotcha                               | Cause                                  | Fix                                               |
| ------------------------------------ | -------------------------------------- | ------------------------------------------------- |
| Nginx can't read certs after renewal | SELinux relabels to `container_file_t` | `restorecon -RFv /etc/letsencrypt`                |
| `docker` not found                   | Podman not aliased                     | `ln -s /usr/bin/podman /usr/local/bin/docker`     |
| Port 80 conflict                     | Nginx still running                    | Script handles this; manual: `fuser -k 80/tcp`    |
| `restorecon` not found               | `policycoreutils` not installed        | `sudo dnf install -y policycoreutils`             |
| Certbot can't pull image             | OCI egress firewall                    | Allow port 443 outbound in VCN Security List      |
| `certbot` snap unavailable           | No snapd on Oracle Linux minimal       | Use Docker image — already handled by this script |
