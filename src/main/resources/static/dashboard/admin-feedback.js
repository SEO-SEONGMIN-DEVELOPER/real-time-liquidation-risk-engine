(() => {
  "use strict";

  const BACKEND_BASE_URL = `${location.protocol}//${location.hostname}:8080`;
  const state = {
    page: 0,
    size: 20,
    totalPages: 1
  };

  const el = {
    refreshBtn: document.getElementById("refresh-btn"),
    prevBtn: document.getElementById("prev-btn"),
    nextBtn: document.getElementById("next-btn"),
    summaryText: document.getElementById("summary-text"),
    pageText: document.getElementById("page-text"),
    tbody: document.getElementById("feedback-body")
  };

  function escapeHtml(value) {
    return String(value ?? "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#39;");
  }

  function formatDate(epochMs) {
    if (!epochMs) return "-";
    return new Date(Number(epochMs)).toLocaleString("ko-KR", { hour12: false });
  }

  async function fetchFeedback(page, size) {
    const query = new URLSearchParams({ page: String(page), size: String(size) });
    const response = await fetch(`${BACKEND_BASE_URL}/api/feedback/admin/list?${query.toString()}`);
    const data = await response.json();
    if (!response.ok || !data.success) {
      throw new Error(data.message || "피드백 조회에 실패했습니다.");
    }
    return data;
  }

  function renderRows(items) {
    if (!Array.isArray(items) || items.length === 0) {
      el.tbody.innerHTML = '<tr><td colspan="6" class="empty">데이터가 없습니다.</td></tr>';
      return;
    }

    el.tbody.innerHTML = items.map((row) => `
      <tr>
        <td>${escapeHtml(row.id)}</td>
        <td>${escapeHtml(formatDate(row.createdAtEpochMs))}</td>
        <td>${escapeHtml(row.name || "anonymous")}</td>
        <td>${escapeHtml(row.lang || "-")}</td>
        <td>${escapeHtml(row.symbol || "-")}</td>
        <td class="msg">${escapeHtml(row.message || "")}</td>
      </tr>
    `).join("");
  }

  function renderPager() {
    const current = state.page + 1;
    const total = Math.max(1, state.totalPages);
    el.pageText.textContent = `${current} / ${total}`;
    el.prevBtn.disabled = state.page <= 0;
    el.nextBtn.disabled = state.page >= total - 1;
  }

  async function load() {
    el.refreshBtn.disabled = true;
    el.summaryText.textContent = "로딩 중...";
    try {
      const data = await fetchFeedback(state.page, state.size);
      state.totalPages = Math.max(1, Number(data.totalPages || 1));
      renderRows(data.items || []);
      renderPager();
      el.summaryText.textContent = `총 ${data.totalElements ?? 0}건, 페이지 크기 ${data.size ?? state.size}`;
    } catch (error) {
      el.tbody.innerHTML = `<tr><td colspan="6" class="empty">${escapeHtml(error.message || "오류가 발생했습니다.")}</td></tr>`;
      el.summaryText.textContent = "조회 실패";
      renderPager();
    } finally {
      el.refreshBtn.disabled = false;
    }
  }

  function bindEvents() {
    el.refreshBtn.addEventListener("click", () => load());
    el.prevBtn.addEventListener("click", () => {
      if (state.page <= 0) return;
      state.page -= 1;
      load();
    });
    el.nextBtn.addEventListener("click", () => {
      if (state.page >= state.totalPages - 1) return;
      state.page += 1;
      load();
    });
  }

  bindEvents();
  load();
})();
