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
4. Use the direct connection details and keep SSL enabled.

Set these env vars from Neon:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`

Example `DB_URL`:

```text
jdbc:postgresql://ep-xxxx.neon.tech/neondb?sslmode=require
```

Notes:

- use the Neon host/user/password for the direct Postgres connection
- keep `sslmode=require` in `DB_URL`

### 3. Render

1. Create a new `Web Service`.
2. Connect the GitLab repository.
3. Select `Blueprint` with `render.yaml`, or create a Docker web service manually.
4. Render will build from `Dockerfile`.
5. Add env vars if Render does not import them automatically from `render.yaml`:
   - `RAPIDAPI_KEY`
   - `RAPIDAPI_HOST`
   - `DUFFEL_API_KEY`
   - `DUFFEL_ENABLED=true`
   - `DB_URL`
   - `DB_USERNAME`
   - `DB_PASSWORD`
   - `APP_BASE_URL`
   - `ADMIN_API_TOKEN`
   - `KAKAO_ENABLED=false`

Recommended values:

- `APP_BASE_URL=https://<your-render-domain>`
- `ADMIN_API_TOKEN=<long-random-secret>`

Note:
- Render does not currently provide Java as a native runtime.
- This repo includes a Docker-based free-tier deployment path for Render.
- `render.yaml` already defines the non-secret defaults used for deployment.

### 4. Notes

- The app reads `server.port` from `PORT`.
- Scheduler stays enabled for cache prewarming.
- User requests still work on cold cache via on-demand fallback.
- Admin cache endpoints stay disabled unless `ADMIN_API_TOKEN` is set.
- Kakao is explicitly disabled in `render.yaml` until NCP SENS credentials are provisioned.
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

Admin cache verification example:

```text
POST /api/admin/cache/clear
X-Admin-Token: <ADMIN_API_TOKEN>
```
