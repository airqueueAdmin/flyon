function trackEvent(name, params) {
  if (typeof gtag === 'function') {
    gtag('event', name, params);
  }
}

function formatMoney(value) {
  if (value === null || value === undefined || value === "") {
    return "미설정";
  }
  return `${Number(value).toLocaleString("ko-KR")}원`;
}

function formatDateTime(value) {
  if (!value) {
    return "대기 중";
  }
  return new Intl.DateTimeFormat("ko-KR", {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit"
  }).format(new Date(value));
}

function formatDateOnly(value) {
  if (!value) return "";
  return new Intl.DateTimeFormat("ko-KR", {month: "short", day: "numeric"}).format(new Date(value));
}

function formatFlightSummary(tracking) {
  const outboundAirline = tracking.lastAirline || "확인 불가";
  if (tracking.tripType !== "ROUND_TRIP") {
    return outboundAirline;
  }
  const inboundAirline = tracking.lastInboundAirline || "확인 불가";
  return `${outboundAirline} / ${inboundAirline}`;
}

function formatSkyscannerDate(value) {
  if (!value) {
    return "";
  }
  return value.replaceAll("-", "").slice(2);
}

function formatTripType(value) {
  return value === "ROUND_TRIP" ? "왕복" : "편도";
}

function formatTimeBucket(value) {
  if (value === "morning") {
    return "오전";
  }
  if (value === "afternoon") {
    return "오후";
  }
  if (value === "evening") {
    return "저녁";
  }
  return value || "일반";
}

function renderApproximateBadge(flight) {
  if (!flight || !flight.approximate) {
    return "";
  }
  return `<span class="approx-badge">예상 가격</span>`;
}

async function requestJson(url, options) {
  const response = await fetch(url, withTrackingAccess(url, options));
  if (!response.ok) {
    let message = "요청에 실패했습니다.";
    try {
      const body = await response.json();
      if (body.message) {
        message = body.message;
      }
    } catch (error) {
      message = response.statusText || message;
    }
    throw new Error(message);
  }

  if (response.status === 204) {
    return null;
  }
  return response.json();
}

function qs(selector) {
  return document.querySelector(selector);
}

function buildAirportDisplayMap() {
  const entries = [...AIRPORT_OPTIONS.origin, ...AIRPORT_OPTIONS.destination].map((option) => [option.code, option.displayName]);
  return Object.fromEntries(entries);
}

function formatAirport(code) {
  if (!code) {
    return "";
  }
  const normalizedCode = code.trim().toUpperCase();
  const displayName = AIRPORT_DISPLAY_MAP[normalizedCode];
  return displayName ? `${normalizedCode} (${displayName})` : normalizedCode;
}

function formatRoute(origin, destination) {
  return `${formatAirport(origin)} -> ${formatAirport(destination)}`;
}

function buildSkyscannerTrackingUrl(tracking) {
  const origin = (tracking.origin || "").trim().toLowerCase();
  const destination = (tracking.destination || "").trim().toLowerCase();
  const departureDate = formatSkyscannerDate(tracking.departureDate);
  const returnDate = formatSkyscannerDate(tracking.returnDate);
  const isRoundTrip = tracking.tripType === "ROUND_TRIP" && returnDate;
  const path = isRoundTrip
    ? `/transport/flights/${origin}/${destination}/${departureDate}/${returnDate}/`
    : `/transport/flights/${origin}/${destination}/${departureDate}/`;
  const params = new URLSearchParams({
    adultsv2: String(tracking.passengers || 1),
    cabinclass: "economy",
    childrenv2: "",
    ref: "home",
    rtn: isRoundTrip ? "1" : "0",
    preferdirects: "false",
    outboundaltsenabled: "false",
    inboundaltsenabled: "false",
    market: "KR",
    locale: "ko-KR",
    currency: "KRW"
  });
  return `https://www.skyscanner.co.kr${path}?${params.toString()}`;
}

const AIRPORT_OPTIONS = {
  origin: [
    { code: "ICN", label: "ICN (인천)", displayName: "인천" }
  ],
  destination: [
    { code: "NRT", label: "NRT (도쿄 나리타)", displayName: "도쿄 나리타" },
    { code: "FUK", label: "FUK (후쿠오카)", displayName: "후쿠오카" },
    { code: "KIX", label: "KIX (오사카 간사이)", displayName: "오사카 간사이" },
    { code: "TYO", label: "TYO (도쿄)", displayName: "도쿄" },
    { code: "CTS", label: "CTS (삿포로 치토세)", displayName: "삿포로 치토세" },
    { code: "OKA", label: "OKA (오키나와 나하)", displayName: "오키나와 나하" },
    { code: "BKK", label: "BKK (방콕)", displayName: "방콕" },
    { code: "HKG", label: "HKG (홍콩)", displayName: "홍콩" },
    { code: "TPE", label: "TPE (타이베이)", displayName: "타이베이" },
    { code: "SIN", label: "SIN (싱가포르)", displayName: "싱가포르" },
    { code: "DAD", label: "DAD (다낭)", displayName: "다낭" },
    { code: "SGN", label: "SGN (호치민)", displayName: "호치민" },
    { code: "MNL", label: "MNL (마닐라)", displayName: "마닐라" },
    { code: "CEB", label: "CEB (세부)", displayName: "세부" },
    { code: "SFO", label: "SFO (샌프란시스코)", displayName: "샌프란시스코" },
    { code: "LAX", label: "LAX (로스앤젤레스)", displayName: "로스앤젤레스" },
    { code: "JFK", label: "JFK (뉴욕)", displayName: "뉴욕" },
    { code: "SEA", label: "SEA (시애틀)", displayName: "시애틀" },
    { code: "YVR", label: "YVR (밴쿠버)", displayName: "밴쿠버" },
    { code: "GUM", label: "GUM (괌)", displayName: "괌" },
    { code: "SYD", label: "SYD (시드니)", displayName: "시드니" },
    { code: "MEL", label: "MEL (멜버른)", displayName: "멜버른" },
    { code: "CDG", label: "CDG (파리 샤를드골)", displayName: "파리 샤를드골" },
    { code: "LHR", label: "LHR (런던 히드로)", displayName: "런던 히드로" }
  ]
};

