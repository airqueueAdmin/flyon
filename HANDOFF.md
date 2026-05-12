# Flight Platform Handoff

## Summary

This repo is a Spring Boot flight-price service with:

- cache-first + on-demand external fetch
- optional scheduler-based cache prewarming
- RapidAPI as primary provider
- Duffel as fallback provider
- local PostgreSQL via Docker Compose
- built-in static web UI (`/` and `/tracking.html`)
- Docker-based deployment prep for Render
- Kakao Login + Kakao Talk Message API (`나에게 메시지 보내기`) notifications

## Kakao Login Migration: COMPLETE

The full migration from NCP SENS AlimTalk to Kakao Login-based notifications is done as of 2026-05-07.

All 6 steps completed:

1. Backend auth endpoints — done
2. Compile stabilization — done
3. Sender transport swap (NCP SENS → Kakao Message API) — done
4. Tracking storage/validation swap — done
5. Scheduler/tests alignment — done
6. Browser login flow + submit wiring — done

`mvn -q test` passed on `2026-05-07`.

NCP SENS is no longer in use. All Kakao notification code now targets `POST https://kapi.kakao.com/v2/api/talk/memo/default/send`.

## Current Runtime Architecture

### Search flow

1. Web request checks in-memory cache
2. If cache miss, it checks PostgreSQL
3. If DB miss, it tries on-demand external fetch
4. RapidAPI is attempted first
5. If RapidAPI fails or is circuit-open, Duffel is used
6. Successful results are persisted to DB and cached

### Tracking & Notification flow

1. Scheduler calls `FlightService.checkTrackedPrices()` every 5 minutes (`tracking.interval-ms: 300000`)
2. For each active `Tracking`, latest price is fetched from cache/DB
3. If price dropped below previous or target and Kakao is opted-in, `PriceDropNotification` is created
4. `PriceTrackerScheduler` applies duplicate suppression (`lastNotifiedPrice`) and min-drop thresholds
5. `KakaoNotificationService.sendAlimTalk()` fires via Kakao Talk Message API using `tracking.kakaoAccessToken`
6. `KakaoAuthService.refreshTrackingTokensIfNeeded(...)` is called before each send
7. On successful send, `tracking.lastNotifiedPrice` is updated; `tracking.lastCheckedPrice` is updated and persisted

### Scheduler

- Prefetch scheduler runs every 10 minutes (`prefetch.interval-ms: 600000`)
- It prewarms popular routes using rotation + hot-route priority
- The system does not depend solely on scheduler data anymore

### Storage

- Default app config requires DB env vars (no H2 fallback in current `application.yml`)
- Local Docker runtime uses PostgreSQL
- JPA `ddl-auto=update` creates schema automatically

## Important Recent Changes

### 1. Hybrid cache-first + on-demand model

Implemented in:

- [src/main/java/com/airplanehome/flight/service/FlightService.java](src/main/java/com/airplanehome/flight/service/FlightService.java)
- [src/main/java/com/airplanehome/flight/service/FlightPrefetchService.java](src/main/java/com/airplanehome/flight/service/FlightPrefetchService.java)

Key behavior:

- user path does not call APIs when cache/DB has data
- cold miss triggers on-demand fetch
- successful fetch persists and populates cache
- per-key in-flight lock prevents duplicate fetches
- per-minute on-demand guard limits request path usage

Relevant logs:

- `CACHE_HIT`
- `CACHE_MISS`
- `CACHE_MISS_ON_DEMAND`
- `DB_FALLBACK_HIT`
- `DB_FALLBACK_MISS`
- `ON_DEMAND_API_CALL`
- `API_CALL`
- `FALLBACK_TO_DUFFEL`
- `FALLBACK_USED`

### 2. RapidAPI circuit breaker + hourly rate limit

Implemented in:

- [src/main/java/com/airplanehome/flight/client/RapidApiClient.java](src/main/java/com/airplanehome/flight/client/RapidApiClient.java)
- [src/main/java/com/airplanehome/flight/client/RapidApiProvider.java](src/main/java/com/airplanehome/flight/client/RapidApiProvider.java)

Behavior:

- repeated `429` opens a 10-minute cooldown window
- while open, RapidAPI provider is skipped immediately
- per-hour call counter enforces `rapidapi.max-calls-per-hour` (default 50)
- location lookup results cached 24 hours in memory

### 3. Prefetch dedupe and summarized logging

Implemented in:

- [src/main/java/com/airplanehome/flight/service/FlightPrefetchService.java](src/main/java/com/airplanehome/flight/service/FlightPrefetchService.java)

