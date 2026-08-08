# TripBrain — Smart AI Travel Itinerary Planner ✈️

TripBrain is a pair-programmed, production-grade web application that plans travel itineraries using Spring AI and a multi-agent orchestration architecture. It rotates through active LLM models, matches user parameters, generates clean A4 PDF documents with custom typography & icons, caches search details using Valkey/Redis, uploads resulting documents directly to Backblaze B2 storage, and enforces strict role-based security & resource-level privacy.

---

## 🛠️ Technology Stack

- **Backend**: Java 25, Spring Boot 3.5.x, Spring Security (Stateless JWT Auth & RBAC), Spring AI 1.1.4 (OpenAI, Gemini & Groq integrations), Hibernate/JPA.
- **Frontend**: Angular 21, CSS Variables, RxJS, Signals, Server-Side Rendering (SSR) & Vitest unit testing.
- **Database & Cache**: PostgreSQL (Storage), Qdrant / OpenSearch (Vector Store), Valkey / Redis (Caching), HikariCP (Tuned connection pool for 1 CPU / 1.4GB RAM).
- **PDF Compilation**: iText Core (version 9.6.0), custom emoji mappings, SVG vector thumbnail generator.
- **Reverse Proxy & SSL**: Nginx HTTPS reverse proxy, Let's Encrypt Certbot SSL certificate automation.
- **CI/CD & Deployment**: GitHub Actions, Docker Blue-Green zero-downtime deployment, automated cron poller.

---

## 📂 Project Structure

```text
trip-brain/
├── backend/                  # Spring Boot application
│   ├── src/main/java/        # Java source code (SecurityConfig, Controllers, Services)
│   ├── src/main/resources/   # Application config (application.yaml, static assets)
│   └── build.gradle          # Gradle project configuration
├── frontend/                 # Client application
│   └── trip-brain-frontend-app/
│       ├── src/              # Angular components, signals, routes, services
│       └── package.json      # Dependencies and scripts
├── monitor/
│   └── trip-brain-monitor/   # Spring Boot Admin Server (spring.cloud1.mooo.com)
├── scripts/                  # Production deployment & operations scripts
│   ├── platform-deployer/    # Modular Python Blue-Green deployment package (deploy.py)
│   ├── cert-manager/         # Modular Python SSL certificate manager (manage_cert.py)
│   ├── cron-manager.sh       # Automated cron job management script
│   └── setup-blue-green-platform.sh # Master platform setup script
├── Dockerfile                # Multi-stage image build definition
└── docker-compose.yml        # Docker execution setup
```

---

## 📊 Spring Boot Admin Platform Monitoring

We have configured a dedicated **Spring Boot Admin Dashboard Server** (`monitor/trip-brain-monitor/`) to monitor the health, metrics, environment properties, thread dumps, and actuator endpoints of `trip-brain` and future platform microservices.

- **Admin Server Domain**: `https://spring.cloud1.mooo.com`
- **Monitored App Domain**: `https://tripbrain.mooo.com`
- **Default Authentication Credentials**:
  - Username: `admin` (overridable via `SPRING_ADMIN_USERNAME`)
  - Password: `admin123` (overridable via `SPRING_ADMIN_PASSWORD`)
