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
- Kakao AlimTalk notifications via NCP SENS

The current working directory used during setup was `D:\airplane-home`.

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
5. `KakaoNotificationService.sendAlimTalk()` fires via NCP SENS API (AlimTalk)
6. On successful send, `tracking.lastNotifiedPrice` is updated; `tracking.lastCheckedPrice` is updated and persisted

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

Added methods:

- `SharedFlightCacheService.evict(...)`
- `SharedFlightCacheService.clear()`
- `FlightPrefetchService.evictCache(...)`
- `FlightPrefetchService.clearCache()`
- `FlightPrefetchService.refresh(...)` — force re-fetch from API and persist

Admin endpoints now exist:

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

### 7. Kakao AlimTalk notification system (NEW)

Implemented in:

- [src/main/java/com/airplanehome/flight/service/KakaoNotificationService.java](src/main/java/com/airplanehome/flight/service/KakaoNotificationService.java)
- [src/main/java/com/airplanehome/flight/service/KakaoNotificationProperties.java](src/main/java/com/airplanehome/flight/service/KakaoNotificationProperties.java)
- [src/main/java/com/airplanehome/flight/service/FlightService.java](src/main/java/com/airplanehome/flight/service/FlightService.java)
- [src/main/java/com/airplanehome/flight/model/Tracking.java](src/main/java/com/airplanehome/flight/model/Tracking.java)
- [src/main/java/com/airplanehome/flight/controller/NotificationController.java](src/main/java/com/airplanehome/flight/controller/NotificationController.java)

Behavior:

- provider: NCP SENS (Naver Cloud Platform Simple & Easy Notification Service)
- authentication: HMAC-SHA256 signature on each request
- template: `[항공권 가격 변동 안내]` with route, old price, new price, deep link
- notification is fired only when:
  - `kakaoNotificationEnabled = true`
  - `kakaoOptIn = true`
  - `phoneNumber` is present
  - price dropped below previous or target price
- `examplePayload()` method exists for testing without real API calls
- deep link uses `app.web.base-url` + `?origin=...&destination=...&date=...`
- `PriceTrackerScheduler` enforces:
  - duplicate suppression via `tracking.lastNotifiedPrice`
  - `app.kakao.min-price-drop-krw` threshold (default 10000 KRW)
  - `app.kakao.min-price-drop-percent` threshold (default 5%)
- operational helper APIs now exist:
  - `GET /api/notifications/kakao/status`
  - `GET /api/notifications/kakao/example`
  - `GET /api/notifications/kakao/trackings/{trackingId}/preview?previousPrice=320000&currentPrice=270000`
- `status` endpoint returns:
  - `enabled`
  - `ready`
  - `provider`
  - `templateCode`
  - `plusFriendId`
  - `senderNumberMasked`
  - `appBaseUrl`
  - `minPriceDropKrw`
  - `minPriceDropPercent`
  - `missingConfiguration`
- `preview` endpoint builds the real outbound AlimTalk payload for an existing tracking row without calling NCP

Tracking model now includes:

- `phoneNumber` — normalized to digits-only, `82`-prefixed for Korea
- `kakaoNotificationEnabled`
- `kakaoOptIn` — fallback to `kakaoNotificationEnabled` if not explicitly set
- `lastNotifiedPrice` — persisted for dedup (field exists, guard logic is caller-side)

Required env vars for Kakao:

- `KAKAO_ENABLED`
- `KAKAO_API_KEY` (NCP IAM access key)
- `KAKAO_API_SECRET` (NCP secret key)
- `KAKAO_SENDER_NUMBER`
- `KAKAO_TEMPLATE_CODE`
- `KAKAO_SERVICE_ID`
- `KAKAO_PLUS_FRIEND_ID`
- `APP_BASE_URL`

Optional:

- `KAKAO_PROVIDER` (default: `ncp-sens`)
- `KAKAO_MIN_PRICE_DROP_KRW` (default: 10000)
- `KAKAO_MIN_PRICE_DROP_PERCENT` (default: 5)

