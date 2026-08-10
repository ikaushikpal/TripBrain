# Scripts

Server-side automation scripts for the TripBrain production platform running on Oracle Cloud (OCI).  
These scripts handle continuous deployment and SSL certificate lifecycle — both driven by cron with no manual intervention required.

---

## Modules

### 🚀 <a href="./platform-deployer/README.md" target="_blank" rel="noopener noreferrer">platform-deployer</a>

Zero-downtime **Blue-Green deployment pipeline** for the TripBrain Spring Boot application.  
Polls Docker Hub every minute, detects new image digests, and automatically switches Nginx traffic between the blue and green container slots. Sends high-priority email reports on deployment success or failure.

---

### 🔐 <a href="./cert-manager/README.md" target="_blank" rel="noopener noreferrer">cert-manager</a>

Automated **Let's Encrypt SSL certificate renewal** for all TripBrain domains.  
Runs Certbot inside a Docker container daily, handles Nginx stop/start around the ACME challenge, restores SELinux labels after renewal (required on Oracle Linux), and sends high-priority email alerts when a renewal is triggered.

---

## Shared Cron Setup

Both modules are wired up by a single script:

```bash
# Install all cron jobs (deployment poller + cert renewal)
sudo bash /opt/platform/cron-manager.sh install

# View current status
sudo bash /opt/platform/cron-manager.sh list

# Remove all cron jobs
sudo bash /opt/platform/cron-manager.sh remove
```

All secrets are read from a single file: `/opt/platform/.env`  
See <a href="../.env.example" target="_blank" rel="noopener noreferrer">`.env.example`</a> at the project root for all required variables.
