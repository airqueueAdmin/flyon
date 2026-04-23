## Render + Neon Deployment

### 1. Build locally

```powershell
mvn clean package
java -jar target/flight-platform-0.1.0-SNAPSHOT.jar
```

### 2. Neon

1. Create a free Neon project.
2. Create or use the default database and role.
3. Copy the Postgres connection details.

Set these env vars from Neon:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`

Example `DB_URL`:

```text
jdbc:postgresql://ep-xxxx.neon.tech/neondb?sslmode=require
```

### 3. Render

1. Create a new `Web Service`.
2. Connect the GitHub repository.
3. Select the `Docker` runtime.
4. Render will build from `Dockerfile`.
5. Add env vars:
   - `RAPIDAPI_KEY`
   - `RAPIDAPI_HOST`
   - `DUFFEL_API_KEY`
   - `DUFFEL_ENABLED=true`
   - `DB_URL`
   - `DB_USERNAME`
   - `DB_PASSWORD`

Note:
- Render does not currently provide Java as a native runtime.
- This repo includes a Docker-based free-tier deployment path for Render.

### 4. Notes

- The app reads `server.port` from `PORT`.
- Scheduler stays enabled for cache prewarming.
- User requests still work on cold cache via on-demand fallback.
- Logs to check:
  - `CACHE_HIT`
  - `CACHE_MISS`
  - `API_CALL`
  - `FALLBACK_USED`

### 5. Verify

Call:

```text
POST /api/flights/search
```

Example body:

```json
{
  "origin": "ICN",
  "destination": "NRT",
  "departureDate": "2026-05-01"
}
```
