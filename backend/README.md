# TripBrain Backend

A **Spring Boot 3.5** monolith that powers the TripBrain AI travel planning platform.  
It serves the Angular frontend as static files, exposes REST APIs, orchestrates multi-model AI pipelines (Gemini + Groq/LLaMA), manages RAG over uploaded PDFs, and handles file storage via Backblaze B2.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.5 (Java 25) |
| AI Orchestration | Spring AI 1.1.4 |
| LLM — Chat | Google Gemini (`gemini-*` models) |
| LLM — Fallback | Groq (LLaMA 3.3 70B via OpenAI-compatible API) |
| Embeddings | Google Gemini Embedding 2 |
| Vector Store | Qdrant (default) or OpenSearch |
| Database | PostgreSQL (JPA/Hibernate) |
| Cache | Redis (Caffeine fallback) |
| File Storage | Backblaze B2 (S3-compatible, AWS SDK v2) |
| OCR | Tesseract 4 (Tess4J) |
| PDF Generation | iText 9 |
| Security | JWT (JJWT), Spring Security |
| Build | Gradle 8 |

---

## Application Architecture

```
Browser / Angular SPA
       │
       ▼  HTTPS
Nginx (OCI)  →  Spring Boot container (port 8080)
                    ├── /api/auth/**          → JWT login & register
                    ├── /api/conversations/** → Trip planning & AI chat
                    ├── /api/admin/**         → Admin management
                    ├── /api/users/**         → User profile
                    ├── /actuator/**          → Health & metrics (restricted)
                    └── /**                   → Angular static files (SPA)
                            │
                            ├── PostgreSQL (JPA entities + data)
                            ├── Redis (response caching)
                            ├── Qdrant (vector embeddings for PDF RAG)
                            ├── Backblaze B2 (PDF & file storage)
                            ├── Gemini API (primary LLM + embeddings)
                            └── Groq API (secondary/fallback LLM)
```

---

## Key Modules

| Package | Purpose |
|---|---|
| `config/` | Security, CORS, CORS, JWT filter, Qdrant, Cache, LLM bulkhead |
| `controller/` | REST endpoints — Auth, Chat, Conversations, Admin, Geocoding |
| `service/` | Business logic — AI orchestration, PDF, B2 storage, JWT, geocoding |
| `model/` | JPA entities |
| `repository/` | Spring Data JPA repositories |
| `rag/` | RAG pipeline for PDF document retrieval |
| `advisor/` | Spring AI advisors (conversation history, logging) |
| `tool/` | Spring AI tools (web search via Tavily, etc.) |

---

## Environment Variables

All variables are loaded from `/opt/platform/.env` (production) or `backend/.env` (local).  
See [`.env.example`](../.env.example) at the project root for all required keys.

### Required Variables

| Variable | Description |
|---|---|
| `GEMINI_KEY` | Google AI Studio API key |
| `GROQ_KEY` | Groq API key |
| `DATABASE_URL` | PostgreSQL JDBC URL |
| `DATABASE_USERNAME` | PostgreSQL username |
| `DATABASE_PASSWORD` | PostgreSQL password |
| `REDIS_URL` | Redis connection URL |
| `JWT_SECRET` | 256-bit hex secret for signing JWTs |
| `B2_ACCESS_KEY_ID` | Backblaze B2 application key ID |
| `B2_SECRET_ACCESS_KEY` | Backblaze B2 application key secret |
| `B2_ENDPOINT` | B2 S3-compatible endpoint (e.g. `https://s3.us-west-004.backblazeb2.com`) |
| `B2_REGION` | B2 region string (e.g. `us-west-004`) |
| `B2_BUCKET_NAME` | B2 bucket name |

### Optional Variables

| Variable | Default | Description |
|---|---|---|
| `GROQ_MODEL` | `llama-3.1-8b-instant` | Groq model to use |
| `TAVILY_KEY` | — | Web search for AI tools |
| `UNSPLASH_ACCESS_KEY` | — | Destination images |
| `QDRANT_HOST` | `http://localhost:6333` | Qdrant vector store endpoint |
| `QDRANT_KEY` | — | Qdrant API key (if secured) |
| `VECTORSTORE_TYPE` | `qdrant` | `qdrant` or `opensearch` |
| `TESSDATA_PATH` | — | Path to Tesseract tessdata dir |

---

## Backblaze B2 — Custom Configuration Required

> This is the **most common source of issues** for anyone setting this up fresh.

### Why Backblaze B2 instead of AWS S3