### 7a. Where to find NCP SENS values

Important:

- this project does not use Kakao Developers REST/Admin keys for outbound customer notifications
- it uses NCP SENS AlimTalk credentials and metadata
- Kakao Developers keys shared by a user are not directly usable for the current implementation

Where to find each required value:

- `KAKAO_API_KEY`
  - NCP console -> account menu / My Page -> authentication key management
  - use the NCP `Access Key ID`
- `KAKAO_API_SECRET`
  - NCP console -> account menu / My Page -> authentication key management
  - use the NCP `Secret Key`
- `KAKAO_SERVICE_ID`
  - NCP console -> SENS -> Biz Message / AlimTalk project
  - use the project `serviceId`
- `KAKAO_PLUS_FRIEND_ID`
  - NCP console -> SENS -> AlimTalk project -> channels
  - use the registered channel ID from SENS
  - expected form is typically `@...`
  - a Kakao channel search ID like `_xxxxxx` may not be the same value
- `KAKAO_TEMPLATE_CODE`
  - NCP console -> SENS -> AlimTalk project -> templates
  - use the approved template code
- `KAKAO_SENDER_NUMBER`
  - NCP console -> SENS / SMS sender-number management
  - use a registered sender number
- `APP_BASE_URL`
  - the deployed app base URL used for deep links in buttons and tracking pages

Practical verification order:

1. fill the env vars
2. start the app
3. call `GET /api/notifications/kakao/status`
4. confirm `ready=true`
5. call preview endpoint for a real tracking row and compare the payload against the approved NCP template

### 8. KST time support (NEW)

Implemented in:

- [src/main/java/com/airplanehome/flight/time/TimeSupport.java](src/main/java/com/airplanehome/flight/time/TimeSupport.java)
- [src/main/java/com/airplanehome/flight/serialization/KstLocalDateTimeSerializer.java](src/main/java/com/airplanehome/flight/serialization/KstLocalDateTimeSerializer.java)

Behavior:

- `TimeSupport.nowKst()` returns `LocalDateTime` in `Asia/Seoul`
- `KstLocalDateTimeSerializer` serializes `Tracking.lastUpdatedAt` as ISO-8601 with `+09:00` offset

### 9. Expanded popular routes (NEW)

`app.prefetch.routes` now covers 24 routes (was 4):

- Japan: ICN|NRT, ICN|FUK, ICN|KIX, ICN|TYO, ICN|CTS, ICN|OKA
- Southeast Asia: ICN|BKK, ICN|HKG, ICN|TPE, ICN|SIN, ICN|DAD, ICN|SGN, ICN|MNL, ICN|CEB
- North America: ICN|SFO, ICN|LAX, ICN|JFK, ICN|SEA, ICN|YVR, ICN|GUM
- Oceania/Europe: ICN|SYD, ICN|MEL, ICN|CDG, ICN|LHR

### 10. Tracking UI, Skyscanner redirect, and localization updates (NEW)

Implemented in:

- [src/main/resources/static/app.js](src/main/resources/static/app.js)
- [src/main/resources/static/index.html](src/main/resources/static/index.html)
- [src/main/resources/static/tracking.html](src/main/resources/static/tracking.html)
- [src/main/resources/static/app.css](src/main/resources/static/app.css)
- [src/main/java/com/airplanehome/flight/service/KakaoNotificationService.java](src/main/java/com/airplanehome/flight/service/KakaoNotificationService.java)

Behavior:

- airport codes are displayed in `ICN (인천)` style across search and tracking UI
- tracking cards now redirect to Skyscanner using per-tracking route/date/passenger data
- tracking cards show current lowest-price airline and departure times to help users find similar results in Skyscanner
- user-facing API error messages were localized to Korean
- Kakao example/flow copy now describes button-based navigation
- Kakao payload now includes:
  - `추적 목록 보기` button
  - `스카이스캐너 이동` button

Important:

- NCP SENS AlimTalk buttons must match the approved Kakao template exactly
- if the current Kakao template in NCP does not include those buttons, delivery can fail even though the code builds correctly