const AIRPORT_DISPLAY_MAP = buildAirportDisplayMap();

const SEARCH_WINDOW_DAYS = 7;
const KAKAO_CONNECTION_STORAGE_KEY = "flight-platform.kakao-connection";
const TRACKING_OWNER_STORAGE_KEY = "flight-platform.tracking-owner-token";
const SEARCH_CACHE_STORAGE_KEY = "flight-platform.search-cache";
const TRACKING_OWNER_HEADER = "X-Tracking-Owner-Token";
const KAKAO_CONNECTION_HEADER = "X-Kakao-Connection-Id";

function populateSelect(selector, options, placeholder) {
  const select = qs(selector);
  if (!select) {
    return;
  }

  select.innerHTML = "";

  if (placeholder) {
    const placeholderOption = document.createElement("option");
    placeholderOption.value = "";
    placeholderOption.textContent = placeholder;
    placeholderOption.disabled = true;
    select.appendChild(placeholderOption);
  }

  options.forEach((option, index) => {
    const element = document.createElement("option");
    element.value = option.code;
    element.textContent = option.label;
    if (index === 0) {
      element.selected = true;
    }
    select.appendChild(element);
  });
}

function populateSelectableFilter(selector, options, allLabel) {
  const select = qs(selector);
  if (!select) {
    return;
  }

  select.innerHTML = "";

  const allOption = document.createElement("option");
  allOption.value = "";
  allOption.textContent = allLabel;
  allOption.selected = true;
  select.appendChild(allOption);

  options.forEach((option) => {
    const element = document.createElement("option");
    element.value = option.code;
    element.textContent = option.label;
    select.appendChild(element);
  });
}

function renderNotificationExample(target, payload) {
  if (!target) {
    return;
  }

  const variables = payload && payload.templateVariables ? payload.templateVariables : {};
  const route = variables.route || "인천 -> 도쿄";
  const oldPrice = variables.oldPrice || "320,000원";
  const newPrice = variables.newPrice || "270,000원";
  const buttonLabels = extractNotificationButtons(payload);

  target.innerHTML = `
    <div class="message-bubble">
      <div class="message-badge">카카오 알림</div>
      <strong>${escapeHtml(route)} 가격이 내려갔어요</strong>
      <p>이전 가격 ${escapeHtml(oldPrice)} -> 현재 가격 ${escapeHtml(newPrice)}</p>
      <p>버튼을 눌러 추적 목록을 확인하거나 바로 예약처로 이동할 수 있습니다.</p>
      <div class="message-button-row">
        ${buttonLabels.map((label) => `<span class="message-chip">${escapeHtml(label)}</span>`).join("")}
      </div>
    </div>
  `;
}

async function loadNotificationExample(targetSelector) {
  const target = qs(targetSelector);
  if (!target) {
    return;
  }

  try {
    const payload = await requestJson("/api/notifications/kakao/example");
    renderNotificationExample(target, payload);
  } catch (error) {
    target.innerHTML = `
      <div class="message-bubble">
        <div class="message-badge">카카오 알림</div>
        <strong>가격 변동 알림 예시</strong>
        <p>가격이 내려가면 추적 중인 노선의 현재 가격과 이동 버튼을 함께 보여줍니다.</p>
      </div>
    `;
  }
}

function extractNotificationButtons(payload) {
  const buttons = payload && Array.isArray(payload.buttons) ? payload.buttons : [];
  const labels = buttons
    .map((button) => button.title)
    .filter((value) => value);
  return labels.length ? labels : ["추적 목록 보기", "예약처로 이동"];
}

function getStoredKakaoConnection() {
  const raw = window.localStorage.getItem(KAKAO_CONNECTION_STORAGE_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw);
  } catch (error) {
    window.localStorage.removeItem(KAKAO_CONNECTION_STORAGE_KEY);
    return null;
  }
}

function storeKakaoConnection(connection) {
  window.localStorage.setItem(KAKAO_CONNECTION_STORAGE_KEY, JSON.stringify(connection));
}

function clearKakaoConnection() {
  window.localStorage.removeItem(KAKAO_CONNECTION_STORAGE_KEY);
}

function createTrackingOwnerToken() {
  if (window.crypto && typeof window.crypto.randomUUID === "function") {
    return window.crypto.randomUUID();
  }
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
}

function getTrackingOwnerToken() {
  let token = window.localStorage.getItem(TRACKING_OWNER_STORAGE_KEY);
  if (!token) {
    token = createTrackingOwnerToken();
    window.localStorage.setItem(TRACKING_OWNER_STORAGE_KEY, token);
  }
  return token;
}

function withTrackingAccess(url, options) {
  const nextOptions = options ? { ...options } : {};
  if (!url.startsWith("/api/trackings")) {
    return nextOptions;
  }

  const headers = new Headers(nextOptions.headers || {});
  headers.set(TRACKING_OWNER_HEADER, getTrackingOwnerToken());

  const kakaoConnection = getStoredKakaoConnection();
  if (kakaoConnection && kakaoConnection.connectionId) {
    headers.set(KAKAO_CONNECTION_HEADER, kakaoConnection.connectionId);
  }

  nextOptions.headers = headers;
  return nextOptions;
}

function syncKakaoConnectionFromParams() {
  const url = new URL(window.location.href);
  const connectionId = url.searchParams.get("connection");
  if (!connectionId) {
    return;
  }

  const storedConnection = getStoredKakaoConnection() || {};
  storeKakaoConnection({
    connectionId,
    nickname: storedConnection.nickname || "카카오 사용자",
    kakaoUserId: storedConnection.kakaoUserId || null
  });

  url.searchParams.delete("connection");
  window.history.replaceState({}, document.title, `${url.pathname}${url.search}${url.hash}`);
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll("\"", "&quot;")
    .replaceAll("'", "&#39;");
}