Backblaze B2 is S3-compatible and used here for PDF and file storage because it offers a generous free tier. The AWS SDK v2 is used with a custom endpoint override.

### Critical: Path-Style Access Must Be Enabled

By default, the AWS SDK uses **virtual-hosted-style** URLs (`bucket-name.s3.amazonaws.com`). Backblaze B2 does **not support virtual-hosted-style** on all plans. The `BackblazeStorageService` explicitly enables path-style:

```java
.serviceConfiguration(S3Configuration.builder()
    .pathStyleAccessEnabled(true)   // ← Required for Backblaze B2
    .build())
```

If you remove or miss this setting, all B2 operations will fail with a `403` or `NoSuchBucket` error.

### Critical: Presigned URLs Need Custom CORS & Bucket Settings on B2

Presigned URLs (used for direct browser-to-B2 PDF upload and download) require specific settings on the **Backblaze side** that are not automatic:

#### 1. CORS Rules on the Bucket

In the Backblaze dashboard → your bucket → **CORS Rules**, you must allow:

```json
[
  {
    "corsRuleName": "allowPresignedUpload",
    "allowedOrigins": ["https://tripbrain.mooo.com"],
    "allowedOperations": ["s3_put", "s3_get", "s3_head"],
    "allowedHeaders": ["*"],
    "exposeHeaders": ["ETag"],
    "maxAgeSeconds": 3600
  }
]
```

Without this, the browser will get a CORS error when trying to upload directly via a presigned PUT URL.

#### 2. Bucket Must Be Set to "Private" with Public Download Disabled

Presigned download URLs only work if the bucket is **private**. If the bucket is public, Backblaze ignores the signature and any CORS policy may behave differently.

#### 3. Application Key Must Have Full Bucket Access

The key created in Backblaze must have these permissions for the specific bucket:
- `readFiles`
- `writeFiles`
- `deleteFiles`
- `listFiles`
- `listBuckets` (optional but avoids some SDK edge cases)

Do **not** use the master key in production — create a restricted application key scoped to the specific bucket.

#### 4. Endpoint Region Must Match

The endpoint URL and the region string must be consistent. Example:

```env
B2_ENDPOINT=https://s3.us-west-004.backblazeb2.com
B2_REGION=us-west-004
```

If these don't match, the SDK will generate incorrect presigned URL signatures and B2 will reject them with `SignatureDoesNotMatch`.

---

## Security Configuration

### JWT Authentication

All `/api/**` routes (except public endpoints) require a Bearer JWT in the `Authorization` header.  
JWTs are signed with `JWT_SECRET` and expire in 30 minutes (access token) / 7 days (refresh token).

### Actuator Access Policy

- `/actuator/health` and `/actuator/info` — **public** (needed by blue-green health checker)
- All other `/actuator/**` — **restricted** to:
  - `127.0.0.1` (localhost)
  - Private Docker network ranges (`172.*`, `10.*`, `192.168.*`)
  - `spring.cloud1.mooo.com` (Spring Boot Admin monitor)

> If you access `/actuator/metrics` from a public browser, you will get a 403. This is intentional.

### CORS

Configured to allow all origins (`*`) with credentials. In production, tighten this to your actual domain in `SecurityConfig.java`.

---

## AI & LLM Configuration

### Rate Limit Bulkhead

The `LlmBulkheadManager` uses semaphores to prevent exceeding free-tier API limits:

| Model | Concurrent limit |
|---|---|
| Groq (LLaMA) | 2 concurrent requests |
| Google Gemini | 1 concurrent request |

If you are on a paid API plan, increase these limits in `LlmBulkheadManager.java`.

### Vector Store Selection

Controlled by `VECTORSTORE_TYPE`:

- `qdrant` (default) — Self-hosted Qdrant via gRPC on port `6334`. Collections `pdf-lookup-app` and `user-profiles` are auto-created on startup.
- `opensearch` — Set `OPENSEARCH_URIS`, `OPENSEARCH_USERNAME`, `OPENSEARCH_PASSWORD`.

---

## What Can Break

### 1. Database — PostgreSQL Connection Failure

Spring Boot will fail to start if the database is unreachable.

```bash
# Check the container logs on startup
sudo docker logs tripbrain-green 2>&1 | grep -i "datasource\|connection\|postgres\|hikari" | head -30

# Verify the DATABASE_URL is reachable from the container
sudo docker exec tripbrain-green \
  wget -qO- http://127.0.0.1:8080/actuator/health | python3 -m json.tool
```

