# ✈️ TripBrain — AI-Powered Travel Itinerary Planner

> **TripBrain** is a production-grade, open-source AI travel planner built with Spring AI and Angular.  
> It orchestrates multiple LLMs (Gemini + Groq/LLaMA), generates beautifully formatted PDF itineraries, caches results in Redis, stores files on Backblaze B2, and deploys itself via a fully automated zero-downtime blue-green pipeline — **all running on Oracle Cloud Free Tier. No cloud bill. Zero cost.**

[![PR Quality Gate](https://github.com/ikaushikpal/TripBrain/actions/workflows/pr-quality-check.yaml/badge.svg)](https://github.com/ikaushikpal/TripBrain/actions/workflows/pr-quality-check.yaml)
[![Release Docker](https://github.com/ikaushikpal/TripBrain/actions/workflows/release-docker-on-main.yaml/badge.svg)](https://github.com/ikaushikpal/TripBrain/actions/workflows/release-docker-on-main.yaml)

🌐 **Live:** [https://tripbrain.mooo.com](https://tripbrain.mooo.com)  
🔁 **Blocked by corporate VPN/firewall?** Use the Render proxy: [https://tripbrain-11du.onrender.com](https://tripbrain-11du.onrender.com)  
📊 **Monitor:** [https://spring.cloud1.mooo.com](https://spring.cloud1.mooo.com)

---

## 👋 A Note from the Author

Hi! I'm **Kaushik**, the developer behind TripBrain. I built this project to explore Spring AI, multi-agent LLM orchestration, and production-grade infrastructure — all on a zero-cost cloud setup.

🔍 **I'm currently looking for new opportunities.** If you find this project interesting and your company is hiring (or you can refer me), I'd genuinely appreciate it. Feel free to reach out via GitHub or [LinkedIn](https://www.linkedin.com/in/ikaushikpal).

Every star ⭐, issue, PR, or referral means a lot — thank you for being here.

---

## 🚀 What TripBrain Does

- 🤖 **Multi-LLM orchestration** — Gemini (primary) + Groq LLaMA (fallback), with a bulkhead rate limiter to respect free-tier API limits
- 🗺️ **AI trip planning** — generates fully structured, day-by-day itineraries via a multi-step agent pipeline
- 📄 **PDF generation** — beautifully typeset A4 PDF documents with custom fonts, icons, and Unsplash destination images
- 🔍 **RAG over PDFs** — upload your own travel documents and ask questions about them via Qdrant vector search
- 🗄️ **File storage** — PDFs stored on Backblaze B2 (S3-compatible), served via presigned URLs
- 🔐 **Full auth** — JWT-based stateless auth with RBAC (USER / ADMIN roles)
- 📤 **Trip sharing** — shareable public links for trip itineraries
- 🖼️ **Gallery** — browse publicly shared trips
- 📊 **App monitoring** — Spring Boot Admin dashboard for JVM health, metrics, threads, log levels
- 📡 **Server monitoring** — Netdata real-time dashboard for CPU, RAM, disk I/O, network, and Nginx metrics
- 🔄 **Zero-downtime deploys** — automated blue-green deployment via cron, with email notifications

---

## 🏗️ Architecture Overview

```
                         ┌──────────────────────────────────────────────────┐
                         │         Oracle Cloud Free Tier (ARM64)            │
                         │                                                    │
  Browser ──HTTPS──► Nginx (tripbrain.mooo.com)                             │
                         │   ├─► tripbrain-blue:8080   (Active slot)        │
                         │   └─► tripbrain-green:8082  (Idle slot)          │
                         │            │                                       │
                         │            ├── PostgreSQL  (Aiven)                │
                         │            ├── Redis       (Valkey/Aiven)         │
                         │            ├── Qdrant      (Vector store)         │
                         │            ├── Backblaze B2 (File storage)        │
                         │            ├── Gemini API  (LLM + Embeddings)     │
                         │            └── Groq API    (LLM fallback)         │
                         │                                                    │
                         │  Nginx (spring.cloud1.mooo.com)                   │
                         │  └─► trip-brain-monitor:8085                      │
                         │       Spring Boot Admin (JVM health & metrics)    │
                         │                                                    │
                         │  Nginx (netdata.cloud1.mooo.com) + Basic Auth     │
                         │  └─► Netdata Agent:19999                          │
                         │       Real-time server metrics (CPU/RAM/Nginx)    │
                         └──────────────────────────────────────────────────┘

Corporate Networks (Zscaler/VPN blocked .mooo.com)
  └── tripbrain-11du.onrender.com  →  Nginx Render Proxy  →  tripbrain.mooo.com
```

---

## 🛠️ Tech Stack

| Layer                 | Technology                                               |
| --------------------- | -------------------------------------------------------- |
| **Backend**           | Java 25, Spring Boot 3.5, Spring AI 1.1.4                |
| **Frontend**          | Angular 21, TailwindCSS v4, SSR, Vitest                  |
| **AI / LLM**          | Google Gemini, Groq (LLaMA 3.3 70B)                      |
| **Database**          | PostgreSQL (Aiven)                                       |
| **Cache**             | Redis / Valkey (Aiven)                                   |
| **Vector Store**      | Qdrant (or OpenSearch)                                   |
| **File Storage**      | Backblaze B2 (S3-compatible)                             |
| **PDF Engine**        | iText 9, custom emoji mapping, SVG thumbnails            |
| **OCR**               | Tesseract 4 (Tess4J)                                     |
| **Security**          | Spring Security, JWT (JJWT), RBAC                        |
| **Reverse Proxy**     | Nginx + Let's Encrypt SSL                                |
| **App Monitoring**    | Spring Boot Admin 3.4                                    |
| **Server Monitoring** | Netdata (real-time CPU, RAM, Nginx, disk)                |
| **CI/CD**             | GitHub Actions (5 workflows)                             |
| **Deployment**        | Docker, Blue-Green, Python automation scripts            |
| **Infrastructure**    | Oracle Cloud Free Tier (ARM64 Ampere A1), Oracle Linux 9 |

---

## 📂 Repository Structure

```
trip-brain/
├── backend/                          # Spring Boot monolith (API + serves Angular)
├── frontend/trip-brain-frontend-app/ # Angular 21 SPA
├── monitor/trip-brain-monitor/       # Spring Boot Admin dashboard server
├── render-proxy/                     # Nginx reverse proxy (deployed on Render.com)
├── scripts/
│   ├── platform-deployer/            # Blue-green Python deployment pipeline
│   ├── cert-manager/                 # Let's Encrypt SSL Python manager
│   └── cron-manager.sh               # Cron job provisioner
├── .github/workflows/                # 5 GitHub Actions CI/CD workflows
├── Dockerfile                        # Multi-stage production image build
├── docker-compose.yml                # Local / compose-based deployment
└── .env.example                      # All environment variables with descriptions
```

---

## 📖 Documentation

Each module has its own detailed README:

| Module            | README                                                                                     | Description                                                      |
| ----------------- | ------------------------------------------------------------------------------------------ | ---------------------------------------------------------------- |
| Backend           | [backend/README.md](./backend/README.md)                                                   | Spring Boot API, B2 presigned URL config, security, AI/LLM setup |
| Frontend          | [frontend/trip-brain-frontend-app/README.md](./frontend/trip-brain-frontend-app/README.md) | Angular dev setup, routes, troubleshooting                       |
| Monitor           | [monitor/trip-brain-monitor/README.md](./monitor/trip-brain-monitor/README.md)             | Spring Boot Admin, Nginx config, CI/CD workflows explained       |
| Render Proxy      | [render-proxy/README.md](./render-proxy/README.md)                                         | Corporate firewall bypass, SSL expiry impact                     |
| Platform Deployer | [scripts/platform-deployer/README.md](./scripts/platform-deployer/README.md)               | Blue-green pipeline, cron setup, troubleshooting                 |
| Cert Manager      | [scripts/cert-manager/README.md](./scripts/cert-manager/README.md)                         | SSL renewal, SELinux gotchas, Oracle Linux ARM guide             |
| Scripts Index     | [scripts/README.md](./scripts/README.md)                                                   | Overview of all automation scripts                               |
| CI/CD Workflows   | [.github/workflows/README.md](./.github/workflows/README.md)                               | All 5 workflows, when they trigger, required secrets             |

---

## 📡 Monitoring Stack

TripBrain runs two separate monitoring layers on the same OCI server:

### Spring Boot Admin — Application Monitoring

**URL:** [https://spring.cloud1.mooo.com](https://spring.cloud1.mooo.com) · **Port:** `8085` · **Auth:** username/password

Monitors all registered Spring Boot instances (tripbrain + the monitor itself) and provides:

- Live UP/DOWN status of each application instance
- JVM heap, GC activity, thread count
- HTTP request traces
- Log level changes at runtime (no restart needed)
- Environment properties and health indicator breakdown

See [monitor/trip-brain-monitor/README.md](./monitor/trip-brain-monitor/README.md) for setup details.

---

### Netdata — Real-Time Server Monitoring

**URL:** [https://netdata.cloud1.mooo.com](https://netdata.cloud1.mooo.com) · **Port:** `19999` · **Auth:** HTTP Basic Auth (htpasswd)

Netdata runs directly on the OCI host and provides real-time visibility into the underlying server:

- CPU, RAM, disk I/O, network throughput
- Nginx request rates, active connections, response codes
- Docker container resource usage (per-container CPU/RAM)
- System-level health — no agents or SaaS required

**Nginx virtual host** (`/etc/nginx/sites-available/netdata-cloud1`):

```nginx
server {
    listen 80;
    server_name netdata.cloud1.mooo.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    server_name netdata.cloud1.mooo.com;

    ssl_certificate     /etc/letsencrypt/live/netdata.cloud1.mooo.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/netdata.cloud1.mooo.com/privkey.pem;

    location / {
        auth_basic           "Netdata";
        auth_basic_user_file /etc/nginx/.netdata_htpasswd;  # bcrypt htpasswd file

        proxy_pass http://127.0.0.1:19999;
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

**Why HTTP Basic Auth?** Netdata has no built-in auth. Since the connection is over HTTPS, Basic Auth credentials are encrypted in transit — secure enough for a personal monitoring dashboard. The password file is managed with `htpasswd`:

```bash
sudo htpasswd -c /etc/nginx/.netdata_htpasswd your_username
```

**Planned improvements:**

- Email alert triggers for CPU/RAM spikes and service downtime
- Loki integration for centralised log aggregation and querying

> The SSL certificate for `netdata.cloud1.mooo.com` is managed by the same cert-manager cron job. See [scripts/cert-manager/README.md](./scripts/cert-manager/README.md).

---

## ⚡ Quick Start (Local Development)

### Prerequisites

- Java 25+
- Node.js 20+ / npm 11+
- Docker
- API keys: Gemini, Groq, PostgreSQL, Redis, Qdrant, Backblaze B2

### 1. Clone and configure

```bash
git clone https://github.com/ikaushikpal/TripBrain.git
cd TripBrain

# Copy example env and fill in your keys
cp .env.example backend/.env
```

### 2. Run the backend

```bash
cd backend
./gradlew bootRun -PskipFrontend
# API starts at http://localhost:8080
```

### 3. Run the frontend

```bash
cd frontend/trip-brain-frontend-app
npm install
npm start
# UI starts at http://localhost:4200
```

### 4. Run with Docker Compose

```bash
docker compose up --build -d
# App available at http://localhost:8080
```

---

## 🌩️ Deploying on Oracle Cloud Free Tier (Zero Cost)

This entire platform runs on the **Oracle Cloud Always Free** tier — no credit card charges, no time limits.

**What you get for free:**

- 4 Arm-based Ampere A1 cores + 24 GB RAM (shared across up to 4 VMs)
- 200 GB block storage
- 10 TB outbound data transfer per month

**Steps to replicate this setup:**

1. Sign up at [cloud.oracle.com](https://cloud.oracle.com) — choose an Always Free account
2. Create an **ARM64 Ampere A1** VM running Oracle Linux 9 Minimal
3. Install Podman (aliased as `docker`), Nginx, Python 3
4. Copy scripts to `/opt/platform/`
5. Configure `/opt/platform/.env` (see [`.env.example`](./.env.example))
6. Run `sudo bash scripts/cron-manager.sh install` to wire up all automation
7. Configure Nginx virtual hosts and obtain SSL certs via the cert-manager

See [scripts/cert-manager/README.md](./scripts/cert-manager/README.md) for the full Oracle Linux + SELinux + ARM64 guide.

---

## 🔄 Deployment Pipeline

Every commit merged to `main` triggers an automated chain:

```
PR merged to main
    │
    ├─► GitHub Actions builds multi-arch Docker image
    │   └─► ikaushikpal/trip-brain:latest pushed to Docker Hub
    │
    └─► Cron job on OCI server (runs every 1 min)
            ├─ Pulls latest image digest
            ├─ Detects change → starts new container (blue/green)
            ├─ Polls /actuator/health (30 retries)
            ├─ Switches Nginx upstream → zero-downtime cutover
            ├─ Stops old container
            └─ Sends SUCCESS/FAILED email report
```

See [scripts/platform-deployer/README.md](./scripts/platform-deployer/README.md) for full details.

---

## 🗺️ Roadmap

### Planned Migrations (Coming Soon)

- **PostgreSQL**: Migrate from Aiven managed PostgreSQL → **Oracle Autonomous Database (Always Free)**
- **File Storage**: Migrate from Backblaze B2 → **Oracle Object Storage (Always Free)** — keeping the same S3-compatible interface via `pathStyleAccessEnabled(true)`

These migrations will make the stack **100% Oracle Cloud native** with zero external SaaS dependencies.

### Other Planned Features

- [ ] Multi-destination trip chaining
- [ ] Budget planning with live currency conversion
- [ ] Trip collaboration (shared editing)
- [ ] Mobile-responsive PWA improvements

---

## 🤝 Contributing

Contributions are welcome! Here's how to get started:

### Fork & Branch

```bash
git clone https://github.com/ikaushikpal/TripBrain.git
git checkout -b feat/your-feature-name
```

### PR Guidelines

- **PR titles must follow [Conventional Commits](https://www.conventionalcommits.org/):**  
  `feat: add feature`, `fix: fix bug`, `docs: update readme`, `chore: update deps`
- All PRs run the quality gate automatically — ensure `npm run lint`, `./gradlew check`, and Prettier pass locally before pushing
- One approval required before merge

### Good First Issues

Look for issues tagged `good first issue` or `help wanted` on the [Issues](https://github.com/ikaushikpal/TripBrain/issues) page.

### What you can contribute

- Bug fixes
- New AI tools / agents
- Frontend UI improvements
- More vector store backends
- Documentation improvements
- Performance optimizations

---

## 🔐 Environment Variables

All configuration is in one file. See [`.env.example`](./.env.example) for the complete reference with descriptions for every variable.

Key variables:

| Variable                         | Description                                     |
| -------------------------------- | ----------------------------------------------- |
| `GEMINI_KEY`                     | Google AI Studio API key                        |
| `GROQ_KEY`                       | Groq API key                                    |
| `DATABASE_URL`                   | PostgreSQL JDBC URL                             |
| `REDIS_URL`                      | Redis connection URL                            |
| `B2_*`                           | Backblaze B2 storage credentials                |
| `JWT_SECRET`                     | 256-bit hex JWT signing secret                  |
| `GMAIL_PASSWORD_TOKEN`           | Gmail App Password for deployment email reports |
| `SPRING_ADMIN_USERNAME/PASSWORD` | Spring Boot Admin credentials                   |

---

## 📄 License

This project is open source under the [MIT License](./LICENSE).

---

<div align="center">

Built with ❤️ by [Kaushik Pal](https://github.com/ikaushikpal) · Running free on Oracle Cloud · Open to contributions

⭐ **If this project helped you or you found it interesting, please consider starring it — it really helps!**

</div>