document.addEventListener("DOMContentLoaded", () => {
  syncKakaoConnectionFromParams();
  if (document.body.dataset.page === "restaurants") {
    initRestaurantsPage();
  }
  if (document.body.dataset.page === "search") {
    initSearchPage();
  }
  if (document.body.dataset.page === "tracking") {
    initTrackingPage();
  }
  if (document.body.dataset.page === "deals") {
    initDealsPage();
  }
});

function renderCalendarStrip(target, days, selectedDate, onDateSelect) {
  if (!days || !days.length) {
    target.innerHTML = "";
    return;
  }

  const validPrices = days.filter(d => d.price != null).map(d => Number(d.price));
  const minPrice = validPrices.length ? Math.min(...validPrices) : null;
  const DAY_NAMES = ["일", "월", "화", "수", "목", "금", "토"];

  target.innerHTML = days.map(day => {
    const date = new Date(day.date + "T00:00:00");
    const dayName = DAY_NAMES[date.getDay()];
    const isCheapest = day.price != null && minPrice != null && Number(day.price) === minPrice;
    const isSelected = day.date === selectedDate;
    const hasPrice = day.price != null;
    const priceDisplay = hasPrice ? `${Math.round(Number(day.price) / 10000)}만원` : "-";

    return `
      <button
        class="calendar-day${isCheapest ? " cheapest" : ""}${isSelected ? " selected" : ""}${!hasPrice ? " no-data" : ""}"
        data-date="${escapeHtml(day.date)}"
        type="button"
        ${!hasPrice ? "disabled" : ""}
      >
        <span class="cal-day-name">${dayName}</span>
        <span class="cal-date">${date.getMonth() + 1}/${date.getDate()}</span>
        <span class="cal-price">${priceDisplay}</span>
        ${isCheapest ? '<span class="cal-cheapest-badge">최저</span>' : ""}
      </button>
    `;
  }).join("");

  target.querySelectorAll(".calendar-day[data-date]:not([disabled])").forEach(btn => {
    btn.addEventListener("click", () => {
      onDateSelect(btn.dataset.date);
    });
  });
}

function renderSparkline(container, history) {
  const prices = (history || [])
    .map(h => (h.price !== null && h.price !== undefined) ? Number(h.price) : null)
    .filter(p => p !== null && !isNaN(p));

  if (prices.length < 2) {
    container.innerHTML = `<span class="sparkline-empty">기록이 쌓이면 가격 추이가 표시됩니다.</span>`;
    return;
  }

  const W = 260, H = 56, P = 6;
  const min = Math.min(...prices);
  const max = Math.max(...prices);
  const range = max - min || 1;

  const xs = prices.map((_, i) => P + (i / (prices.length - 1)) * (W - 2 * P));
  const ys = prices.map(v => H - P - ((v - min) / range) * (H - 2 * P));

  const pathD = xs.map((x, i) => `${i === 0 ? "M" : "L"} ${x.toFixed(1)} ${ys[i].toFixed(1)}`).join(" ");
  const areaD = `${pathD} L ${xs[xs.length - 1].toFixed(1)} ${H} L ${xs[0].toFixed(1)} ${H} Z`;

  const first = prices[0];
  const last = prices[prices.length - 1];
  const trendDown = last < first;
  const trendUp = last > first;
  const stroke = trendDown ? "var(--success)" : trendUp ? "#b13c2f" : "var(--muted)";
  const fill = trendDown ? "rgba(32,115,95,0.07)" : trendUp ? "rgba(177,60,47,0.06)" : "rgba(109,93,77,0.05)";

  container.innerHTML = `
    <svg viewBox="0 0 ${W} ${H}" class="sparkline-svg" aria-hidden="true">
      <path d="${areaD}" fill="${fill}" stroke="none"/>
      <path d="${pathD}" fill="none" stroke="${stroke}" stroke-width="1.8" stroke-linejoin="round" stroke-linecap="round"/>
      <circle cx="${xs[0].toFixed(1)}" cy="${ys[0].toFixed(1)}" r="2" fill="${stroke}" opacity="0.45"/>
      <circle cx="${xs[xs.length - 1].toFixed(1)}" cy="${ys[ys.length - 1].toFixed(1)}" r="3" fill="${stroke}"/>
    </svg>
    <div class="sparkline-labels">
      <span>최저 ${formatMoney(min)}</span>
      <span>현재 ${formatMoney(last)}</span>
    </div>
  `;
}

async function loadHistoryCharts(trackings) {
  await Promise.allSettled((trackings || []).map(async tracking => {
    const container = document.getElementById(`sparkline-${tracking.id}`);
    if (!container) return;
    try {
      const history = await requestJson(`/api/trackings/${tracking.id}/history`);
      renderSparkline(container, history || []);
    } catch {
      const historySection = container.closest(".price-history");
      if (historySection) historySection.hidden = true;
    }
  }));
}

async function initDealsPage() {
  const grid = qs("#deals-grid");
  const status = qs("#deals-status");
  const subtitle = qs("#deals-subtitle");
  if (!grid) return;

  status.textContent = "최저가 정보를 불러오는 중입니다...";
  status.className = "status";

  try {
    const deals = await requestJson("/api/flights/deals?origin=ICN");
    status.textContent = "";

    if (!deals || !deals.length) {
      grid.innerHTML = `<div class="empty-state"><h3>현재 표시할 특가 항공권이 없습니다</h3><p class="muted">잠시 후 다시 확인해 주세요.</p></div>`;
      return;
    }

    const today = new Date();
    const endDate = new Date(today.getTime() + 6 * 86400000);
    if (subtitle) {
      subtitle.textContent = `${toDateInputValue(today)} ~ ${toDateInputValue(endDate)} 기간의 인천 출발 노선별 최저가입니다.`;
    }

    grid.innerHTML = deals.map(deal => {
      const destName = AIRPORT_DISPLAY_MAP[deal.destination] || deal.destination;
      const dateLabel = deal.departureDate ? formatDealDate(deal.departureDate) : "";
      const searchUrl = `/?origin=ICN&destination=${encodeURIComponent(deal.destination)}${deal.departureDate ? `&departureDate=${deal.departureDate}` : ""}&autoSearch=1`;
      return `
        <a class="deal-card" href="${escapeHtml(searchUrl)}">
          <div class="deal-destination">${escapeHtml(destName)}</div>
          <div class="deal-route">ICN → ${escapeHtml(deal.destination)}</div>
          <div class="deal-price">${formatMoney(deal.price)}</div>
          <div class="deal-meta">
            ${dateLabel ? `<span>${escapeHtml(dateLabel)}</span>` : ""}
            ${deal.airline ? `<span>${escapeHtml(deal.airline)}</span>` : ""}
            ${deal.approximate ? `<span class="approx-badge">예상 가격</span>` : ""}
          </div>
          <span class="deal-cta">검색하기 →</span>
        </a>
      `;
    }).join("");
  } catch (error) {
    status.textContent = error.message;
    status.className = "status error";
  }
}