Behavior:

- duplicate exact route/date fetches within one cycle are avoided
- overlapping persistence is merged before saving
- `PREFETCH_UPDATE` is route-level summarized
- `refresh(origin, destination, departureDate)` method supports manual forced refresh

### 4. Duffel integration fixes

Implemented in:

- [src/main/java/com/airplanehome/flight/client/DuffelApiProvider.java](src/main/java/com/airplanehome/flight/client/DuffelApiProvider.java)

Important fixes:

- uses `Duffel-Version: v2`
- supports insecure SSL option
- airline mapping now prefers:
  - `operating_carrier.name`
  - then `marketing_carrier.name`
  - only then `owner.name`
- `Duffel Airways` is explicitly rejected as a persisted airline name

Previously bad rows with `airline = 'Duffel Airways'` were deleted from PostgreSQL.

### 5. Exchange rate SSL handling

Implemented in:

- [src/main/java/com/airplanehome/flight/FlightPlatformApplication.java](src/main/java/com/airplanehome/flight/FlightPlatformApplication.java)
- [src/main/java/com/airplanehome/flight/service/ExchangeRateService.java](src/main/java/com/airplanehome/flight/service/ExchangeRateService.java)
- [src/main/resources/application.yml](src/main/resources/application.yml)

Behavior:

- exchange-rate calls now use a dedicated `exchangeRateRestTemplate`
- supports `app.exchange-rate.insecure-ssl`
- default config currently disables insecure SSL (`EXCHANGE_RATE_INSECURE_SSL=false`)

### 6. Cache eviction support

Implemented in:

- [src/main/java/com/airplanehome/flight/service/SharedFlightCacheService.java](src/main/java/com/airplanehome/flight/service/SharedFlightCacheService.java)
- [src/main/java/com/airplanehome/flight/service/FlightPrefetchService.java](src/main/java/com/airplanehome/flight/service/FlightPrefetchService.java)
- [src/main/java/com/airplanehome/flight/controller/AdminCacheController.java](src/main/java/com/airplanehome/flight/controller/AdminCacheController.java)
- [src/main/java/com/airplanehome/flight/controller/dto/AdminCacheRequest.java](src/main/java/com/airplanehome/flight/controller/dto/AdminCacheRequest.java)

Admin endpoints:

- `POST /api/admin/cache/evict`
- `POST /api/admin/cache/refresh`
- `POST /api/admin/cache/clear`

Auth behavior:

- requires `X-Admin-Token` header
- server compares it to `ADMIN_API_TOKEN`
- if `ADMIN_API_TOKEN` is missing, admin cache API is disabled and returns `503`
- if token is wrong or missing, returns `401`

Request body for `evict` and `refresh`:

- `origin`
- `destination`
- `departureDate`

### 7. Kakao Login + Kakao Talk Message API notification system

NCP SENS has been fully removed. The system now uses Kakao Login OAuth to obtain user tokens, then sends messages via `나에게 메시지 보내기`.

Key files:

- [src/main/java/com/airplanehome/flight/service/KakaoNotificationService.java](src/main/java/com/airplanehome/flight/service/KakaoNotificationService.java)
- [src/main/java/com/airplanehome/flight/service/KakaoNotificationProperties.java](src/main/java/com/airplanehome/flight/service/KakaoNotificationProperties.java)
- [src/main/java/com/airplanehome/flight/service/KakaoAuthService.java](src/main/java/com/airplanehome/flight/service/KakaoAuthService.java)
- [src/main/java/com/airplanehome/flight/model/KakaoAuthConnection.java](src/main/java/com/airplanehome/flight/model/KakaoAuthConnection.java)
- [src/main/java/com/airplanehome/flight/repository/KakaoAuthConnectionRepository.java](src/main/java/com/airplanehome/flight/repository/KakaoAuthConnectionRepository.java)
- [src/main/java/com/airplanehome/flight/service/FlightService.java](src/main/java/com/airplanehome/flight/service/FlightService.java)
- [src/main/java/com/airplanehome/flight/model/Tracking.java](src/main/java/com/airplanehome/flight/model/Tracking.java)
- [src/main/java/com/airplanehome/flight/controller/NotificationController.java](src/main/java/com/airplanehome/flight/controller/NotificationController.java)
- [src/main/resources/static/kakao-callback.html](src/main/resources/static/kakao-callback.html)

Backend auth endpoints:

