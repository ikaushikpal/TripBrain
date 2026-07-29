# TripBrain — Smart AI Travel Itinerary Planner ✈️

TripBrain is a pair-programmed web application that plans travel itineraries using Spring AI and a multi-agent orchestration architecture. It rotates through active LLM models, matches user parameters, generates clean A4 PDF documents with custom typography & icons, caches search details using Valkey/Redis, and uploads resulting documents directly to Backblaze B2 storage.

---

## 🛠️ Technology Stack

- **Backend**: Java 25, Spring Boot 3.5.x, Spring AI 1.1.4 (OpenAI, Gemini & Groq integrations), Hibernate/JPA.
- **Frontend**: Angular, CSS Variables, RxJS, Server-Side Rendering (SSR) & Client-Side fallback.
- **Database & Cache**: PostgreSQL (Storage), Qdrant / Opensearch (Vector Store), Valkey / Redis (Caching).
- **PDF Compilation**: iText Core (version 9.6.0), custom emoji mappings, Roboto typography.
- **File Storage**: Backblaze B2 (S3-Compatible API) for presigned download redirects.

---

## 📂 Project Structure

```
trip-brain/
├── backend/                  # Spring Boot application
│   ├── src/main/java/        # Java source code
│   └── build.gradle          # Gradle project configuration
├── frontend/                 # Client applications
│   └── trip-brain-frontend-app/
│       ├── src/              # Angular components, signals, routes
│       └── angular.json      # Angular build options
├── Dockerfile                # Multi-stage image build definition
├── docker-compose.yml        # Docker execution setup
└── .gitignore                # Global ignore rules
```

---

## ⚡ Development Running

### 1. Backend Server
Make sure you have Java 25 installed. Configure your API keys in `backend/.env`, then run:
```bash
cd backend
./gradlew bootRun
```
The backend API will start listening on [http://localhost:8080](http://localhost:8080).

### 2. Frontend Development Server
Ensure Node.js 20+ is installed:
```bash
cd frontend/trip-brain-frontend-app
npm install
npm run start
```
Open [http://localhost:4200](http://localhost:4200) to access the interactive web dashboard.

---

## 🚀 Combined Production Packaging

We have automated the production build pipeline. Spring Boot acts as a single-host server serving both client static files and the API:

### Local Packaging
Running `./gradlew build` in the `backend/` directory will:
1. Run `npm install && npm run build` inside the frontend directory.
2. Copy the resulting static files from `dist/` directly into Spring Boot's static resources directory (`src/main/resources/static/`).
3. Compile and package everything into a single runnable JAR file.

```bash
cd backend
./gradlew build
java -jar build/libs/trip-brain-0.0.1-SNAPSHOT.jar
```

---

## 🐳 Docker Deployment

To build and run the entire application (both frontend and backend served together in a single container):

```bash
# Start the container
docker compose up --build -d

# View application logs
docker compose logs -f
```
The unified application will be available at [http://localhost:8080](http://localhost:8080).
All environment variables are loaded automatically from `backend/.env`.