function formatDealDate(dateStr) {
  if (!dateStr) return "";
  const date = new Date(dateStr + "T00:00:00");
  const DAY_NAMES = ["일", "월", "화", "수", "목", "금", "토"];
  return `${date.getMonth() + 1}월 ${date.getDate()}일 (${DAY_NAMES[date.getDay()]})`;
}

function initSearchPage() {
  const form = qs("#search-form");
  const results = qs("#results");
  const resultCount = qs("#result-count");
  const status = qs("#search-status");
  const modal = qs("#track-modal");
  const modalForm = qs("#track-form");
  const modalStatus = qs("#track-status");
  const selectedRoute = qs("#selected-route");
  const trackingLink = qs("#tracking-link");
  const departureDateInput = qs("#departure-date");
  const returnDateInput = qs("#return-date");
  const returnDateField = qs("#return-date-field");
  const searchWindowNote = qs("#search-window-note");
  const kakaoEnabledInput = qs("#kakao-enabled");
  const kakaoOptInInput = qs("#kakao-opt-in");
  const kakaoConnectGroup = qs("#kakao-connect-group");
  const kakaoConnectionState = qs("#kakao-connection-state");
  const kakaoConnectButton = qs("#kakao-connect-button");
  const kakaoDisconnectButton = qs("#kakao-disconnect-button");
  const tripTypeInputs = document.querySelectorAll('input[name="trip-type"]');
  let selectedFlight = null;
  let searchState = null;
  let kakaoConnection = getStoredKakaoConnection();

  const calendarSection = qs("#calendar-section");
  const calendarStrip = qs("#calendar-strip");

  populateSelect("#origin", AIRPORT_OPTIONS.origin);
  populateSelect("#destination", AIRPORT_OPTIONS.destination);
  applySearchDateWindow();
  applySearchParams();
  restoreSearchResultsCache();
  syncTripTypeFields();
  syncReturnDateConstraint();
  syncKakaoFields();
  loadNotificationExample("#kakao-example");
  loadExchangeRates();
  loadCalendar();

  if (new URLSearchParams(window.location.search).get("autoSearch") === "1") {
    setTimeout(() => form.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true })), 0);
  }

  qs("#origin").addEventListener("change", loadCalendar);
  qs("#destination").addEventListener("change", loadCalendar);

  async function loadCalendar() {
    const origin = qs("#origin").value;
    const destination = qs("#destination").value;
    if (!origin || !destination || !calendarSection || !calendarStrip) return;
    try {
      const days = await requestJson(`/api/flights/calendar?origin=${encodeURIComponent(origin)}&destination=${encodeURIComponent(destination)}`);
      const hasAnyPrice = days && days.some(d => d.price != null);
      if (!hasAnyPrice) {
        calendarSection.hidden = true;
        return;
      }
      renderCalendarStrip(calendarStrip, days, departureDateInput.value, (newDate) => {
        departureDateInput.value = newDate;
        syncReturnDateConstraint();
        const submitBtn = qs('#search-form button[type="submit"]');
        if (submitBtn) submitBtn.click();
      });
      calendarSection.hidden = false;
    } catch {
      calendarSection.hidden = true;
    }
  }

  tripTypeInputs.forEach((input) => {
    input.addEventListener("change", () => {
      syncTripTypeFields();
      syncReturnDateConstraint();
    });
  });

  departureDateInput.addEventListener("change", () => {
    syncReturnDateConstraint();
  });

  kakaoEnabledInput.addEventListener("change", () => {
    syncKakaoFields();
  });

  window.addEventListener("message", async (event) => {
    if (event.origin !== window.location.origin) {
      return;
    }
    if (!event.data || event.data.type !== "kakao-auth-success") {
      return;
    }

    kakaoConnection = {
      connectionId: event.data.connectionId,
      nickname: event.data.nickname || "카카오 사용자",
      kakaoUserId: event.data.kakaoUserId
    };
    storeKakaoConnection(kakaoConnection);
    syncKakaoFields();
    modalStatus.textContent = "카카오 연결이 완료되었습니다.";
    modalStatus.className = "status success";
  });

  kakaoConnectButton.addEventListener("click", async () => {
    kakaoConnectButton.disabled = true;
    modalStatus.textContent = "카카오 로그인 창을 여는 중입니다...";
    modalStatus.className = "status";

    try {
      const auth = await requestJson("/api/notifications/kakao/auth/start");
      const popup = window.open(auth.authorizationUrl, "kakao-auth", "width=520,height=720");
      if (!popup) {
        throw new Error("팝업이 차단되었습니다. 브라우저에서 팝업을 허용해 주세요.");
      }
    } catch (error) {
      modalStatus.textContent = error.message;
      modalStatus.className = "status error";
    } finally {
      kakaoConnectButton.disabled = false;
    }
  });

  kakaoDisconnectButton.addEventListener("click", () => {
    clearKakaoConnection();
    kakaoConnection = null;
    syncKakaoFields();
    modalStatus.textContent = "카카오 연결이 해제되었습니다.";
    modalStatus.className = "status";
  });

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    status.textContent = "최저가를 검색하는 중입니다...";
    status.className = "status";
    results.innerHTML = "";
    resultCount.textContent = "";

    const tripType = getSelectedTripType();
    if (tripType === "ROUND_TRIP" && !returnDateInput.value) {
      status.textContent = "왕복 검색은 도착일을 선택해야 합니다.";
      status.className = "status error";
      return;
    }

    const payload = {
      tripType,
      origin: qs("#origin").value,
      destination: qs("#destination").value,
      departureDate: departureDateInput.value,
      returnDate: tripType === "ROUND_TRIP" ? returnDateInput.value : null,
      adults: Number(qs("#adults").value || 1)
    };

    searchState = payload;

    try {
      const flights = await requestJson("/api/flights/search", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
      });

      if (!flights.length) {
        status.textContent = "해당 노선의 가격을 찾지 못했습니다.";
        return;
      }

      status.textContent = "";
      resultCount.textContent = `${flights.length}개의 항공편을 찾았습니다`;
      loadCalendar();
      trackEvent('flight_search', {
        origin: payload.origin,
        destination: payload.destination,
        trip_type: payload.tripType,
        result_count: flights.length
      });
      renderResults(results, flights, (flight) => {
        selectedFlight = flight;
        selectedRoute.textContent = formatRoute(flight.origin, flight.destination);
        qs("#target-price").value = flight.price != null ? Math.round(Number(flight.price)) : "";
        modalStatus.textContent = "";
        modalStatus.className = "status";
        trackingLink.hidden = true;
        modal.classList.add("open");
      });
      sessionStorage.setItem(SEARCH_CACHE_STORAGE_KEY, JSON.stringify({ searchState: payload, flights }));
    } catch (error) {
      status.textContent = error.message;
      status.className = "status error";
    }
  });

  qs("#close-modal").addEventListener("click", () => {
    modal.classList.remove("open");
  });

  modal.addEventListener("click", (event) => {
    if (event.target === modal) {
      modal.classList.remove("open");
    }
  });

  modalForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!selectedFlight || !searchState) {
      return;
    }

    modalStatus.textContent = "백그라운드 추적을 시작하는 중입니다...";
    modalStatus.className = "status";

    const rawTargetPrice = qs("#target-price").value.trim();
    const kakaoEnabled = kakaoEnabledInput.checked;
    const payload = {
      tripType: searchState.tripType,
      origin: searchState.origin,
      destination: searchState.destination,
      departureDate: searchState.departureDate,
      returnDate: searchState.returnDate,
      adults: searchState.adults,
      targetPrice: rawTargetPrice ? Number(rawTargetPrice) : null,
      kakaoNotificationEnabled: kakaoEnabled,
      kakaoOptIn: kakaoEnabled && kakaoOptInInput.checked,
      kakaoConnectionId: kakaoEnabled && kakaoConnection ? kakaoConnection.connectionId : null,
      ownerToken: getTrackingOwnerToken()
    };

    try {
      const tracking = await requestJson("/api/trackings", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
      });

      modalStatus.textContent = "추적이 저장되었습니다.";
      modalStatus.className = "status success";
      trackEvent('tracking_start', {
        origin: payload.origin,
        destination: payload.destination,
        trip_type: payload.tripType,
        kakao_enabled: payload.kakaoNotificationEnabled
      });
      const trackingLinkParams = new URLSearchParams({ id: String(tracking.id) });
      if (payload.kakaoConnectionId) {
        trackingLinkParams.set("connection", payload.kakaoConnectionId);
      }
      trackingLink.href = `/tracking.html?${trackingLinkParams.toString()}`;
      trackingLink.hidden = false;
      modalForm.reset();
      syncKakaoFields();
    } catch (error) {
      modalStatus.textContent = error.message;
      modalStatus.className = "status error";
    }
  });

  function getSelectedTripType() {
    const selected = document.querySelector('input[name="trip-type"]:checked');
    return selected ? selected.value : "ONE_WAY";
  }

  function syncTripTypeFields() {
    const isRoundTrip = getSelectedTripType() === "ROUND_TRIP";
    returnDateField.hidden = !isRoundTrip;
    returnDateInput.required = isRoundTrip;
    if (!isRoundTrip) {
      returnDateInput.value = "";
    }
  }

  function syncKakaoFields() {
    const enabled = kakaoEnabledInput.checked;
    kakaoConnectGroup.hidden = !enabled;
    kakaoOptInInput.disabled = !enabled;
    kakaoOptInInput.required = enabled;
    const isConnected = Boolean(kakaoConnection && kakaoConnection.connectionId);
    if (kakaoConnectionState) {
      kakaoConnectionState.textContent = enabled
        ? isConnected
          ? `연결됨: ${kakaoConnection.nickname || "카카오 사용자"}`
          : "카카오 로그인이 아직 연결되지 않았습니다."
        : "카카오 알림을 끄면 로그인 연결 없이 추적만 저장합니다.";
    }
    kakaoConnectButton.hidden = !enabled || isConnected;
    kakaoDisconnectButton.hidden = !enabled || !isConnected;
    if (!enabled) {
      kakaoOptInInput.checked = false;
    }
  }

  function syncReturnDateConstraint() {
    if (!departureDateInput.value) {
      returnDateInput.min = "";
      returnDateInput.max = "";
      return;
    }
    returnDateInput.min = departureDateInput.value;
    returnDateInput.max = departureDateInput.max;
    if (returnDateInput.value && returnDateInput.value < departureDateInput.value) {
      returnDateInput.value = departureDateInput.value;
    }
  }

  function restoreSearchResultsCache() {
    const raw = sessionStorage.getItem(SEARCH_CACHE_STORAGE_KEY);
    if (!raw) return;
    let cached;
    try {
      cached = JSON.parse(raw);
    } catch {
      sessionStorage.removeItem(SEARCH_CACHE_STORAGE_KEY);
      return;
    }
    if (!cached || !Array.isArray(cached.flights) || !cached.flights.length || !cached.searchState) return;

    const s = cached.searchState;
    if (!s.departureDate || s.departureDate < departureDateInput.min || s.departureDate > departureDateInput.max) {
      return;
    }

    searchState = s;
    qs("#origin").value = s.origin || "";
    qs("#destination").value = s.destination || "";
    qs("#adults").value = String(s.adults || 1);
    departureDateInput.value = s.departureDate;
    if (s.tripType === "ROUND_TRIP") {
      const rtInput = document.querySelector('input[name="trip-type"][value="ROUND_TRIP"]');
      if (rtInput) rtInput.checked = true;
    }
    if (s.returnDate) returnDateInput.value = s.returnDate;

    resultCount.textContent = `${cached.flights.length}개의 항공편을 찾았습니다`;
    renderResults(results, cached.flights, (flight) => {
      selectedFlight = flight;
      selectedRoute.textContent = formatRoute(flight.origin, flight.destination);
      qs("#target-price").value = flight.price != null ? Math.round(Number(flight.price)) : "";
      modalStatus.textContent = "";
      modalStatus.className = "status";
      trackingLink.hidden = true;
      modal.classList.add("open");
    });
  }

  function applySearchDateWindow() {
    const today = new Date();
    const maxDate = new Date(today);
    maxDate.setDate(today.getDate() + SEARCH_WINDOW_DAYS - 1);

    const minValue = toDateInputValue(today);
    const maxValue = toDateInputValue(maxDate);
    departureDateInput.min = minValue;
    departureDateInput.max = maxValue;
    returnDateInput.min = minValue;
    returnDateInput.max = maxValue;

    if (!departureDateInput.value || departureDateInput.value < minValue || departureDateInput.value > maxValue) {
      departureDateInput.value = minValue;
    }

    if (searchWindowNote) {
      searchWindowNote.textContent = `검색 가능 기간: ${minValue} ~ ${maxValue}`;
    }
  }

  function applySearchParams() {
    const params = new URLSearchParams(window.location.search);
    const origin = (params.get("origin") || "").toUpperCase();
    const destination = (params.get("destination") || "").toUpperCase();
    const tripType = params.get("tripType");
    const departureDate = params.get("departureDate");
    const returnDate = params.get("returnDate");

    if (origin || destination) {
      sessionStorage.removeItem(SEARCH_CACHE_STORAGE_KEY);
    }

    if (origin) {
      qs("#origin").value = origin;
    }
    if (destination) {
      qs("#destination").value = destination;
    }
    if (tripType === "ROUND_TRIP") {
      const input = document.querySelector('input[name="trip-type"][value="ROUND_TRIP"]');
      if (input) {
        input.checked = true;
      }
    }
    if (departureDate && departureDate >= departureDateInput.min && departureDate <= departureDateInput.max) {
      departureDateInput.value = departureDate;
    }
    if (returnDate) {
      returnDateInput.value = returnDate;
    }
  }
}

