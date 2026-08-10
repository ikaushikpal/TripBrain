# TripBrain Frontend

The **Angular 21** web application for TripBrain — an AI-powered travel planning platform.  
Built with Server-Side Rendering (SSR), TailwindCSS v4, and served statically from the Spring Boot backend in production.

---

## Tech Stack

| Technology | Version | Purpose |
|---|---|---|
| Angular | 21 | SPA framework with SSR |
| `@angular/ssr` | 21 | Server-Side Rendering via Express |
| TailwindCSS | v4 | Utility-first CSS |
| Leaflet | 1.9 | Interactive maps |
| Marked | 18 | Markdown rendering for AI responses |
| Lucide Angular | — | Icon library |
| Vitest | 4 | Unit test runner |
| Prettier | 3 | Code formatter |

---

## How It Works in Production

The frontend is **not deployed as a separate service**. During the Spring Boot build, Gradle:

1. Runs `npm install && npm run build` inside the Angular project
2. Copies the built `dist/` output into `backend/src/main/resources/static/`
3. Spring Boot serves the Angular app as static files at `/`

This means the frontend and backend ship as a **single Docker image** (`ikaushikpal/trip-brain:latest`).

```
Browser → https://tripbrain.mooo.com
              │
              ▼
         Nginx (OCI)  →  Spring Boot container (port 8080)
                              ├─ /api/**        → REST endpoints
                              └─ /**            → Angular static files
```

---

## Application Routes

| Route | Access | Description |
|---|---|---|
| `/` | Public | Landing / home page |
| `/auth/login` | Public | Login page |
| `/auth/register` | Public | Registration page |
| `/dashboard` | 🔒 Auth required | AI trip planning dashboard |
| `/gallery` | Public | Trip gallery |
| `/admin` | 🔒 Admin only | Admin panel |
| `/share/:id` | Public | Shareable trip chat view |

Route guards: `authGuard` (JWT check) and `adminGuard` (role check).

---

## API Base URL Logic

Defined in `src/app/core/constants.ts`:

```ts
const isLocalhost4200 = typeof window !== 'undefined' && window.location.port === '4200';
export const BASE_API_URL = isLocalhost4200 ? 'http://localhost:8080/api' : '/api';
export const BASE_URL     = isLocalhost4200 ? 'http://localhost:8080' : '';
```

- **Local dev** (`ng serve` on port 4200) → calls `http://localhost:8080/api`
- **Production** (served from Spring Boot) → calls `/api` (same origin, no CORS)

---

## Local Development

### Prerequisites

- Node.js 20+
- npm 11+
- Spring Boot backend running locally on port `8080`

### Install dependencies

```bash
cd frontend/trip-brain-frontend-app
npm install
```

### Start dev server

```bash
npm start
# or
ng serve
```

Open `http://localhost:4200`. The app hot-reloads on file changes.

### Build for production

```bash
npm run build
```

Output goes to `dist/trip-brain-frontend-app/browser/`.  
In the full project build, this is triggered automatically by Gradle.

### Build via Gradle (as CI/CD does)

```bash
# From the repo root
./gradlew bootJar
# This runs: npm install && npm run build, then copies dist → src/main/resources/static
```

To skip the frontend build during development:

```bash
./gradlew bootJar -PskipFrontend
```

### Lint & Format

```bash
npm run lint      # Prettier check + TypeScript type check
npm run format    # Auto-fix formatting with Prettier
```

### Run tests

```bash
npm test          # Vitest unit tests
```

---

## Troubleshooting

### Blank page / white screen after backend deploy

The Angular static files may not have been copied correctly into the Spring Boot jar.

**Check:**
```bash
# Inside the running container, confirm static files exist
sudo docker exec tripbrain-green ls /app/resources/static/

# Should show: index.html, main-*.js, styles-*.css, etc.
```

**Fix:** Trigger a fresh build with the frontend included (no `-PskipFrontend` flag).

---

### `404` on page refresh for any route (e.g. `/dashboard`)

Angular uses client-side routing. If Nginx or Spring Boot doesn't fall back to `index.html` for unknown paths, refreshing a route returns a 404.

Spring Boot is configured to serve `index.html` for all non-API, non-static paths.  
If you ever see this — check that no Nginx `try_files` or `location` block is intercepting frontend routes before Spring Boot.

---

### API calls failing in local dev (`CORS` errors in browser console)

The backend must be running on port `8080`. CORS is only an issue in dev (port 4200 ≠ port 8080).

**Check Spring Boot CORS config** — it should allow `http://localhost:4200`.

```bash
# Confirm backend is running
curl -I http://localhost:8080/api/health
```

---

### `ng serve` fails — port 4200 already in use

```bash
# Kill whatever is using port 4200
lsof -ti:4200 | xargs kill -9

# Or serve on a different port
ng serve --port 4300
```

---

### `npm install` fails / `node_modules` issues

```bash
# Clean install
rm -rf node_modules package-lock.json
npm install
```

---

### Leaflet map tiles not loading

Map tiles are fetched from OpenStreetMap CDN at runtime. This fails if:
- The browser is offline
- A corporate proxy blocks `*.tile.openstreetmap.org`

No fix needed in code — purely a network/proxy issue on the client side.

---

### Markdown not rendering in AI responses

The `marked` library renders AI response text. If markdown appears as raw text:
- Check the browser console for JS errors
- Confirm `marked` is present in `node_modules`

```bash
npm list marked
```

---

### Build fails with `ENOMEM` or heap out-of-memory during `ng build`

Angular's build can be memory-intensive. Increase Node's heap:

```bash
NODE_OPTIONS="--max-old-space-size=4096" npm run build
```

---

## Project Structure

```
src/
├── app/
│   ├── core/
│   │   ├── constants.ts          # API base URL logic
│   │   ├── guards/               # authGuard, adminGuard
│   │   ├── interceptors/         # authInterceptor (JWT header)
│   │   └── services/             # Shared HTTP services
│   ├── features/
│   │   ├── home/                 # Landing page
│   │   ├── auth/                 # Login & Register
│   │   ├── dashboard/            # AI trip planner (main feature)
│   │   ├── gallery/              # Trip gallery
│   │   ├── admin/                # Admin panel
│   │   └── share/                # Public shareable trip view
│   └── shared/                   # Reusable components & utilities
└── environments/                 # Angular environment configs
```
