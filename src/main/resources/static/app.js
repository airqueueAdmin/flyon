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
  let selectedFlight = null;
  let searchState = null;

  loadNotificationExample("#kakao-example");

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    status.textContent = "최저가를 검색하는 중입니다...";
    status.className = "status";
    results.innerHTML = "";
    resultCount.textContent = "";

    const payload = {
      origin: qs("#origin").value.trim(),
      destination: qs("#destination").value.trim(),
      departureDate: qs("#departure-date").value
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
      origin: searchState.origin,
      destination: searchState.destination,
      departureDate: searchState.departureDate,
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

      modalStatus.textContent = "추적이 저장되었습니다. 카카오 알림에서 이 앱으로 돌아올 수 있습니다.";
      modalStatus.className = "status success";
      trackingLink.href = `/tracking.html?id=${tracking.id}`;
      trackingLink.hidden = false;
      modalForm.reset();
    } catch (error) {
      modalStatus.textContent = error.message;
      modalStatus.className = "status error";
    }
  });
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
          <div>항공사: ${flight.airline || "확인 불가"}</div>
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
          <span>${flight.timeBucket || "일반"}</span>
        </div>
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
  const params = new URLSearchParams(window.location.search);
  const focusId = params.get("id");

  loadNotificationExample("#kakao-example");
  loadTrackings();

  async function loadTrackings() {
    status.textContent = "추적 목록을 불러오는 중입니다...";
    status.className = "status";

    try {
      const trackings = await requestJson("/api/trackings");
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

      renderTrackings(list, trackings, async (id) => {
        await requestJson(`/api/trackings/${id}`, { method: "DELETE" });
        await loadTrackings();
      });
    } catch (error) {
      status.textContent = error.message;
      status.className = "status error";
    }
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
      </div>
    `;

    const actionRow = document.createElement("div");
    actionRow.className = "button-row";

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