function toDateInputValue(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function renderResults(target, flights, onTrack) {
  const INITIAL_SHOW = 5;
  target.innerHTML = "";

  function buildCard(flight, absoluteIndex) {
    const card = document.createElement("article");
    card.className = `result-card${absoluteIndex === 0 ? " cheapest" : ""}`;
    card.innerHTML = `
      <div class="result-head">
        <div class="meta">
          <div class="route">${formatRoute(flight.origin, flight.destination)} ${renderApproximateBadge(flight)}</div>
          <div>여정: ${formatTripType(flight.tripType)}</div>
          <div>항공사: ${flight.airline || "확인 불가"}</div>
          ${flight.tripType === "ROUND_TRIP" ? `<div>귀국 항공사: ${flight.inboundAirline || "확인 불가"}</div>` : ""}
        </div>
        <div class="price">${formatMoney(flight.price)}</div>
      </div>
      <div class="meta">
        ${flight.timeBucket ? `
        <div class="meta-line">
          <span>출발일</span>
          <span>${formatDateOnly(flight.departureDate)}</span>
        </div>
        <div class="meta-line">
          <span>출발 시간대</span>
          <span>${formatTimeBucket(flight.timeBucket)}</span>
        </div>
        ${flight.tripType === "ROUND_TRIP" && flight.returnDate ? `
        <div class="meta-line">
          <span>귀국일</span>
          <span>${formatDateOnly(flight.returnDate)}</span>
        </div>
        <div class="meta-line">
          <span>귀국 시간대</span>
          <span>${formatTimeBucket(flight.timeBucket)}</span>
        </div>` : ""}
        ` : `
        <div class="meta-line">
          <span>출발</span>
          <span>${formatDateTime(flight.departureTime)}</span>
        </div>
        <div class="meta-line">
          <span>도착</span>
          <span>${formatDateTime(flight.arrivalTime)}</span>
        </div>
        ${flight.tripType === "ROUND_TRIP" ? `
        <div class="meta-line">
          <span>귀국 출발</span>
          <span>${formatDateTime(flight.returnDepartureTime)}</span>
        </div>
        <div class="meta-line">
          <span>귀국 도착</span>
          <span>${formatDateTime(flight.returnArrivalTime)}</span>
        </div>` : ""}
        `}
      </div>
    `;

    const actionRow = document.createElement("div");
    actionRow.className = "button-row";

    const button = document.createElement("button");
    button.type = "button";
    button.className = "button";
    button.textContent = "가격 추적";
    button.addEventListener("click", () => onTrack(flight));

    actionRow.appendChild(button);
    card.appendChild(actionRow);
    return card;
  }

  flights.slice(0, INITIAL_SHOW).forEach((flight, i) => {
    target.appendChild(buildCard(flight, i));
  });

  if (flights.length > INITIAL_SHOW) {
    const remaining = flights.length - INITIAL_SHOW;
    const showMoreWrap = document.createElement("div");
    showMoreWrap.className = "show-more-wrap";

    const showMoreBtn = document.createElement("button");
    showMoreBtn.type = "button";
    showMoreBtn.className = "show-more-btn";
    showMoreBtn.textContent = `결과 더 보기 (${remaining}개)`;
    showMoreBtn.addEventListener("click", () => {
      showMoreWrap.remove();
      flights.slice(INITIAL_SHOW).forEach((flight, i) => {
        target.appendChild(buildCard(flight, INITIAL_SHOW + i));
      });
    });

    showMoreWrap.appendChild(showMoreBtn);
    target.appendChild(showMoreWrap);
  }
}

function initTrackingPage() {
  const list = qs("#tracking-list");
  const status = qs("#tracking-status");
  const focusTitle = qs("#tracking-focus");
  const originFilter = qs("#tracking-origin-filter");
  const destinationFilter = qs("#tracking-destination-filter");
  const params = new URLSearchParams(window.location.search);
  const focusId = params.get("id");
  let allTrackings = [];

  populateSelectableFilter("#tracking-origin-filter", AIRPORT_OPTIONS.origin, "전체 출발지");
  populateSelectableFilter("#tracking-destination-filter", AIRPORT_OPTIONS.destination, "전체 도착지");
  loadNotificationExample("#kakao-example");
  loadTrackings();

  originFilter.addEventListener("change", () => renderFilteredTrackings());
  destinationFilter.addEventListener("change", () => renderFilteredTrackings());

  async function loadTrackings() {
    status.textContent = "추적 목록을 불러오는 중입니다...";
    status.className = "status";

    try {
      const trackings = await requestJson("/api/trackings");
      allTrackings = trackings;
      status.textContent = "";

      if (!trackings.length) {
        list.innerHTML = `<div class="empty-state"><h3>아직 추적 중인 노선이 없습니다</h3><p class="muted">노선을 검색한 뒤 가격 추적을 누르고 카카오 로그인을 연결해 보세요.</p></div>`;
        return;
      }

      if (focusId) {
        const focused = trackings.find((item) => String(item.id) === focusId);
        focusTitle.textContent = focused
          ? `알림에서 돌아온 노선: ${formatRoute(focused.origin, focused.destination)}`
          : "알림에서 돌아왔습니다";
      }

      renderFilteredTrackings();
    } catch (error) {
      status.textContent = error.message;
      status.className = "status error";
    }
  }

  function renderFilteredTrackings() {
    if (!allTrackings.length) {
      list.innerHTML = `<div class="empty-state"><h3>아직 추적 중인 노선이 없습니다</h3><p class="muted">노선을 검색한 뒤 가격 추적을 누르고 카카오 로그인을 연결해 보세요.</p></div>`;
      return;
    }

    const origin = originFilter.value;
    const destination = destinationFilter.value;
    const filteredTrackings = allTrackings.filter((tracking) => {
      const matchesOrigin = !origin || tracking.origin === origin;
      const matchesDestination = !destination || tracking.destination === destination;
      return matchesOrigin && matchesDestination;
    });

    if (!filteredTrackings.length) {
      list.innerHTML = `<div class="empty-state"><h3>조건에 맞는 추적이 없습니다</h3><p class="muted">필터를 바꾸거나 검색 화면에서 새 추적을 추가해 보세요.</p></div>`;
      return;
    }

    renderTrackings(list, filteredTrackings, async (id) => {
      await requestJson(`/api/trackings/${id}`, { method: "DELETE" });
      await loadTrackings();
    });
    loadHistoryCharts(filteredTrackings);
  }
}

function renderTrackings(target, trackings, onRemove) {
  target.innerHTML = "";

  trackings.forEach((tracking) => {
    const card = document.createElement("article");
    card.className = "tracking-card";
    card.innerHTML = `
      <div class="tracking-head">
        <div>
          <div class="route">${formatRoute(tracking.origin, tracking.destination)}</div>
          <div class="muted">추적 #${tracking.id}</div>
        </div>
        <div class="price">${formatMoney(tracking.lastCheckedPrice)}</div>
      </div>
      <div class="tracking-meta">
        <div class="tracking-line">
          <span>여정 유형</span>
          <span>${formatTripType(tracking.tripType)}</span>
        </div>
        <div class="tracking-line">
          <span>출발일</span>
          <span>${tracking.departureDate || "미설정"}</span>
        </div>
        <div class="tracking-line">
          <span>도착일</span>
          <span>${tracking.returnDate || "해당 없음"}</span>
        </div>
        <div class="tracking-line">
          <span>현재 가격</span>
          <span>${formatMoney(tracking.lastCheckedPrice)}</span>
        </div>
        <div class="tracking-line">
          <span>현재 최저가 항공사</span>
          <span>${formatFlightSummary(tracking)}</span>
        </div>
        <div class="tracking-line">
          <span>현재 최저가 출발</span>
          <span>${formatDateTime(tracking.lastDepartureTime)}</span>
        </div>
        <div class="tracking-line">
          <span>현재 최저가 도착</span>
          <span>${formatDateTime(tracking.lastArrivalTime)}</span>
        </div>
        ${tracking.tripType === "ROUND_TRIP" ? `
        <div class="tracking-line">
          <span>현재 최저가 귀국 출발</span>
          <span>${formatDateTime(tracking.lastReturnDepartureTime)}</span>
        </div>
        <div class="tracking-line">
          <span>현재 최저가 귀국 도착</span>
          <span>${formatDateTime(tracking.lastReturnArrivalTime)}</span>
        </div>` : ""}
        <div class="tracking-line">
          <span>카카오 연결</span>
          <span>${tracking.kakaoNickname || (tracking.kakaoLinked ? "연결됨" : "미연결")}</span>
        </div>
        <div class="tracking-line">
          <span>목표 가격</span>
          <span>${formatMoney(tracking.targetPrice)}</span>
        </div>
        <div class="tracking-line">
          <span>마지막 업데이트</span>
          <span>${formatDateTime(tracking.lastUpdatedAt)}</span>
        </div>
        <div class="tracking-line">
          <span>최저가 이동</span>
          <span>스카이스캐너</span>
        </div>
      </div>
      <div class="price-history">
        <div class="price-history-header">
          <span>가격 추이</span>
        </div>
        <div id="sparkline-${tracking.id}" class="sparkline-container">
          <span class="sparkline-empty">로딩 중...</span>
        </div>
      </div>
    `;

    const actionRow = document.createElement("div");
    actionRow.className = "button-row";

    const dealButton = document.createElement("button");
    dealButton.type = "button";
    dealButton.className = "button";
    dealButton.textContent = "스카이스캐너로 이동";
    dealButton.addEventListener("click", () => {
      trackEvent('skyscanner_click', {
        origin: tracking.origin,
        destination: tracking.destination,
        trip_type: tracking.tripType
      });
      window.location.href = buildSkyscannerTrackingUrl(tracking);
    });
    actionRow.appendChild(dealButton);

    const removeButton = document.createElement("button");
    removeButton.type = "button";
    removeButton.className = "danger-button";
    removeButton.textContent = "삭제";
    removeButton.addEventListener("click", () => {
      if (confirm(`${formatRoute(tracking.origin, tracking.destination)} 추적을 삭제하시겠습니까?\n삭제하면 가격 이력도 함께 제거됩니다.`)) {
        onRemove(tracking.id);
      }
    });

    actionRow.appendChild(removeButton);
    card.appendChild(actionRow);

    const note = document.createElement("p");
    note.className = "inline-note";
    note.textContent = "스카이스캐너 가격과 순서는 실시간으로 변동될 수 있습니다.";
    card.appendChild(note);

    target.appendChild(card);
  });
}

const FX_CURRENCIES = [
  { code: "USD", label: "미국 달러", flag: "🇺🇸", unit: 1 },
  { code: "JPY", label: "일본 엔", flag: "🇯🇵", unit: 100 },
  { code: "EUR", label: "유로", flag: "🇪🇺", unit: 1 },
  { code: "THB", label: "태국 바트", flag: "🇹🇭", unit: 1 },
  { code: "CNY", label: "중국 위안", flag: "🇨🇳", unit: 1 },
];

// ============================================================
// Restaurants Page
// ============================================================

let _restaurantMap = null;
let _selectedCityMarker = null;

async function initRestaurantsPage() {
  if (typeof L === "undefined") {
    qs("#restaurants-status").textContent = "지도 라이브러리를 불러오지 못했습니다.";
    return;
  }

  _restaurantMap = L.map("restaurants-map", {
    center: [25, 120],
    zoom: 3,
  });

  L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
    maxZoom: 18,
  }).addTo(_restaurantMap);

  try {
    const cities = await requestJson("/api/restaurants/cities");
    cities.forEach((city) => {
      const marker = L.circleMarker([city.lat, city.lng], {
        radius: 8,
        fillColor: "#d46a3a",
        color: "#ffffff",
        weight: 2,
        opacity: 1,
        fillOpacity: 0.85,
      }).addTo(_restaurantMap);

      marker.bindTooltip(city.name, {
        permanent: true,
        direction: "top",
        offset: [0, -8],
        className: "restaurant-city-tooltip",
      });

      marker.on("click", () => {
        if (_selectedCityMarker) {
          _selectedCityMarker.setStyle({ fillColor: "#d46a3a", radius: 8 });
        }
        marker.setStyle({ fillColor: "#af4b1f", radius: 11 });
        _selectedCityMarker = marker;
        loadRestaurants(city.code, city.name);
      });
    });
  } catch (_) {
    qs("#restaurants-status").textContent = "도시 정보를 불러오지 못했습니다.";
  }
}