### 11. Privacy hardening for tracking + Kakao (NEW)

Implemented in:

- [src/main/java/com/airplanehome/flight/controller/dto/TrackingRequest.java](src/main/java/com/airplanehome/flight/controller/dto/TrackingRequest.java)
- [src/main/java/com/airplanehome/flight/model/Tracking.java](src/main/java/com/airplanehome/flight/model/Tracking.java)
- [src/main/java/com/airplanehome/flight/service/FlightService.java](src/main/java/com/airplanehome/flight/service/FlightService.java)
- [src/main/java/com/airplanehome/flight/repository/PriceHistoryRepository.java](src/main/java/com/airplanehome/flight/repository/PriceHistoryRepository.java)
- [src/main/resources/static/index.html](src/main/resources/static/index.html)
- [src/main/resources/static/tracking.html](src/main/resources/static/tracking.html)

Behavior:

- tracking creation no longer stores `email`
- phone number is collected only when Kakao notifications are enabled
- privacy consent and Kakao third-party sharing consent are split in the UI
- Kakao-related checkboxes are no longer pre-checked by default
- `Tracking.phoneNumber` and `Tracking.email` are hidden from JSON responses
- UI shows `maskedPhoneNumber` instead of raw phone number
- deleting a tracking now also deletes related `price_history` rows
- tracking deletion is wrapped in a transaction to avoid `TransactionRequiredException`

Current consent-related fields on `Tracking`:

- `personalDataConsent`
- `personalDataConsentAt`
- `kakaoOptIn`
- `kakaoOptInAt`

Important:

- this is code-level hardening only; it is not a substitute for a full privacy policy, retention policy, or infra/data-governance review
- existing already-stored PII in old rows is not automatically backfilled/erased by these code changes

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

### Commands that worked

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
- [src/main/java/com/airplanehome/flight/scheduler/PriceTrackerScheduler.java](src/main/java/com/airplanehome/flight/scheduler/PriceTrackerScheduler.java)
- [src/main/java/com/airplanehome/flight/service/PriceDropNotification.java](src/main/java/com/airplanehome/flight/service/PriceDropNotification.java)
- [src/main/java/com/airplanehome/flight/client/RapidApiClient.java](src/main/java/com/airplanehome/flight/client/RapidApiClient.java)
- [src/main/java/com/airplanehome/flight/client/RapidApiProvider.java](src/main/java/com/airplanehome/flight/client/RapidApiProvider.java)
- [src/main/java/com/airplanehome/flight/client/DuffelApiProvider.java](src/main/java/com/airplanehome/flight/client/DuffelApiProvider.java)
- [src/main/java/com/airplanehome/flight/controller/ApiExceptionHandler.java](src/main/java/com/airplanehome/flight/controller/ApiExceptionHandler.java)
- [src/main/java/com/airplanehome/flight/model/Tracking.java](src/main/java/com/airplanehome/flight/model/Tracking.java)
- [src/main/java/com/airplanehome/flight/time/TimeSupport.java](src/main/java/com/airplanehome/flight/time/TimeSupport.java)
- [src/main/java/com/airplanehome/flight/serialization/KstLocalDateTimeSerializer.java](src/main/java/com/airplanehome/flight/serialization/KstLocalDateTimeSerializer.java)
- [src/main/java/com/airplanehome/flight/FlightPlatformApplication.java](src/main/java/com/airplanehome/flight/FlightPlatformApplication.java)
- [src/main/resources/application.yml](src/main/resources/application.yml)
- [src/main/resources/static/index.html](src/main/resources/static/index.html)
- [src/main/resources/static/tracking.html](src/main/resources/static/tracking.html)
- [docker-compose.local.yml](docker-compose.local.yml)
- [.env.local](.env.local)
- [Dockerfile](Dockerfile)
- [render.yaml](render.yaml)
- [DEPLOY_RENDER_NEON.md](DEPLOY_RENDER_NEON.md)

## Known Runtime Behaviors

### 1. DB delete does not clear in-memory cache automatically

