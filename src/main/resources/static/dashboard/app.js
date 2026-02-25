(() => {
  "use strict";

  const BACKEND_BASE_URL = `${location.protocol}//${location.hostname}:8080`;
  const WS_ENDPOINT = "/ws";
  const t = (key, vars) => DashboardI18n.get(key, vars);

  const el = {
    langSelector: document.getElementById("lang-selector"),
    setupView: document.getElementById("setup-view"),
    monitorView: document.getElementById("monitor-view"),
    form: document.getElementById("monitor-form"),
    feedbackForm: document.getElementById("feedback-form"),
    feedbackName: document.getElementById("feedback-name"),
    feedbackMessage: document.getElementById("feedback-message"),
    feedbackSubmit: document.getElementById("feedback-submit"),
    feedbackResult: document.getElementById("feedback-result"),
    symbol: document.getElementById("symbol"),
    liqPrice: document.getElementById("liq-price"),
    side: document.getElementById("position-side"),
    leverage: document.getElementById("leverage"),
    startBtn: document.getElementById("start-btn"),
    stopBtn: document.getElementById("stop-btn"),
    backBtn: document.getElementById("back-btn"),
    msg: document.getElementById("form-message"),
    wsDot: document.getElementById("ws-status-dot"),
    wsText: document.getElementById("ws-status-text"),
    activeSymbol: document.getElementById("active-symbol"),
    activeSide: document.getElementById("active-side"),
    activeLev: document.getElementById("active-lev"),
    activeLiq: document.getElementById("active-liq"),
    mCascadeRisk: document.getElementById("m-cascade-risk"),
    mReachProb: document.getElementById("m-reach-prob"),
    mMcRisk: document.getElementById("m-mc-risk"),
    distance: document.getElementById("distance"),
    direction: document.getElementById("direction"),
    density: document.getElementById("density"),
    pressure: document.getElementById("pressure"),
    oiScore: document.getElementById("oi-score"),
    liqScore: document.getElementById("liq-score"),
    imbalScore: document.getElementById("imbal-score"),
    currentPrice: document.getElementById("current-price"),
    liquidationPrice: document.getElementById("liquidation-price"),
    mcSigma: document.getElementById("mc-sigma"),
    mcPaths: document.getElementById("mc-paths"),
    horizons: document.getElementById("horizon-list")
  };

  let stompClient = null;
  let riskSubscription = null;
  let mcSubscription = null;
  let activeSymbol = null;

  function setActiveLangBtn() {
    const lang = DashboardI18n.getLang();
    document.querySelectorAll(".lang-btn").forEach((btn) => {
      btn.classList.toggle("active", btn.dataset.lang === lang);
    });
  }

  function applyTranslations() {
    document.querySelectorAll("[data-i18n]").forEach((node) => {
      const key = node.dataset.i18n;
      node.textContent = t(key);
    });

    document.querySelectorAll("[data-i18n-placeholder]").forEach((node) => {
      const key = node.dataset.i18nPlaceholder;
      node.placeholder = t(key);
    });

    const online = el.wsDot.classList.contains("online");
    el.wsText.textContent = online ? t("wsConnected") : t("wsDisconnected");

    if (el.mcSigma.textContent.includes(":")) {
      const sigmaVal = el.mcSigma.textContent.split(":").slice(1).join(":").trim();
      el.mcSigma.textContent = `${t("mcVolatility")}: ${sigmaVal}`;
    } else {
      el.mcSigma.textContent = `${t("mcVolatility")}: --`;
    }

    if (el.mcPaths.textContent.includes(":")) {
      const pathVal = el.mcPaths.textContent.split(":").slice(1).join(":").trim();
      el.mcPaths.textContent = `${t("mcPaths")}: ${pathVal}`;
    } else {
      el.mcPaths.textContent = `${t("mcPaths")}: --`;
    }

    if (!el.horizons.querySelector(".horizon-row")) {
      el.horizons.innerHTML = `<p class="empty">${t("mcNoData")}</p>`;
    }
  }

  function showSetupView() {
    el.setupView.classList.remove("hidden");
    el.monitorView.classList.add("hidden");
  }

  function showMonitorView(config) {
    el.setupView.classList.add("hidden");
    el.monitorView.classList.remove("hidden");
    el.activeSymbol.textContent = config.symbol;
    el.activeSide.textContent = config.positionSide;
    el.activeLev.textContent = `${config.leverage}x`;
    el.activeLiq.textContent = `${t("tagLiqPrefix")}: ${formatNumber(config.liquidationPrice, 2)}`;
  }

  function setConnectionStatus(online) {
    el.wsDot.classList.toggle("online", online);
    el.wsDot.classList.toggle("offline", !online);
    el.wsText.textContent = online ? t("wsConnected") : t("wsDisconnected");
  }

  function setMessage(text, type = "normal") {
    el.msg.textContent = text || "";
    if (type === "error") {
      el.msg.style.color = "#f6465d";
    } else if (type === "success") {
      el.msg.style.color = "#0ecb81";
    } else {
      el.msg.style.color = "#848e9c";
    }
  }

  function formatNumber(value, digits = 2) {
    if (value === undefined || value === null || Number.isNaN(Number(value))) return "--";
    return Number(value).toLocaleString("en-US", {
      minimumFractionDigits: digits,
      maximumFractionDigits: digits
    });
  }

  function formatPct(value, digits = 2) {
    if (value === undefined || value === null || Number.isNaN(Number(value))) return "--";
    return `${formatNumber(value, digits)}%`;
  }

  function riskClass(level) {
    const lv = String(level || "").toLowerCase();
    if (lv === "low") return "risk-low";
    if (lv === "medium") return "risk-medium";
    if (lv === "high") return "risk-high";
    if (lv === "critical") return "risk-critical";
    return "";
  }

  function applyRiskValue(element, level) {
    element.classList.remove("risk-low", "risk-medium", "risk-high", "risk-critical");
    const cls = riskClass(level);
    if (cls) element.classList.add(cls);
    element.textContent = level || "--";
  }

  function horizonLabel(minutes) {
    if (minutes <= 10) return "10m";
    if (minutes <= 60) return "1h";
    if (minutes <= 240) return "4h";
    if (minutes <= 1440) return "24h";
    return `${minutes}m`;
  }

  function horizonColor(probability) {
    if (probability >= 0.5) return "#f6465d";
    if (probability >= 0.25) return "#f0b90b";
    if (probability >= 0.1) return "#1e9cf4";
    return "#0ecb81";
  }

  function renderMcHorizons(horizons = []) {
    if (!Array.isArray(horizons) || horizons.length === 0) {
      el.horizons.innerHTML = `<p class="empty">${t("mcNoData")}</p>`;
      return;
    }

    el.horizons.innerHTML = horizons.map((h) => {
      const prob = Number(h.calibratedProbability ?? h.liquidationProbability ?? 0);
      const pct = Math.max(0, Math.min(100, prob * 100));
      const color = horizonColor(prob);
      return `
        <div class="horizon-row">
          <span class="horizon-label">${horizonLabel(Number(h.minutes || 0))}</span>
          <div class="bar-wrap">
            <div class="bar" style="width:${pct}%;background:${color}"></div>
          </div>
          <span class="horizon-value" style="color:${color}">${pct.toFixed(1)}%</span>
        </div>
      `;
    }).join("");
  }

  function updateCascadeReport(report) {
    if (report.symbol) {
      el.activeSymbol.textContent = report.symbol;
    }
    applyRiskValue(el.mCascadeRisk, report.riskLevel);
    el.mReachProb.textContent = formatPct(
      report.calibratedReachProbability ?? report.cascadeReachProbability
    );

    el.distance.textContent = formatPct(report.distancePercent);
    el.direction.textContent = report.direction || "--";
    el.density.textContent = `${formatNumber(report.densityScore, 1)} / 100`;
    el.pressure.textContent = `${report.marketPressureTotal ?? "--"} / 60`;
    el.oiScore.textContent = `${report.oiPressureScore ?? "--"} / 20`;
    el.liqScore.textContent = `${report.liqIntensityScore ?? "--"} / 20`;
    el.imbalScore.textContent = `${report.imbalanceScore ?? "--"} / 20`;
    el.currentPrice.textContent = formatNumber(report.currentPrice, 2);
    el.liquidationPrice.textContent = formatNumber(report.userLiquidationPrice, 2);
  }

  function updateMcReport(report) {
    applyRiskValue(el.mMcRisk, report.riskLevel);
    el.mcSigma.textContent = `${t("mcVolatility")}: ${formatPct((report.sigma || 0) * 100, 1)} / yr`;
    el.mcPaths.textContent = `${t("mcPaths")}: ${(report.pathCount || 0).toLocaleString("en-US")}`;
    renderMcHorizons(report.horizons);
  }

  function connectStomp() {
    return new Promise((resolve, reject) => {
      if (stompClient && stompClient.connected) {
        resolve(stompClient);
        return;
      }

      if (typeof SockJS === "undefined" || typeof Stomp === "undefined") {
        reject(new Error(t("msgWsLibMissing")));
        return;
      }

      const socket = new SockJS(`${BACKEND_BASE_URL}${WS_ENDPOINT}`);
      stompClient = Stomp.over(socket);
      stompClient.debug = () => {};
      stompClient.reconnect_delay = 5000;

      stompClient.connect(
        {},
        () => {
          setConnectionStatus(true);
          resolve(stompClient);
        },
        () => {
          setConnectionStatus(false);
          setMessage(t("msgWsDisconnected"), "error");
        }
      );
    });
  }

  function unsubscribeAll() {
    if (riskSubscription) {
      riskSubscription.unsubscribe();
      riskSubscription = null;
    }
    if (mcSubscription) {
      mcSubscription.unsubscribe();
      mcSubscription = null;
    }
  }

  function subscribeSymbol(symbol) {
    if (!stompClient || !stompClient.connected) return;
    unsubscribeAll();

    riskSubscription = stompClient.subscribe(`/topic/risk/${symbol}`, (message) => {
      try {
        updateCascadeReport(JSON.parse(message.body));
      } catch (err) {
        console.error("[Dashboard] Risk message parse error", err);
      }
    });

    mcSubscription = stompClient.subscribe(`/topic/mc/${symbol}`, (message) => {
      try {
        updateMcReport(JSON.parse(message.body));
      } catch (err) {
        console.error("[Dashboard] MC message parse error", err);
      }
    });
  }

  async function registerPosition(payload) {
    const response = await fetch(`${BACKEND_BASE_URL}/api/position/register`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });

    const data = await response.json();
    if (!response.ok || !data.success) {
      throw new Error(data.message || "포지션 등록 실패");
    }
    return data;
  }

  async function unregisterPosition(symbol) {
    await fetch(`${BACKEND_BASE_URL}/api/position/unregister?symbol=${encodeURIComponent(symbol)}`, {
      method: "DELETE"
    });
  }

  async function submitFeedback(payload) {
    const response = await fetch(`${BACKEND_BASE_URL}/api/feedback`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });

    const data = await response.json();
    if (!response.ok || !data.success) {
      throw new Error(data.message || t("feedbackError"));
    }
    return data;
  }

  async function handleStart(event) {
    event.preventDefault();

    const symbol = String(el.symbol.value || "").toUpperCase();
    const liquidationPrice = Number(el.liqPrice.value);
    const positionSide = String(el.side.value || "LONG").toUpperCase();
    const leverage = Number(el.leverage.value || 20);

    if (!symbol) {
      setMessage(t("msgSelectSymbol"), "error");
      return;
    }
    if (!liquidationPrice || liquidationPrice <= 0) {
      setMessage(t("msgInvalidLiq"), "error");
      return;
    }

    el.startBtn.disabled = true;
    setMessage(t("msgStarting"));

    try {
      await connectStomp();
      const payload = { symbol, liquidationPrice, positionSide, leverage };
      await registerPosition(payload);
      activeSymbol = symbol;
      subscribeSymbol(symbol);
      el.stopBtn.disabled = false;
      showMonitorView(payload);
      setMessage(t("msgStarted", { symbol }), "success");
    } catch (error) {
      setMessage(error.message || t("msgStartFailed"), "error");
    } finally {
      el.startBtn.disabled = false;
    }
  }

  async function handleStop() {
    if (!activeSymbol) return;
    el.stopBtn.disabled = true;
    try {
      await unregisterPosition(activeSymbol);
      unsubscribeAll();
      setMessage(t("msgStopped", { symbol: activeSymbol }), "success");
      activeSymbol = null;
      showSetupView();
    } catch (error) {
      setMessage(error.message || t("msgStopFailed"), "error");
      el.stopBtn.disabled = false;
    }
  }

  async function handleBack() {
    if (activeSymbol) {
      await handleStop();
      return;
    }
    showSetupView();
  }

  async function handleFeedbackSubmit(event) {
    event.preventDefault();
    const name = (el.feedbackName.value || "").trim();
    const message = (el.feedbackMessage.value || "").trim();

    if (!message) {
      el.feedbackResult.style.color = "#f6465d";
      el.feedbackResult.textContent = t("feedbackValidation");
      return;
    }

    el.feedbackSubmit.disabled = true;
    el.feedbackResult.style.color = "#848e9c";
    el.feedbackResult.textContent = "...";

    try {
      await submitFeedback({
        name,
        message,
        lang: DashboardI18n.getLang(),
        symbol: activeSymbol || null
      });
      el.feedbackMessage.value = "";
      el.feedbackResult.style.color = "#0ecb81";
      el.feedbackResult.textContent = t("feedbackSent");
    } catch (error) {
      el.feedbackResult.style.color = "#f6465d";
      el.feedbackResult.textContent = error.message || t("feedbackError");
    } finally {
      el.feedbackSubmit.disabled = false;
    }
  }

  function init() {
    setActiveLangBtn();
    applyTranslations();
    setConnectionStatus(false);
    showSetupView();

    el.langSelector.addEventListener("click", (e) => {
      const btn = e.target.closest(".lang-btn");
      if (!btn) return;
      DashboardI18n.setLang(btn.dataset.lang);
      setActiveLangBtn();
      applyTranslations();
      if (activeSymbol) {
        el.activeLiq.textContent = `${t("tagLiqPrefix")}: ${formatNumber(Number(el.liqPrice.value), 2)}`;
      }
    });

    el.form.addEventListener("submit", handleStart);
    el.feedbackForm.addEventListener("submit", handleFeedbackSubmit);
    el.stopBtn.addEventListener("click", handleStop);
    el.backBtn.addEventListener("click", handleBack);
  }

  init();
})();