async function loadRestaurants(cityCode, cityName) {
  const header = qs("#restaurants-panel-header");
  const status = qs("#restaurants-status");
  const list = qs("#restaurants-list");

  header.innerHTML = `
    <h2>${cityName} 맛집</h2>
    <p class="muted">Google 평점 기준 인기 맛집</p>
  `;
  list.innerHTML = "";
  status.innerHTML = `<span class="muted" style="font-size:13px">불러오는 중…</span>`;

  try {
    const restaurants = await requestJson(`/api/restaurants?city=${encodeURIComponent(cityCode)}`);
    status.innerHTML = "";

    if (!restaurants || restaurants.length === 0) {
      list.innerHTML = `<p class="muted" style="font-size:13px;padding:4px 0">맛집 정보가 없습니다.</p>`;
      return;
    }

    list.innerHTML = restaurants.map(buildRestaurantCard).join("");
  } catch (_) {
    status.innerHTML = "";
    list.innerHTML = `<p class="muted" style="font-size:13px;padding:4px 0">맛집 정보를 불러오지 못했습니다.</p>`;
  }
}

function buildRestaurantCard(r) {
  const ratingNum = r.rating ? r.rating.toFixed(1) : null;
  const filledStars = r.rating ? Math.round(r.rating) : 0;
  const stars = ratingNum
    ? `<span class="restaurant-rating-stars">${"★".repeat(filledStars)}${"☆".repeat(5 - filledStars)}</span>`
    : "";
  const countText = r.ratingCount
    ? `<span class="restaurant-rating-count">(${r.ratingCount.toLocaleString("ko-KR")})</span>`
    : "";
  const category = r.category ? `<div class="restaurant-category">${r.category}</div>` : "";
  const address = r.address ? `<div class="restaurant-address">${r.address}</div>` : "";

  return `
    <div class="restaurant-card">
      <div class="restaurant-name">${r.name}</div>
      ${ratingNum ? `<div class="restaurant-rating"><span class="restaurant-rating-score">${ratingNum}</span>${stars}${countText}</div>` : ""}
      ${category}
      ${address}
    </div>`;
}

async function loadExchangeRates() {
  const listEl = qs("#fx-list");
  const updatedEl = qs("#fx-updated");
  if (!listEl) return;

  listEl.innerHTML = FX_CURRENCIES.map(() => `<div class="fx-skeleton"></div>`).join("");

  try {
    const data = await requestJson("/api/exchange-rates");

    listEl.innerHTML = FX_CURRENCIES.map(({ code, label, flag, unit }) => {
      const krwPerUnit = data.rates && data.rates[code];
      if (!krwPerUnit) return "";
      const unitLabel = unit === 100 ? "100" : "1";
      const formatted = Number(krwPerUnit).toLocaleString("ko-KR") + "원";
      return `
        <div class="fx-row">
          <span class="fx-label">
            <span class="fx-flag">${flag}</span>
            <span>${unitLabel} ${code} <span class="fx-currency">${label}</span></span>
          </span>
          <span class="fx-value">${formatted}</span>
        </div>`;
    }).join("");

    if (updatedEl && data.date) {
      updatedEl.textContent = `${data.date} 기준`;
    }
  } catch (_) {
    listEl.innerHTML = `<p class="muted" style="font-size:13px;padding:4px 2px">환율 정보를 불러오지 못했습니다.</p>`;
  }
}