- `GET /api/notifications/kakao/auth/status`
- `GET /api/notifications/kakao/auth/start`
- `GET /api/notifications/kakao/auth/callback?code=...&state=...`
- `GET /api/notifications/kakao/auth/connections/{connectionId}`

Notification send behavior:

- provider: `kakao-message-api`
- outbound: `POST https://kapi.kakao.com/v2/api/talk/memo/default/send`
- auth: Bearer access token from `Tracking.kakaoAccessToken`
- `KakaoAuthService.refreshTrackingTokensIfNeeded(...)` is called before send
- message shape: Kakao `template_object` text template + web buttons (`추적 목록 보기`, `스카이스캐너 이동`)
- notification fires only when:
  - `kakaoNotificationEnabled = true`
  - `kakaoOptIn = true`
  - `tracking.isKakaoLinked()` is true
  - `kakaoAccessToken` is non-empty
  - price dropped below previous or target price

`PriceTrackerScheduler` enforces:

- duplicate suppression via `tracking.lastNotifiedPrice`
- `app.kakao.min-price-drop-krw` threshold (default 10000 KRW)
- `app.kakao.min-price-drop-percent` threshold (default 5%)

`Tracking` now stores:

- `kakaoUserId`
- `kakaoAccessToken`
- `kakaoRefreshToken`
- `kakaoAccessTokenExpiresAt`
- `kakaoRefreshTokenExpiresAt`
- `kakaoNickname`
- `kakaoNotificationEnabled`
- `kakaoOptIn`
- `lastNotifiedPrice`

Browser flow:

- user enables Kakao alert checkbox → clicks connect button
- browser calls `/api/notifications/kakao/auth/start` → opens Kakao OAuth popup
- Kakao redirects to `/kakao-callback.html`
- callback page exchanges `code` via backend callback API
- callback sends `connectionId` back with `postMessage`
- browser stores `connectionId` in localStorage
- tracking creation request includes `kakaoConnectionId`

Operational APIs:

- `GET /api/notifications/kakao/status`
- `GET /api/notifications/kakao/example`
- `GET /api/notifications/kakao/trackings/{trackingId}/preview?previousPrice=320000&currentPrice=270000`

### Kakao deployment checklist

Kakao Developers setup:

- create or reuse a Kakao Developers application
- enable `카카오 로그인`
- register Redirect URI: `https://your-domain/kakao-callback.html`
- enable the Kakao Talk message permission (`talk_message`) for sending messages to the logged-in user
- confirm the app `REST API 키`
- if client secret protection is enabled, also confirm `Client Secret`

Required server environment variables:

- `KAKAO_ENABLED=true`
- `KAKAO_PROVIDER=kakao-message-api`
- `KAKAO_REST_API_KEY=<Kakao REST API key>`
- `KAKAO_REDIRECT_URI=https://your-domain/kakao-callback.html`
- `APP_BASE_URL=https://your-domain`

Optional:

- `KAKAO_CLIENT_SECRET=<set if Kakao app uses client secret>`
- `KAKAO_MIN_PRICE_DROP_KRW` (default: 10000)
- `KAKAO_MIN_PRICE_DROP_PERCENT` (default: 5)

Important caution:

- Kakao Developers Redirect URI and `KAKAO_REDIRECT_URI` must match exactly
- this is login-based `나에게 메시지 보내기`, not business AlimTalk
- each user must connect Kakao login individually before receiving notifications

Recommended production verification order:

1. apply env vars in the deployment platform
2. redeploy the app
3. call `GET /api/notifications/kakao/auth/status`
4. confirm `ready=true` and `provider=kakao-message-api`
5. open the site in a browser and complete Kakao login connection
6. create a tracking with Kakao notifications enabled
7. verify the stored connection flow and actual message delivery

### 8. KST time support

Implemented in:

- [src/main/java/com/airplanehome/flight/time/TimeSupport.java](src/main/java/com/airplanehome/flight/time/TimeSupport.java)
- [src/main/java/com/airplanehome/flight/serialization/KstLocalDateTimeSerializer.java](src/main/java/com/airplanehome/flight/serialization/KstLocalDateTimeSerializer.java)

Behavior:

- `TimeSupport.nowKst()` returns `LocalDateTime` in `Asia/Seoul`
- `KstLocalDateTimeSerializer` serializes `Tracking.lastUpdatedAt` as ISO-8601 with `+09:00` offset

### 9. Expanded popular routes

`app.prefetch.routes` now covers 24 routes (was 4):

