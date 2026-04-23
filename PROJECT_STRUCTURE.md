# Project Structure

개발자를 위한 현재 프로젝트 구조 요약 문서입니다.

## Runtime Source

```text
airplane-home/
├─ src/
│  ├─ main/
│  │  ├─ java/com/airplanehome/flight/
│  │  │  ├─ client/
│  │  │  ├─ controller/
│  │  │  │  └─ dto/
│  │  │  ├─ model/
│  │  │  ├─ repository/
│  │  │  ├─ scheduler/
│  │  │  ├─ service/
│  │  │  └─ FlightPlatformApplication.java
│  │  └─ resources/
│  │     ├─ application.yml
│  │     └─ static/
│  │        ├─ index.html
│  │        ├─ tracking.html
│  │        ├─ app.css
│  │        └─ app.js
│  └─ test/
│     └─ java/com/airplanehome/flight/
├─ pom.xml
└─ PROJECT_STRUCTURE.md
```

## Package Roles

- `client`: 외부 API 연동
- `controller`: HTTP API 진입점
- `controller/dto`: 요청 DTO
- `model`: JPA 엔티티
- `repository`: Spring Data JPA repository
- `service`: 비즈니스 로직
- `scheduler`: 주기 작업

## Main Flow

1. 사용자 검색 요청
2. `FlightService`
3. `FlightPrefetchService`
4. `SharedFlightCacheService`
5. cache miss 시 DB fallback
6. 필요 시 KRW 환산 후 응답

## Notes

- 현재 구조는 `src/main` 단일 앱 기준입니다.
- 루트의 예전 보조 모듈/빌드 산출물/로그는 제거되었습니다.
