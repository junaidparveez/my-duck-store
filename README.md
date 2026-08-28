## Prerequisites

- Java 21+
- Docker & Docker Compose
- Node.js 20+

---

## Quickstart

```bash
# 1. Start the database
docker compose up -d

# 2. Start the backend (http://localhost:8080)
cd backend
./mvnw spring-boot:run

# 3. Start the frontend (http://localhost:5173)
cd frontend
npm install        # first time only
npm run dev