- Japan: ICN|NRT, ICN|FUK, ICN|KIX, ICN|TYO, ICN|CTS, ICN|OKA
- Southeast Asia: ICN|BKK, ICN|HKG, ICN|TPE, ICN|SIN, ICN|DAD, ICN|SGN, ICN|MNL, ICN|CEB
- North America: ICN|SFO, ICN|LAX, ICN|JFK, ICN|SEA, ICN|YVR, ICN|GUM
- Oceania/Europe: ICN|SYD, ICN|MEL, ICN|CDG, ICN|LHR

### 10. Tracking UI, Skyscanner redirect, and localization updates

Implemented in:

- [src/main/resources/static/app.js](src/main/resources/static/app.js)
- [src/main/resources/static/index.html](src/main/resources/static/index.html)
- [src/main/resources/static/tracking.html](src/main/resources/static/tracking.html)
- [src/main/resources/static/app.css](src/main/resources/static/app.css)

Behavior:

- airport codes displayed in `ICN (인천)` style across search and tracking UI
- tracking cards redirect to Skyscanner using per-tracking route/date/passenger data
- tracking cards show current lowest-price airline and departure times
- user-facing API error messages localized to Korean
- Kakao payload includes `추적 목록 보기` and `스카이스캐너 이동` buttons

### 11. Privacy hardening for tracking + Kakao

Implemented in:

- [src/main/java/com/airplanehome/flight/controller/dto/TrackingRequest.java](src/main/java/com/airplanehome/flight/controller/dto/TrackingRequest.java)
- [src/main/java/com/airplanehome/flight/model/Tracking.java](src/main/java/com/airplanehome/flight/model/Tracking.java)
- [src/main/java/com/airplanehome/flight/service/FlightService.java](src/main/java/com/airplanehome/flight/service/FlightService.java)
- [src/main/java/com/airplanehome/flight/repository/PriceHistoryRepository.java](src/main/java/com/airplanehome/flight/repository/PriceHistoryRepository.java)

Behavior:

- tracking creation no longer stores `email`
- phone number is no longer collected (replaced by Kakao connection)
- `Tracking.phoneNumber` and `Tracking.email` are hidden from JSON responses
- deleting a tracking now also deletes related `price_history` rows
- tracking deletion is wrapped in a transaction

## Local Docker Setup

### Files

- [Dockerfile](Dockerfile)
- [docker-compose.local.yml](docker-compose.local.yml)
- [.dockerignore](.dockerignore)
- [src/main/resources/application.yml](src/main/resources/application.yml)

### Compose services

`docker-compose.local.yml` starts:

- `flight-platform-postgres`
- `flight-platform-local`

PostgreSQL settings:

- DB: `flight_platform`
- user: `flight`
- password: `flight`
- port: `5432`

App settings in Compose:

- `DB_URL=jdbc:postgresql://postgres:5432/flight_platform`
- `DB_USERNAME=flight`
- `DB_PASSWORD=flight`

### Local env file

There is a local-only env file:

- [\.env.local](.env.local)

Current purpose:

- enables Duffel in local Docker
- provides local `DUFFEL_API_KEY`
- provides local `ADMIN_API_TOKEN`
- provides `RAPIDAPI_HOST`
- enables local SSL-relaxed API clients for Docker:
  - `DUFFEL_INSECURE_SSL=true`
  - `RAPIDAPI_INSECURE_SSL=true`
  - `EXCHANGE_RATE_INSECURE_SSL=true`

Important:

- treat `.env.local` as sensitive
- rotate the Duffel token if this repo is shared
- if Duffel starts returning `401 expired_access_token`, replace `DUFFEL_API_KEY` there and rebuild the app container

### Commands

Start local stack:

```powershell
docker compose -f docker-compose.local.yml up -d --build
```

Restart only app:

```powershell
docker compose -f docker-compose.local.yml up -d --build app
```

Inspect:

```powershell
docker compose -f docker-compose.local.yml ps
docker logs --tail 120 flight-platform-local
docker exec flight-platform-postgres psql -U flight -d flight_platform -c "\dt"
```

Stop stack:

```powershell
docker compose -f docker-compose.local.yml down
```

### Current Docker state (as of 2026-05-12)

Running containers:

- `flight-platform-postgres` (postgres:16-alpine) — port 5432, healthy
- `flight-platform-local` (air-app) — port 8080, up

Docker Desktop was freshly installed on the `Administrator` account (`C:\Program Files\Docker\Docker`).
WSL2 (Ubuntu) was installed and is running.
The previous Docker setup was under `D:\Users\user`.