If rows are deleted directly from PostgreSQL, web responses can still show old data until cache expires or is explicitly evicted.

Reason:

- cache layer is independent from DB
- scheduler/on-demand fetch can repopulate data after manual deletes

Admin cache endpoints now exist for explicit cache eviction/refresh, but they only work when `ADMIN_API_TOKEN` is configured.

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

### 5. Kakao notification requires all NCP SENS env vars

If any of `KAKAO_API_KEY`, `KAKAO_API_SECRET`, `KAKAO_SENDER_NUMBER`, `KAKAO_TEMPLATE_CODE`, `KAKAO_SERVICE_ID`, `KAKAO_PLUS_FRIEND_ID` is empty, the service logs `KAKAO_FAILED: missing configuration` and skips silently.

### 6. API exceptions are normalized to JSON responses

Implemented in:

- [src/main/java/com/airplanehome/flight/controller/ApiExceptionHandler.java](src/main/java/com/airplanehome/flight/controller/ApiExceptionHandler.java)

Behavior:

- `IllegalArgumentException` -> `400`
- `IllegalStateException` -> `503`
- admin auth failures -> `401`
- `EntityNotFoundException` -> `404`
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

Kakao (required only if Kakao AlimTalk is wanted):

- `KAKAO_ENABLED`
- `KAKAO_API_KEY`
- `KAKAO_API_SECRET`
- `KAKAO_SENDER_NUMBER`
- `KAKAO_TEMPLATE_CODE`
- `KAKAO_SERVICE_ID`
- `KAKAO_PLUS_FRIEND_ID`
- `KAKAO_PROVIDER` (default: `ncp-sens`)
- `KAKAO_MIN_PRICE_DROP_KRW` (default: 10000)
- `KAKAO_MIN_PRICE_DROP_PERCENT` (default: 5)

## Deployment Prep

Deployment prep files already exist:

- [render.yaml](render.yaml)
- [DEPLOY_RENDER_NEON.md](DEPLOY_RENDER_NEON.md)

`render.yaml` now includes:

- `DUFFEL_ENABLED: "true"` — Duffel is on by default in Render deploys
- `KAKAO_ENABLED: "false"` — Kakao is disabled by default until credentials are provisioned
- secret placeholders for `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `RAPIDAPI_KEY`, `DUFFEL_API_KEY`, `APP_BASE_URL`, `ADMIN_API_TOKEN`

`DEPLOY_RENDER_NEON.md` is updated for GitLab-based Render deployment and Neon SSL configuration.

Render/Neon deployment config is prepared in-repo, but actual live deployment still requires platform-side setup in Render and Neon.

## Recommended Next Steps

1. Set `ADMIN_API_TOKEN` in the actual runtime environment before using admin cache endpoints
2. Verify and tune Kakao notification thresholds (`KAKAO_MIN_PRICE_DROP_KRW`, `KAKAO_MIN_PRICE_DROP_PERCENT`) for production behavior
3. Finish the actual Render + Neon platform-side setup using the prepared `render.yaml` and deployment doc
4. Rotate `.env.local` Duffel token if it expires
5. Provision real Kakao env vars in the target runtime before enabling `KAKAO_ENABLED`

## Suggested Prompt For The Next Agent

> Read `HANDOFF.md` first. This Spring Boot flight service supports cache-first + on-demand fetch, RapidAPI primary, Duffel fallback, scheduler prewarming (24 routes), local PostgreSQL via Docker Compose, built-in static UI (`/` and `/tracking.html`), dedicated SSL-relaxed RestTemplates for external APIs, corrected Duffel airline mapping, admin cache endpoints (`/api/admin/cache/evict`, `/api/admin/cache/refresh`, `/api/admin/cache/clear`) protected by `X-Admin-Token` / `ADMIN_API_TOKEN`, Kakao AlimTalk notifications via NCP SENS with threshold + duplicate suppression in `PriceTrackerScheduler`, KST timestamp serialization, and per-hour RapidAPI rate limiting with circuit breaker. Continue from the current repo state without reverting changes.
