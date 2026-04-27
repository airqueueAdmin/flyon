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
  const response = await fetch(url, options);
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
    { code: "ICN", label: "인천국제공항 (ICN)" }
  ],
  destination: [
    { code: "NRT", label: "도쿄 나리타 (NRT)" },
    { code: "FUK", label: "후쿠오카 (FUK)" },
    { code: "KIX", label: "오사카 간사이 (KIX)" },
    { code: "TYO", label: "도쿄 지역 (TYO)" },
    { code: "CTS", label: "삿포로 치토세 (CTS)" },
    { code: "OKA", label: "오키나와 나하 (OKA)" },
    { code: "BKK", label: "방콕 (BKK)" },
    { code: "HKG", label: "홍콩 (HKG)" },
    { code: "TPE", label: "타이베이 (TPE)" },
    { code: "SIN", label: "싱가포르 (SIN)" },
    { code: "DAD", label: "다낭 (DAD)" },
    { code: "SGN", label: "호치민 (SGN)" },
    { code: "MNL", label: "마닐라 (MNL)" },
    { code: "CEB", label: "세부 (CEB)" },
    { code: "SFO", label: "샌프란시스코 (SFO)" },
    { code: "LAX", label: "로스앤젤레스 (LAX)" },
    { code: "JFK", label: "뉴욕 JFK (JFK)" },
    { code: "SEA", label: "시애틀 (SEA)" },
    { code: "YVR", label: "밴쿠버 (YVR)" },
    { code: "GUM", label: "괌 (GUM)" },
    { code: "SYD", label: "시드니 (SYD)" },
    { code: "MEL", label: "멜버른 (MEL)" },
    { code: "CDG", label: "파리 샤를드골 (CDG)" },
    { code: "LHR", label: "런던 히드로 (LHR)" }
  ]
};

const SEARCH_WINDOW_DAYS = 7;

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
  target.textContent = JSON.stringify(payload, null, 2);
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
    target.textContent = "카카오 예시 메시지를 지금 불러올 수 없습니다.";
  }
}

document.addEventListener("DOMContentLoaded", () => {
  if (document.body.dataset.page === "search") {
    initSearchPage();
  }
  if (document.body.dataset.page === "tracking") {
    initTrackingPage();
  }
});

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
  const tripTypeInputs = document.querySelectorAll('input[name="trip-type"]');
  let selectedFlight = null;
  let searchState = null;

  populateSelect("#origin", AIRPORT_OPTIONS.origin);
  populateSelect("#destination", AIRPORT_OPTIONS.destination);
  applySearchDateWindow();
  syncTripTypeFields();
  syncReturnDateConstraint();
  loadNotificationExample("#kakao-example");

  tripTypeInputs.forEach((input) => {
    input.addEventListener("change", () => {
      syncTripTypeFields();
      syncReturnDateConstraint();
    });
  });

  departureDateInput.addEventListener("change", () => {
    syncReturnDateConstraint();
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
      renderResults(results, flights, (flight) => {
        selectedFlight = flight;
        selectedRoute.textContent = `${flight.origin} -> ${flight.destination}`;
        modalStatus.textContent = "";
        modalStatus.className = "status";
        trackingLink.hidden = true;
        modal.classList.add("open");
      });
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
    const kakaoEnabled = qs("#kakao-enabled").checked;
    const payload = {
      tripType: searchState.tripType,
      origin: searchState.origin,
      destination: searchState.destination,
      departureDate: searchState.departureDate,
      returnDate: searchState.returnDate,
      adults: searchState.adults,
      targetPrice: rawTargetPrice ? Number(rawTargetPrice) : null,
      kakaoNotificationEnabled: kakaoEnabled,
      kakaoOptIn: kakaoEnabled && qs("#kakao-opt-in").checked,
      phoneNumber: qs("#phone-number").value.trim()
    };

    try {
      const tracking = await requestJson("/api/trackings", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
      });

      modalStatus.textContent = "추적이 저장되었습니다.";
      modalStatus.className = "status success";
      trackingLink.href = `/tracking.html?id=${tracking.id}`;
      trackingLink.hidden = false;
      modalForm.reset();
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
}

function toDateInputValue(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function renderResults(target, flights, onTrack) {
  target.innerHTML = "";

  flights.forEach((flight, index) => {
    const card = document.createElement("article");
    card.className = `result-card${index === 0 ? " cheapest" : ""}`;
    card.innerHTML = `
      <div class="result-head">
        <div class="meta">
          <div class="route">${flight.origin} -> ${flight.destination} ${renderApproximateBadge(flight)}</div>
          <div>여정: ${formatTripType(flight.tripType)}</div>
          <div>항공사: ${flight.airline || "확인 불가"}</div>
          ${flight.tripType === "ROUND_TRIP" ? `<div>귀국 항공사: ${flight.inboundAirline || "확인 불가"}</div>` : ""}
        </div>
        <div class="price">${formatMoney(flight.price)}</div>
      </div>
      <div class="meta">
        <div class="meta-line">
          <span>출발</span>
          <span>${formatDateTime(flight.departureTime)}</span>
        </div>
        <div class="meta-line">
          <span>도착</span>
          <span>${formatDateTime(flight.arrivalTime)}</span>
        </div>
        <div class="meta-line">
          <span>시간대</span>
          <span>${formatTimeBucket(flight.timeBucket)}</span>
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
    target.appendChild(card);
  });
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
        list.innerHTML = `<div class="empty-state"><h3>아직 추적 중인 노선이 없습니다</h3><p class="muted">노선을 검색한 뒤 가격 추적을 누르고 카카오 알림을 설정해 보세요.</p></div>`;
        return;
      }

      if (focusId) {
        const focused = trackings.find((item) => String(item.id) === focusId);
        focusTitle.textContent = focused
          ? `알림에서 돌아온 노선: ${focused.origin} -> ${focused.destination}`
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
      list.innerHTML = `<div class="empty-state"><h3>아직 추적 중인 노선이 없습니다</h3><p class="muted">노선을 검색한 뒤 가격 추적을 누르고 카카오 알림을 설정해 보세요.</p></div>`;
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
          <div class="route">${tracking.origin} -> ${tracking.destination}</div>
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
          <span>목표 가격</span>
          <span>${formatMoney(tracking.targetPrice)}</span>
        </div>
        <div class="tracking-line">
          <span>마지막 업데이트</span>
          <span>${formatDateTime(tracking.lastUpdatedAt)}</span>
        </div>
        <div class="tracking-line">
          <span>카카오 알림</span>
          <span>${tracking.kakaoNotificationEnabled ? "사용" : "끔"}</span>
        </div>
        <div class="tracking-line">
          <span>알림톡 수신 동의</span>
          <span>${tracking.kakaoOptIn ? "동의" : "미동의"}</span>
        </div>
        <div class="tracking-line">
          <span>수신 번호</span>
          <span>${tracking.phoneNumber || "미설정"}</span>
        </div>
        <div class="tracking-line">
          <span>최저가 이동</span>
          <span>스카이스캐너</span>
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
      window.location.href = buildSkyscannerTrackingUrl(tracking);
    });
    actionRow.appendChild(dealButton);

    const removeButton = document.createElement("button");
    removeButton.type = "button";
    removeButton.className = "ghost-button";
    removeButton.textContent = "삭제";
    removeButton.addEventListener("click", () => onRemove(tracking.id));

    actionRow.appendChild(removeButton);
    card.appendChild(actionRow);
    target.appendChild(card);
  });
}