Source repository: `https://gitlab.com/syl0309/air.git`
Working directory: `C:\Users\Administrator\IdeaProjects\air`

## Verified Local Database State

The PostgreSQL schema exists and includes:

- `flight_price`
- `price_history`
- `tracking`

DB data has been confirmed to populate from Duffel-backed prefetch runs.

Note:

- stored rows are expanded into `morning/afternoon/evening` time buckets
- prefetch also creates approximate coverage for nearby dates
- DB counts will be larger than raw provider result counts

## Important Files

- [src/main/java/com/airplanehome/flight/service/FlightService.java](src/main/java/com/airplanehome/flight/service/FlightService.java)
- [src/main/java/com/airplanehome/flight/service/FlightPrefetchService.java](src/main/java/com/airplanehome/flight/service/FlightPrefetchService.java)
- [src/main/java/com/airplanehome/flight/service/SharedFlightCacheService.java](src/main/java/com/airplanehome/flight/service/SharedFlightCacheService.java)
- [src/main/java/com/airplanehome/flight/service/ExchangeRateService.java](src/main/java/com/airplanehome/flight/service/ExchangeRateService.java)
- [src/main/java/com/airplanehome/flight/service/KakaoNotificationService.java](src/main/java/com/airplanehome/flight/service/KakaoNotificationService.java)
- [src/main/java/com/airplanehome/flight/service/KakaoAuthService.java](src/main/java/com/airplanehome/flight/service/KakaoAuthService.java)
- [src/main/java/com/airplanehome/flight/scheduler/PriceTrackerScheduler.java](src/main/java/com/airplanehome/flight/scheduler/PriceTrackerScheduler.java)
- [src/main/java/com/airplanehome/flight/service/PriceDropNotification.java](src/main/java/com/airplanehome/flight/service/PriceDropNotification.java)
- [src/main/java/com/airplanehome/flight/client/RapidApiClient.java](src/main/java/com/airplanehome/flight/client/RapidApiClient.java)
- [src/main/java/com/airplanehome/flight/client/RapidApiProvider.java](src/main/java/com/airplanehome/flight/client/RapidApiProvider.java)
- [src/main/java/com/airplanehome/flight/client/DuffelApiProvider.java](src/main/java/com/airplanehome/flight/client/DuffelApiProvider.java)
- [src/main/java/com/airplanehome/flight/controller/NotificationController.java](src/main/java/com/airplanehome/flight/controller/NotificationController.java)
- [src/main/java/com/airplanehome/flight/controller/ApiExceptionHandler.java](src/main/java/com/airplanehome/flight/controller/ApiExceptionHandler.java)
- [src/main/java/com/airplanehome/flight/model/Tracking.java](src/main/java/com/airplanehome/flight/model/Tracking.java)
- [src/main/java/com/airplanehome/flight/model/KakaoAuthConnection.java](src/main/java/com/airplanehome/flight/model/KakaoAuthConnection.java)
- [src/main/java/com/airplanehome/flight/time/TimeSupport.java](src/main/java/com/airplanehome/flight/time/TimeSupport.java)
- [src/main/java/com/airplanehome/flight/FlightPlatformApplication.java](src/main/java/com/airplanehome/flight/FlightPlatformApplication.java)
- [src/main/resources/application.yml](src/main/resources/application.yml)
- [src/main/resources/static/index.html](src/main/resources/static/index.html)
- [src/main/resources/static/tracking.html](src/main/resources/static/tracking.html)
- [src/main/resources/static/kakao-callback.html](src/main/resources/static/kakao-callback.html)
- [docker-compose.local.yml](docker-compose.local.yml)
- [.env.local](.env.local)
- [Dockerfile](Dockerfile)
- [render.yaml](render.yaml)
- [DEPLOY_RENDER_NEON.md](DEPLOY_RENDER_NEON.md)

## Known Runtime Behaviors

### 1. DB delete does not clear in-memory cache automatically

If rows are deleted directly from PostgreSQL, web responses can still show old data until cache expires or is explicitly evicted.

Admin cache endpoints exist for explicit eviction/refresh, but only work when `ADMIN_API_TOKEN` is configured.

### 2. RapidAPI is often unavailable due to quota

Observed behavior:

- RapidAPI frequently returns `429`
- circuit breaker then skips it for 10 minutes
- per-hour rate cap (default 50 calls/hour) also limits usage
- Duffel often becomes the effective provider

### 3. Duffel token validity matters

Past issues seen during setup:

