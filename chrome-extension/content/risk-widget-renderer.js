const RiskWidgetRenderer = (() => {
  const C = {
    bg:            '#181A20',
    cardBg:        '#1E2329',
    border:        '#2B3139',
    textPrimary:   '#EAECEF',
    textSecondary: '#848E9C',
    textTertiary:  '#5E6673',
    red:           '#F6465D',
    redBg:         'rgba(246,70,93,0.10)',
    orange:        '#F0B90B',
    orangeBg:      'rgba(240,185,11,0.10)',
    green:         '#0ECB81',
    greenBg:       'rgba(14,203,129,0.10)',
    blue:          '#1E9CF4',
    blueBg:        'rgba(30,156,244,0.10)',
    critical:      '#F6465D',
    criticalBg:    'rgba(246,70,93,0.15)',
    high:          '#F0B90B',
    highBg:        'rgba(240,185,11,0.15)',
    medium:        '#1E9CF4',
    mediumBg:      'rgba(30,156,244,0.15)',
    low:           '#0ECB81',
    lowBg:         'rgba(14,203,129,0.15)',
  };

  let widget = null;
  let onRegisterCallback = null;
  let onUnregisterCallback = null;
  let isDragging = false;
  let dragOffsetX = 0;
  let dragOffsetY = 0;

  let isResizing = false;
  let resizeStartX = 0;
  let resizeStartY = 0;
  let resizeStartW = 0;
  let resizeStartH = 0;

  const t = (key) => I18n.get(key);

  function getRiskColor(level) {
    switch (level) {
      case 'CRITICAL': return { text: C.critical, bg: C.criticalBg };
      case 'HIGH':     return { text: C.high,     bg: C.highBg };
      case 'MEDIUM':   return { text: C.medium,   bg: C.mediumBg };
      case 'LOW':      return { text: C.low,      bg: C.lowBg };
      default:         return { text: C.textSecondary, bg: 'transparent' };
    }
  }

  function getDensityColor(level) {
    switch (level) {
      case 'THIN':     return { text: C.critical, bg: C.criticalBg, label: 'Thin' };
      case 'SPARSE':   return { text: C.high,     bg: C.highBg,     label: 'Sparse' };
      case 'MODERATE': return { text: C.medium,   bg: C.mediumBg,   label: 'Moderate' };
      case 'THICK':    return { text: C.green,    bg: C.greenBg,    label: 'Thick' };
      case 'WALL':     return { text: C.green,    bg: C.greenBg,    label: 'Wall' };
      default:         return { text: C.textSecondary, bg: 'transparent', label: '--' };
    }
  }

  function fmtNum(n, digits) {
    if (n === null || n === undefined) return '--';
    return Number(n).toFixed(digits !== undefined ? digits : 2);
  }

  function fmtPrice(p) {
    if (p === null || p === undefined) return '--';
    const n = Number(p);
    if (n >= 1000) return n.toFixed(1).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
    if (n >= 1) return n.toFixed(2);
    return n.toFixed(4);
  }

  function fmtPct(p) {
    if (p === null || p === undefined) return '--';
    return Number(p).toFixed(2) + '%';
  }

  function create(savedPosition, opts) {
    if (widget) return;

    if (opts) {
      onRegisterCallback = opts.onRegister || null;
      onUnregisterCallback = opts.onUnregister || null;
    }

    widget = document.createElement('div');
    widget.id = 'liq-risk-widget';

    widget.innerHTML = buildFormHTML() + buildHTML();
    document.body.appendChild(widget);

    if (savedPosition && savedPosition.riskWidgetTop !== undefined) {
      widget.style.top = savedPosition.riskWidgetTop + 'px';
      widget.style.left = savedPosition.riskWidgetLeft + 'px';
    } else {
      widget.style.top = '80px';
      widget.style.right = '12px';
    }

    setupDrag();
    setupResize();
    setupClose();
    setupForm();
    setupDetailToggle();
    applyI18n();
    showFormView();
  }

  function buildFormHTML() {
    return `
      <div class="rw-header">
        <div class="rw-header-left">
          <span class="rw-icon">&#9888;</span>
          <span class="rw-title" data-i18n="title"></span>
        </div>
        <button class="rw-close">&times;</button>
      </div>
      <div class="rw-form" id="rw-form">
        <div class="rw-form-group">
          <label class="rw-form-label" data-i18n="symbol"></label>
          <input type="text" id="rw-input-symbol" class="rw-input" placeholder="BTCUSDT" spellcheck="false" />
        </div>
        <div class="rw-form-group">
          <label class="rw-form-label" data-i18n="liqPrice"></label>
          <input type="number" id="rw-input-liq-price" class="rw-input" placeholder="0.00" step="any" />
        </div>
        <div class="rw-form-row">
          <div class="rw-form-group rw-form-half">
            <label class="rw-form-label" data-i18n="side"></label>
            <div class="rw-side-toggle" id="rw-side-toggle">
              <button class="rw-side-btn active" data-side="LONG">Long</button>
              <button class="rw-side-btn" data-side="SHORT">Short</button>
            </div>
          </div>
          <div class="rw-form-group rw-form-half">
            <label class="rw-form-label" data-i18n="leverage"></label>
            <div class="rw-leverage-wrap">
              <input type="number" id="rw-input-leverage" class="rw-input rw-input-sm" value="20" min="1" max="125" />
              <span class="rw-leverage-x">&times;</span>
            </div>
          </div>
        </div>
        <button class="rw-submit-btn" id="rw-submit-btn" data-i18n="startMonitoring"></button>
        <div class="rw-form-error" id="rw-form-error"></div>
      </div>
    `;
  }

  function buildHTML() {
    return `
      <div class="rw-monitor-header" id="rw-monitor-header" style="display:none">
        <div class="rw-header-left">
          <span class="rw-icon">&#9888;</span>
          <span class="rw-title" data-i18n="title"></span>
        </div>
        <div class="rw-header-right">
          <button class="rw-stop-btn" id="rw-stop-btn" data-i18n="stop"></button>
          <button class="rw-close">&times;</button>
        </div>
      </div>
      <div class="rw-body" id="rw-body" style="display:none">
        <!-- Section: Future Risk (MC) -->
        <div class="rw-section-subtitle" data-i18n="sectionFuture"></div>
        <div class="rw-mc-section" id="rw-mc-section">
          <div class="rw-mc-no-data" id="rw-mc-no-data" data-i18n="mcNoData"></div>
          <div class="rw-mc-content" id="rw-mc-content" style="display:none">
            <div class="rw-mc-risk-row">
              <span class="rw-label" data-i18n="mcRisk"></span>
              <span class="rw-mc-risk-badge" id="rw-mc-risk-badge">--</span>
            </div>
            <div class="rw-mc-horizons" id="rw-mc-horizons"></div>
            <div class="rw-mc-meta" id="rw-mc-meta">
              <span class="rw-label" data-i18n="mcVolatility"></span>
              <span class="rw-mc-meta-val" id="rw-mc-sigma">--</span>
              <span class="rw-mc-meta-sep">·</span>
              <span class="rw-label" data-i18n="mcPaths"></span>
              <span class="rw-mc-meta-val" id="rw-mc-paths">--</span>
            </div>
          </div>
        </div>
        <!-- Section: Current Risk (Cascade) -->
        <div class="rw-section-subtitle" data-i18n="sectionCurrent"></div>
        <div class="rw-risk-badge-row">
          <div class="rw-risk-badge" id="rw-risk-badge">--</div>
          <div class="rw-reach-prob">
            <span class="rw-label" data-i18n="reachProb"></span>
            <span class="rw-value" id="rw-reach-prob">--%</span>
          </div>
        </div>
        <div class="rw-divider"></div>
        <div class="rw-grid">
          <div class="rw-grid-item">
            <span class="rw-label" data-i18n="distance"></span>
            <span class="rw-value" id="rw-distance">--</span>
          </div>
          <div class="rw-grid-item">
            <span class="rw-label" data-i18n="direction"></span>
            <span class="rw-value" id="rw-direction">--</span>
          </div>
          <div class="rw-grid-item">
            <span class="rw-label" data-i18n="liqPriceLabel"></span>
            <span class="rw-value" id="rw-liq-price">--</span>
          </div>
          <div class="rw-grid-item">
            <span class="rw-label" data-i18n="current"></span>
            <span class="rw-value" id="rw-current-price">--</span>
          </div>
        </div>
        <!-- Detail Toggle -->
        <div class="rw-detail-toggle" id="rw-detail-toggle">
          <span class="rw-detail-toggle-text" data-i18n="details"></span>
          <span class="rw-detail-toggle-arrow" id="rw-detail-arrow">&#9660;</span>
        </div>
        <!-- Detail View (collapsed by default) -->
        <div class="rw-detail" id="rw-detail" style="display:none">
          <div class="rw-gauge-section">
            <div class="rw-gauge-header">
              <span class="rw-label" data-i18n="densityScore"></span>
              <span class="rw-value-sm" id="rw-density-val">--</span>
            </div>
            <div class="rw-gauge-bar">
              <div class="rw-gauge-fill" id="rw-density-fill"></div>
            </div>
            <div class="rw-gauge-labels">
              <span>Wall</span><span>Thick</span><span>Moderate</span><span>Sparse</span><span>Thin</span>
            </div>
          </div>
          <div class="rw-divider"></div>
          <div class="rw-section-title" data-i18n="orderBookPath"></div>
          <div class="rw-grid">
            <div class="rw-grid-item">
              <span class="rw-label" data-i18n="depth"></span>
              <span class="rw-value" id="rw-depth">--</span>
            </div>
            <div class="rw-grid-item">
              <span class="rw-label" data-i18n="notional"></span>
              <span class="rw-value" id="rw-notional">--</span>
            </div>
            <div class="rw-grid-item">
              <span class="rw-label" data-i18n="levels"></span>
              <span class="rw-value" id="rw-levels">--</span>
            </div>
            <div class="rw-grid-item">
              <span class="rw-label" data-i18n="depthRatio"></span>
              <span class="rw-value" id="rw-depth-ratio">--</span>
            </div>
          </div>
          <div class="rw-divider"></div>
          <div class="rw-section-title" data-i18n="liqClusters"></div>
          <div class="rw-cluster-list" id="rw-cluster-list">
            <div class="rw-no-data" data-i18n="noClusters"></div>
          </div>
          <div class="rw-divider"></div>
          <div class="rw-section-title" data-i18n="marketPressure"></div>
          <div class="rw-pressure-row">
            <div class="rw-pressure-item">
              <div class="rw-pressure-bar-wrap">
                <div class="rw-pressure-bar" id="rw-oi-bar"></div>
              </div>
              <span class="rw-pressure-label">OI</span>
              <span class="rw-pressure-val" id="rw-oi-val">--</span>
            </div>
            <div class="rw-pressure-item">
              <div class="rw-pressure-bar-wrap">
                <div class="rw-pressure-bar" id="rw-liq-bar"></div>
              </div>
              <span class="rw-pressure-label">Liq</span>
              <span class="rw-pressure-val" id="rw-liq-val">--</span>
            </div>
            <div class="rw-pressure-item">
              <div class="rw-pressure-bar-wrap">
                <div class="rw-pressure-bar" id="rw-imb-bar"></div>
              </div>
              <span class="rw-pressure-label">Imbal</span>
              <span class="rw-pressure-val" id="rw-imb-val">--</span>
            </div>
          </div>
          <div class="rw-pressure-total">
            <span class="rw-label" data-i18n="totalPressure"></span>
            <span class="rw-value" id="rw-pressure-total">--<span class="rw-sub">/60</span></span>
          </div>
        </div>
      </div>
      <div class="rw-resize"></div>
    `;
  }

  function applyI18n() {
    if (!widget) return;
    widget.querySelectorAll('[data-i18n]').forEach(el => {
      el.textContent = t(el.dataset.i18n);
    });
  }

  function update(report) {
    if (!widget || !report) return;

    const riskColor = getRiskColor(report.riskLevel);
    const badge = widget.querySelector('#rw-risk-badge');
    badge.textContent = report.riskLevel || '--';
    badge.style.color = riskColor.text;
    badge.style.background = riskColor.bg;
    badge.style.borderColor = riskColor.text;

    const reachEl = widget.querySelector('#rw-reach-prob');
    reachEl.textContent = fmtPct(report.cascadeReachProbability);
    reachEl.style.color = riskColor.text;

    const densityVal = widget.querySelector('#rw-density-val');
    const densityInfo = getDensityColor(report.densityLevel);
    densityVal.textContent = fmtNum(report.densityScore, 1) + ' / 100';
    densityVal.style.color = densityInfo.text;

    const densityFill = widget.querySelector('#rw-density-fill');
    const pct = Math.max(0, Math.min(100, report.densityScore || 0));
    densityFill.style.width = pct + '%';
    densityFill.style.background = densityInfo.text;

    widget.querySelector('#rw-distance').textContent = fmtPct(report.distancePercent);
    const dirEl = widget.querySelector('#rw-direction');
    if (report.direction === 'DOWN') {
      dirEl.textContent = t('dirDown');
      dirEl.style.color = C.red;
    } else if (report.direction === 'UP') {
      dirEl.textContent = t('dirUp');
      dirEl.style.color = C.green;
    } else {
      dirEl.textContent = '--';
      dirEl.style.color = C.textSecondary;
    }
    widget.querySelector('#rw-liq-price').textContent = fmtPrice(report.userLiquidationPrice);
    widget.querySelector('#rw-current-price').textContent = fmtPrice(report.currentPrice);

    widget.querySelector('#rw-depth').textContent = fmtNum(report.depthBetween, 4);
    const notional = report.notionalBetween;
    if (notional !== null && notional !== undefined) {
      const n = Number(notional);
      if (n >= 1000000) {
        widget.querySelector('#rw-notional').textContent = (n / 1000000).toFixed(2) + 'M';
      } else if (n >= 1000) {
        widget.querySelector('#rw-notional').textContent = (n / 1000).toFixed(1) + 'K';
      } else {
        widget.querySelector('#rw-notional').textContent = fmtNum(n, 0);
      }
    } else {
      widget.querySelector('#rw-notional').textContent = '--';
    }
    widget.querySelector('#rw-levels').textContent = report.levelCount !== undefined ? report.levelCount : '--';
    widget.querySelector('#rw-depth-ratio').textContent = fmtPct(report.depthRatio);

    renderClusters(report.clustersInPath || []);

    updatePressureBar('rw-oi-bar', 'rw-oi-val', report.oiPressureScore, 20);
    updatePressureBar('rw-liq-bar', 'rw-liq-val', report.liqIntensityScore, 20);
    updatePressureBar('rw-imb-bar', 'rw-imb-val', report.imbalanceScore, 20);

    const totalEl = widget.querySelector('#rw-pressure-total');
    totalEl.querySelector('.rw-value').innerHTML =
      (report.marketPressureTotal !== undefined ? report.marketPressureTotal : '--') +
      '<span class="rw-sub">/60</span>';
  }

  function updatePressureBar(barId, valId, score, max) {
    const bar = widget.querySelector('#' + barId);
    const val = widget.querySelector('#' + valId);
    const s = score !== undefined ? score : 0;
    const pct = Math.max(0, Math.min(100, (s / max) * 100));
    bar.style.width = pct + '%';

    let color;
    if (pct >= 75) color = C.critical;
    else if (pct >= 50) color = C.high;
    else if (pct >= 25) color = C.medium;
    else color = C.green;
    bar.style.background = color;

    val.textContent = s + '/' + max;
    val.style.color = color;
  }

  function renderClusters(clusters) {
    const list = widget.querySelector('#rw-cluster-list');
    if (!clusters || clusters.length === 0) {
      list.innerHTML = '<div class="rw-no-data">' + t('noClusters') + '</div>';
      return;
    }

    const sorted = [...clusters].sort((a, b) => a.distanceFromCurrentPercent - b.distanceFromCurrentPercent);
    const maxVol = Math.max(...sorted.map(c => Number(c.estimatedNotional || 0)), 1);

    list.innerHTML = sorted.map(c => {
      const volPct = Math.max(3, (Number(c.estimatedNotional || 0) / maxVol) * 100);
      const notional = Number(c.estimatedNotional || 0);
      let notionalStr;
      if (notional >= 1000000) notionalStr = (notional / 1000000).toFixed(2) + 'M';
      else if (notional >= 1000) notionalStr = (notional / 1000).toFixed(1) + 'K';
      else notionalStr = notional.toFixed(0);

      return `
        <div class="rw-cluster-row">
          <span class="rw-cluster-lev">${c.leverage}x</span>
          <div class="rw-cluster-bar-wrap">
            <div class="rw-cluster-bar" style="width:${volPct}%"></div>
          </div>
          <span class="rw-cluster-vol">${notionalStr}</span>
          <span class="rw-cluster-dist">${fmtNum(c.distanceFromCurrentPercent, 2)}%</span>
        </div>
      `;
    }).join('');
  }

  function setupForm() {
    const toggle = widget.querySelector('#rw-side-toggle');
    toggle.addEventListener('click', (e) => {
      const btn = e.target.closest('.rw-side-btn');
      if (!btn) return;
      toggle.querySelectorAll('.rw-side-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
    });

    widget.querySelector('#rw-submit-btn').addEventListener('click', () => {
      const symbol = (widget.querySelector('#rw-input-symbol').value || '').trim().toUpperCase();
      const liqPrice = parseFloat(widget.querySelector('#rw-input-liq-price').value);
      const side = toggle.querySelector('.rw-side-btn.active')?.dataset.side || 'LONG';
      const leverage = parseInt(widget.querySelector('#rw-input-leverage').value) || 20;
      const errorEl = widget.querySelector('#rw-form-error');

      if (!symbol) { errorEl.textContent = t('errSymbol'); return; }
      if (!liqPrice || liqPrice <= 0) { errorEl.textContent = t('errLiqPrice'); return; }
      errorEl.textContent = '';

      if (onRegisterCallback) {
        onRegisterCallback({ symbol, liquidationPrice: liqPrice, side, leverage });
      }
      showMonitorView(symbol, side, leverage);
    });

    const symbolInput = widget.querySelector('#rw-input-symbol');
    const url = window.location.pathname;
    const match = url.match(/\/futures\/(\w+)/i);
    if (match) symbolInput.value = match[1].toUpperCase();
  }

  function setupDetailToggle() {
    const toggle = widget.querySelector('#rw-detail-toggle');
    const detail = widget.querySelector('#rw-detail');
    const arrow = widget.querySelector('#rw-detail-arrow');
    if (!toggle || !detail) return;

    toggle.addEventListener('click', () => {
      const isOpen = detail.style.display !== 'none';
      detail.style.display = isOpen ? 'none' : '';
      arrow.innerHTML = isOpen ? '&#9660;' : '&#9650;';
      toggle.classList.toggle('open', !isOpen);
    });
  }

  function showFormView() {
    if (!widget) return;
    const form = widget.querySelector('#rw-form');
    const formHeader = form?.previousElementSibling;
    const monitorHeader = widget.querySelector('#rw-monitor-header');
    const body = widget.querySelector('#rw-body');

    if (form) form.style.display = '';
    if (formHeader && formHeader.classList.contains('rw-header')) formHeader.style.display = '';
    if (monitorHeader) monitorHeader.style.display = 'none';
    if (body) body.style.display = 'none';
  }

  function showMonitorView(symbol, side, leverage) {
    if (!widget) return;
    const form = widget.querySelector('#rw-form');
    const formHeader = form?.previousElementSibling;
    const monitorHeader = widget.querySelector('#rw-monitor-header');
    const body = widget.querySelector('#rw-body');

    if (form) form.style.display = 'none';
    if (formHeader && formHeader.classList.contains('rw-header')) formHeader.style.display = 'none';
    if (monitorHeader) monitorHeader.style.display = '';
    if (body) body.style.display = '';

    const stopBtn = widget.querySelector('#rw-stop-btn');
    if (stopBtn) {
      stopBtn.onclick = () => {
        if (onUnregisterCallback) onUnregisterCallback(symbol);
        showFormView();
      };
    }
  }

  function setupDrag() {
    widget.querySelectorAll('.rw-header, .rw-monitor-header').forEach(header => {
      header.addEventListener('mousedown', (e) => {
        if (e.target.classList.contains('rw-close') || e.target.classList.contains('rw-stop-btn')) return;
        isDragging = true;
        const rect = widget.getBoundingClientRect();
        dragOffsetX = e.clientX - rect.left;
        dragOffsetY = e.clientY - rect.top;
        e.preventDefault();
      });
    });

    document.addEventListener('mousemove', (e) => {
      if (!isDragging) return;
      widget.style.left = Math.max(0, e.clientX - dragOffsetX) + 'px';
      widget.style.top = Math.max(0, e.clientY - dragOffsetY) + 'px';
      widget.style.right = 'auto';
    });

    document.addEventListener('mouseup', () => {
      if (!isDragging) return;
      isDragging = false;
      savePosition();
    });
  }

  function setupResize() {
    const handle = widget.querySelector('.rw-resize');
    handle.addEventListener('mousedown', (e) => {
      isResizing = true;
      resizeStartX = e.clientX;
      resizeStartY = e.clientY;
      resizeStartW = widget.clientWidth;
      resizeStartH = widget.clientHeight;
      document.body.style.cursor = 'nwse-resize';
      document.body.style.userSelect = 'none';
      e.preventDefault();
      e.stopPropagation();
    });

    document.addEventListener('mousemove', (e) => {
      if (!isResizing) return;
      const newW = Math.max(200, Math.min(500, resizeStartW + (e.clientX - resizeStartX)));
      const newH = Math.max(200, Math.min(800, resizeStartH + (e.clientY - resizeStartY)));
      widget.style.width = newW + 'px';
      widget.style.height = newH + 'px';
    });

    document.addEventListener('mouseup', () => {
      if (!isResizing) return;
      isResizing = false;
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
    });
  }

  function setupClose() {
    widget.querySelectorAll('.rw-close').forEach(btn => {
      btn.addEventListener('click', () => {
        widget.style.display = 'none';
      });
    });
  }

  function savePosition() {
    const rect = widget.getBoundingClientRect();
    chrome.storage?.sync?.set({
      liqRiskWidgetPosition: {
        riskWidgetTop: rect.top,
        riskWidgetLeft: rect.left,
      },
    });
  }

  function getMcProbColor(prob) {
    if (prob >= 0.50) return C.critical;
    if (prob >= 0.25) return C.high;
    if (prob >= 0.10) return C.orange;
    return C.green;
  }

  function getMcHorizonLabel(minutes) {
    if (minutes <= 10) return t('mc10m');
    if (minutes <= 60) return t('mc1h');
    if (minutes <= 240) return t('mc4h');
    return t('mc24h');
  }

  function updateMc(mcReport) {
    if (!widget || !mcReport) return;

    const noData = widget.querySelector('#rw-mc-no-data');
    const content = widget.querySelector('#rw-mc-content');
    if (noData) noData.style.display = 'none';
    if (content) content.style.display = '';

    const riskBadge = widget.querySelector('#rw-mc-risk-badge');
    if (riskBadge && mcReport.riskLevel) {
      const rc = getRiskColor(mcReport.riskLevel);
      riskBadge.textContent = mcReport.riskLevel;
      riskBadge.style.color = rc.text;
      riskBadge.style.background = rc.bg;
      riskBadge.style.borderColor = rc.text;
    }

    const horizonsEl = widget.querySelector('#rw-mc-horizons');
    if (horizonsEl && mcReport.horizons) {
      horizonsEl.innerHTML = mcReport.horizons.map(h => {
        const prob = h.liquidationProbability || 0;
        const pct = (prob * 100).toFixed(1);
        const barWidth = Math.max(1, Math.min(100, prob * 100));
        const color = getMcProbColor(prob);
        const label = getMcHorizonLabel(h.minutes);

        return `
          <div class="rw-mc-horizon-row">
            <span class="rw-mc-horizon-label">${label}</span>
            <div class="rw-mc-bar-wrap">
              <div class="rw-mc-bar" style="width:${barWidth}%;background:${color}"></div>
            </div>
            <span class="rw-mc-prob" style="color:${color}">${pct}%</span>
          </div>
        `;
      }).join('');
    }

    const sigmaEl = widget.querySelector('#rw-mc-sigma');
    if (sigmaEl) {
      sigmaEl.textContent = mcReport.sigma !== undefined
        ? (mcReport.sigma * 100).toFixed(1) + '%/yr'
        : '--';
    }

    const pathsEl = widget.querySelector('#rw-mc-paths');
    if (pathsEl) {
      pathsEl.textContent = mcReport.pathCount !== undefined
        ? (mcReport.pathCount >= 1000 ? (mcReport.pathCount / 1000).toFixed(0) + 'K' : mcReport.pathCount)
        : '--';
    }
  }

  function show() { if (widget) widget.style.display = 'flex'; }
  function hide() { if (widget) widget.style.display = 'none'; }
  function isCreated() { return !!widget; }

  return { create, update, updateMc, applyI18n, show, hide, isCreated };
})();