- **Docker Image**: `ikaushikpal/trip-brain-monitor-app:latest`
- **Automated CI/CD Workflow**: [.github/workflows/release-monitor-app.yaml](file:///Users/kaushikpal/Desktop/codes/projects/spring-ai/trip-brain/.github/workflows/release-monitor-app.yaml)

To run the Spring Boot Admin Server locally:

```bash
cd monitor/trip-brain-monitor
./gradlew bootRun
```
The monitor dashboard will be available at `http://localhost:8085`.

---

## 🔒 Security & Authorization Architecture

- **Spring Security `SecurityFilterChain`**: Stateless JWT authentication filter (`JwtAuthenticationFilter`) validating Bearer tokens and populating `SecurityContextHolder`.
- **Method-Level Security (`@EnableMethodSecurity`)**: `@PreAuthorize` annotations across controllers enforcing role checks (`ROLE_USER`, `ROLE_ADMIN`).
- **Resource Ownership & Privacy Isolation**:
  - **Private Chats**: Owner-only access (`verifyReadAccess` & `verifyWriteAccess`). Administrators (`ROLE_ADMIN`) **cannot view or modify** another user's private conversations.
  - **Shared Trips**: Public trips and gallery items are read-only (`@PreAuthorize("permitAll()")`).
- **Admin Cascade Cleanup**: Soft-deleting or purging a user via `UserService.deleteUser(userId)` cascades through generated PDF files (local & Backblaze cloud), `TripPdf` entities, `ChatMessage` records, `Conversation` records, and `RefreshToken` entities.

---

## 🤖 Automated Cron Jobs Management

We provide a dedicated script `cron-manager.sh` to provision, monitor, and clean up automated system cron tasks on your deployment server:

```bash
# Provision / install automated cron jobs
sudo bash scripts/cron-manager.sh install

# View active cron jobs & log file status
sudo bash scripts/cron-manager.sh list

# Remove TripBrain cron jobs
sudo bash scripts/cron-manager.sh remove
```

### Configured Automated Cron Schedules:
1. **1-Minute Blue-Green Deployment Poller** (`* * * * *`):
   Runs `/opt/platform/platform-deployer/deploy.py` every 1 minute. Detects new `ikaushikpal/tripbrain:latest` Docker images, starts target container (Blue/Green), polls `/actuator/health`, updates Nginx upstream, reloads Nginx zero-downtime, and decommissions old containers.
2. **Daily SSL Certificate Auto-Renewal Check** (`0 3 * * *`):
   Runs `/opt/platform/cert-manager/manage_cert.py tripbrain` daily at 03:00 AM. Checks OpenSSL expiry, executes standalone Dockerized Certbot HTTP-01 challenge if certificate expires within 30 days, and reloads Nginx.

---

## 🚀 Server Platform Setup & CLI Commands

### 1. Master Server Setup
To provision directory structures, Nginx configuration files (`/etc/nginx/conf.d/tripbrain.conf`), and install cron jobs on an Oracle Linux / RHEL / Ubuntu server:

```bash
sudo bash scripts/setup-blue-green-platform.sh
```

### 2. Manual Blue-Green Deployment Execution
To manually trigger a Blue-Green deployment check:

```bash
python3 scripts/platform-deployer/deploy.py
```

### 3. SSL Certificate Operations
To manage Let's Encrypt SSL certificates:

```bash
# Register or renew certificate (tripbrain & netdata)
sudo python3 scripts/cert-manager/manage_cert.py tripbrain
sudo python3 scripts/cert-manager/manage_cert.py netdata

# Run renewal dry-run test
sudo python3 scripts/cert-manager/manage_cert.py tripbrain dry-run
sudo python3 scripts/cert-manager/manage_cert.py netdata dry-run

# List configured domain applications
python3 scripts/cert-manager/manage_cert.py list
```

### 4. Gmail SMTP Email Notifications Setup
To enable automated Gmail SMTP email reports (`iamkaushik2014@gmail.com`) for deployment runs and log persistence (`/data/tripbrain/platform-deployer-logs/`), export your Gmail App Password in your environment or add it to `/opt/platform/.env`:

```bash
export GMAIL_APP_PASSWORD="your-16-char-gmail-app-password"
```

---

## ⚡ Local Development & Testing

### 1. Backend Development Server
Ensure Java 25 is installed. Create `backend/.env` with your API keys, then run:

```bash
cd backend
./gradlew bootRun
```
The backend API will start on `http://localhost:8080`.

### 2. Backend Verification & Tests
To run full static analysis, compilation, and unit/integration tests with in-memory H2 database:

```bash
cd backend
./gradlew check --no-daemon
```

### 3. Frontend Development Server
Ensure Node.js 20+ is installed:

```bash
cd frontend/trip-brain-frontend-app
npm install
npm run start
```
Open `http://localhost:4200` to access the interactive web dashboard.

### 4. Frontend Formatting & Tests
```bash
cd frontend/trip-brain-frontend-app

# Format code with Prettier
npm run format

# Run formatting & type check
npm run lint

# Run Vitest unit tests
npm test -- --watch=false
```

---

## 🐳 Docker Deployment

To build and run the unified application locally in Docker:

```bash
# Build and start container
docker compose up --build -d

# Stream application logs
docker compose logs -f
```
The application will be available at `http://localhost:8080`.

___
Viewed README.md:50-110

The specified sections and command blocks are present in [README.md](file:///Users/kaushikpal/Desktop/codes/projects/spring-ai/trip-brain/README.md#L52-L103):

```markdown
## 🤖 Automated Cron Jobs Management

We provide a dedicated script `cron-manager.sh` to provision, monitor, and clean up automated system cron tasks on your deployment server:

```bash
# Provision / install automated cron jobs
sudo bash scripts/cron-manager.sh install

# View active cron jobs & log file status
sudo bash scripts/cron-manager.sh list

# Remove TripBrain cron jobs
sudo bash scripts/cron-manager.sh remove
```

### Configured Automated Cron Schedules:
1. **1-Minute Blue-Green Deployment Poller** (`* * * * *`):
   Runs `/opt/platform/platform-deployer/deploy.py` every 1 minute. Detects new `ikaushikpal/tripbrain:latest` Docker images, starts target container (Blue/Green), polls `/actuator/health`, updates Nginx upstream, reloads Nginx zero-downtime, and decommissions old containers.
2. **Daily SSL Certificate Auto-Renewal Check** (`0 3 * * *`):
   Runs `/opt/platform/cert-manager/manage_cert.py tripbrain` daily at 03:00 AM. Checks OpenSSL expiry, executes standalone Dockerized Certbot HTTP-01 challenge if certificate expires within 30 days, and reloads Nginx.

---

## 🚀 Server Platform Setup & CLI Commands

### 1. Master Server Setup
To provision directory structures, Nginx configuration files (`/etc/nginx/conf.d/tripbrain.conf`), and install cron jobs on an Oracle Linux / RHEL / Ubuntu server:

```bash
sudo bash scripts/setup-blue-green-platform.sh
```

### 2. Manual Blue-Green Deployment Execution
To manually trigger a Blue-Green deployment check:

```bash
python3 scripts/platform-deployer/deploy.py
```

### 3. SSL Certificate Operations
To manage Let's Encrypt SSL certificates:

```bash
# Register or renew certificate
sudo python3 scripts/cert-manager/manage_cert.py tripbrain

# Run renewal dry-run test
sudo python3 scripts/cert-manager/manage_cert.py tripbrain dry-run

# List configured domain applications
python3 scripts/cert-manager/manage_cert.py list
```