- unsupported version when header was `v1`
- insufficient permissions
- expired access token

Current code is correct; failures here are usually credential/account issues.

### 4. Exchange rate conversion can fall back

If the external exchange-rate API fails, the service falls back to:

- cached exchange rate, if available
- otherwise default rate `1300`

### 5. Kakao notification requires valid Kakao REST API key and redirect URI

If `KAKAO_REST_API_KEY` or `KAKAO_REDIRECT_URI` is empty, the service logs `KAKAO_FAILED: missing configuration` and skips silently.

Kakao access tokens expire and are refreshed automatically via `KakaoAuthService.refreshTrackingTokensIfNeeded(...)` before each send attempt.

### 6. API exceptions are normalized to JSON responses

Implemented in:

- [src/main/java/com/airplanehome/flight/controller/ApiExceptionHandler.java](src/main/java/com/airplanehome/flight/controller/ApiExceptionHandler.java)

Behavior:

- `IllegalArgumentException` → `400`
- `IllegalStateException` → `503`
- admin auth failures → `401`
- `EntityNotFoundException` → `404`
- response body shape is `{ "message": "..." }`

## Environment Variables

Minimum useful set:

- `RAPIDAPI_KEY`
- `RAPIDAPI_HOST`
- `DUFFEL_ENABLED`
- `DUFFEL_API_KEY`
- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`

Optional:

- `ADMIN_API_TOKEN` — required only if admin cache API should be enabled
- `PORT`
- `DUFFEL_VERSION`
- `DUFFEL_INSECURE_SSL`
- `RAPIDAPI_INSECURE_SSL`
- `EXCHANGE_RATE_INSECURE_SSL`
- `APP_BASE_URL`

Kakao (required only if Kakao notifications are wanted):

- `KAKAO_ENABLED`
- `KAKAO_PROVIDER` (set to `kakao-message-api`)
- `KAKAO_REST_API_KEY`
- `KAKAO_REDIRECT_URI`
- `APP_BASE_URL`
- `KAKAO_CLIENT_SECRET` (optional, only if Kakao app enforces it)
- `KAKAO_MIN_PRICE_DROP_KRW` (default: 10000)
- `KAKAO_MIN_PRICE_DROP_PERCENT` (default: 5)

## Deployment

Service is live at: https://flight-platform.onrender.com

- Render (free plan) + Neon PostgreSQL
- `render.yaml` includes:
  - `DUFFEL_ENABLED: "true"` — Duffel is on by default
  - `KAKAO_ENABLED: "false"` — Kakao disabled until credentials are provisioned
  - secret placeholders for `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `RAPIDAPI_KEY`, `DUFFEL_API_KEY`, `APP_BASE_URL`, `ADMIN_API_TOKEN`

### UptimeRobot keep-alive

Render free plan spins down after 15 minutes of inactivity (cold start ~30–60s).
UptimeRobot (free) pings `/health` every 5 minutes to prevent spin-down.

- Monitor URL: `https://flight-platform.onrender.com/health`
- Interval: 5 minutes

## Recommended Next Steps

1. Set `KAKAO_ENABLED=true` and provision Kakao env vars in Render when ready to enable notifications
2. Set `ADMIN_API_TOKEN` in the actual runtime environment before using admin cache endpoints
3. Rotate `.env.local` Duffel token if it expires
4. Tune Kakao notification thresholds (`KAKAO_MIN_PRICE_DROP_KRW`, `KAKAO_MIN_PRICE_DROP_PERCENT`) for production behavior

## Suggested Prompt For The Next Agent

> Read `HANDOFF.md` first. This Spring Boot flight service is live at https://flight-platform.onrender.com (Render free plan + Neon PostgreSQL). UptimeRobot pings `/health` every 5 minutes to prevent Render's 15-minute idle spin-down. The service supports cache-first + on-demand fetch, RapidAPI primary, Duffel fallback, scheduler prewarming (24 routes), local PostgreSQL via Docker Compose, built-in static UI (`/` and `/tracking.html`), Kakao Login + Kakao Talk Message API (`나에게 메시지 보내기`) notifications with token auto-refresh, admin cache endpoints protected by `X-Admin-Token` / `ADMIN_API_TOKEN`, KST timestamp serialization, and per-hour RapidAPI rate limiting with circuit breaker. The NCP SENS migration is complete — do not reference NCP SENS. Local dev runs on `C:\Users\Administrator\IdeaProjects\air` with Docker Desktop (WSL2/Ubuntu). Continue from the current repo state without reverting changes.