Common causes: wrong host in `DATABASE_URL`, PostgreSQL firewall, SSL mode mismatch.

---

### 2. Redis Unavailable

The app uses Redis for caching. The `CacheConfig` implements a **graceful fallback** — if Redis is down, cache operations are silently skipped (no crash). But response times may degrade significantly since nothing is cached.

```bash
# Check Redis health in actuator
curl http://127.0.0.1:8082/actuator/health | python3 -m json.tool
# Look for "redis" key in components
```

---

### 3. Backblaze B2 — Presigned URL Errors

| Symptom | Likely cause |
|---|---|
| `SignatureDoesNotMatch` | Region or endpoint mismatch in `.env` |
| `403 Forbidden` on upload | Missing CORS rules on the B2 bucket |
| `NoSuchBucket` | Wrong bucket name or path-style not enabled |
| `Access Denied` | Application key missing `writeFiles` permission |
| Presigned URL works in Postman but fails in browser | CORS rules on B2 not set for your domain |

```bash
# Test B2 connectivity from the server
curl -I https://s3.us-west-004.backblazeb2.com
```

---

### 4. Gemini / Groq API Key Invalid or Rate Limited

```bash
# Check container logs for API errors
sudo docker logs tripbrain-green 2>&1 | grep -i "gemini\|groq\|rate.limit\|quota\|429" | tail -30
```

The app will return a 500 to the frontend user when the LLM call fails. Free-tier quotas reset daily.

---

### 5. Qdrant Unreachable

If Qdrant is down, PDF RAG queries will fail. The app still starts (collections are created lazily).

```bash
# Check Qdrant health
curl http://your-qdrant-host:6333/healthz

# Check app logs for Qdrant errors
sudo docker logs tripbrain-green 2>&1 | grep -i "qdrant\|grpc\|vector" | tail -20
```

---

### 6. Tesseract — OCR Fails for Uploaded Documents

Tesseract is used for OCR on uploaded images/PDFs. If `TESSDATA_PATH` is wrong, OCR-related features silently degrade.

```bash
# Verify tessdata path inside the container
sudo docker exec tripbrain-green ls $TESSDATA_PATH

# Should list: eng.traineddata, osd.traineddata, etc.
```

---

### 7. Frontend Not Being Served (404 on `/`)

The Angular build output must exist inside the JAR at `BOOT-INF/classes/static/`.

```bash
# Check static files are present
sudo docker exec tripbrain-green \
  find /app -name "index.html" 2>/dev/null
```

If missing, the JAR was built with `-PskipFrontend`. Rebuild without that flag.

---

### 8. JWT `403` on Valid Requests

Token may be expired (30-minute access token) or `JWT_SECRET` changed between deployments (all existing tokens are immediately invalidated).

---

## Troubleshooting Checklist

```bash
# 1. Is the container running?
sudo docker ps | grep tripbrain

# 2. View startup logs (look for errors during boot)
sudo docker logs tripbrain-green 2>&1 | head -100

# 3. Check full health breakdown
curl -s http://127.0.0.1:8082/actuator/health | python3 -m json.tool

# 4. Check environment variables inside the container
sudo docker exec tripbrain-green env | grep -E "DATABASE|REDIS|QDRANT|B2_|GEMINI|GROQ"

# 5. Check database connectivity
sudo docker exec tripbrain-green \
  wget -qO- "http://127.0.0.1:8080/actuator/health/db"

# 6. Check Redis connectivity
sudo docker exec tripbrain-green \
  wget -qO- "http://127.0.0.1:8080/actuator/health/redis"

# 7. Tail live application logs
sudo docker logs -f --tail 50 tripbrain-green
```

---

## Local Development

```bash
cd backend

# Run with local .env file
./gradlew bootRun

# Skip frontend build (faster iteration)
./gradlew bootRun -PskipFrontend

# Build production JAR (includes frontend)
./gradlew bootJar

# Run tests
./gradlew test
```

The `backend/.env` file is picked up automatically via:
```yaml
spring:
  config:
    import: optional:file:.env[.properties]
```

---

## Nginx — Backend Actuator Protection

The Nginx config on the OCI server adds an extra firewall layer on top of Spring Security:

```nginx
location /actuator {
    allow 127.0.0.1;
    allow 172.16.0.0/12;   # Docker bridge networks
    allow 140.238.12.34;   # Your OCI server IP
    deny all;
}
```

Spring Security also enforces its own IP-based rules for `/actuator/**` independently (defense in depth).
