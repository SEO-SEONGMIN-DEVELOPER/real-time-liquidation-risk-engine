(() => {
  'use strict';

  let panel = null;
  let hCanvas = null;
  let chartCanvas = null;
  let isEnabled = true;
  let updateTimer = null;
  let settings = {
    priceRangePercent: 10,
    priceBucketCount: 60,
    overlayWidth: 180,
    panelHeight: 400,
    opacity: 0.95,
    updateIntervalMs: 1000,
  };

  let isDragging = false;
  let dragOffsetX = 0;
  let dragOffsetY = 0;

  let isResizing = false;
  let resizeStartX = 0;
  let resizeStartY = 0;
  let resizeStartW = 0;
  let resizeStartH = 0;

  function bootstrap() {
    console.log('[LiqHeatmap] 청산맵 확장 프로그램 로드됨');

    chrome.storage?.sync?.get(['liqHeatmapSettings', 'liqHeatmapEnabled', 'liqHeatmapPosition'], (result) => {
      if (result.liqHeatmapSettings) {
        settings = { ...settings, ...result.liqHeatmapSettings };
      }
      if (result.liqHeatmapEnabled === false) {
        isEnabled = false;
      }
      console.log('[LiqHeatmap] 설정 로드:', { isEnabled, settings: result.liqHeatmapSettings, pos: result.liqHeatmapPosition });
      waitForChart(result.liqHeatmapPosition || null);
    });

    chrome.runtime?.onMessage?.addListener((msg) => {
      if (msg.type === 'TOGGLE_HEATMAP') {
        isEnabled = msg.enabled;
        togglePanel();
      }
      if (msg.type === 'UPDATE_SETTINGS') {
        settings = { ...settings, ...msg.settings };
        applySettings();
      }
    });
  }

  function findChartCanvas() {
    const allCanvas = document.querySelectorAll('canvas');
    let best = null;
    let bestArea = 0;

    allCanvas.forEach((c) => {
      const rect = c.getBoundingClientRect();
      const area = rect.width * rect.height;
      if (rect.width > 400 && rect.height > 200 && area > bestArea) {
        best = c;
        bestArea = area;
      }
    });

    return best;
  }

  function waitForChart(savedPosition) {
    const found = findChartCanvas();
    if (found) {
      console.log('[LiqHeatmap] 차트 캔버스 발견');
      chartCanvas = found;
      createPanel(savedPosition);
      return;
    }

    console.log('[LiqHeatmap] 차트 캔버스 대기 중...');
    let attempts = 0;
    const poll = setInterval(() => {
      attempts++;
      const c = findChartCanvas();
      if (c) {
        clearInterval(poll);
        console.log('[LiqHeatmap] 차트 캔버스 발견 (' + attempts + '회 시도)');
        chartCanvas = c;
        createPanel(savedPosition);
      }
      if (attempts > 60) {
        clearInterval(poll);
        console.warn('[LiqHeatmap] 차트 캔버스를 찾지 못했습니다 (60회 시도).');
      }
    }, 500);
  }

  function createPanel(savedPosition) {
    if (panel) return;

    panel = document.createElement('div');
    panel.id = 'liq-heatmap-panel';
    panel.style.opacity = settings.opacity;
    panel.style.width = settings.overlayWidth + 'px';
    panel.style.height = settings.panelHeight + 'px';

    const header = document.createElement('div');
    header.id = 'liq-heatmap-header';
    header.innerHTML = '<span class="title">Liquidation Map</span><button class="close-btn">\u00D7</button>';
    panel.appendChild(header);

    hCanvas = document.createElement('canvas');
    hCanvas.id = 'liq-heatmap-canvas';
    panel.appendChild(hCanvas);

    const footer = document.createElement('div');
    footer.id = 'liq-heatmap-footer';
    footer.innerHTML =
      '<span class="legend-item"><span class="dot long"></span>Long</span>' +
      '<span class="legend-item"><span class="dot short"></span>Short</span>';
    panel.appendChild(footer);

    const resizeHandle = document.createElement('div');
    resizeHandle.id = 'liq-heatmap-resize';
    panel.appendChild(resizeHandle);

    document.body.appendChild(panel);

    if (savedPosition && isPositionValid(savedPosition)) {
      panel.style.top = savedPosition.top + 'px';
      panel.style.left = savedPosition.left + 'px';
    } else {
      positionNextToChart();
    }

    syncCanvasSize();
    HistogramRenderer.init(hCanvas);
    setupDrag(header);
    setupResize(resizeHandle);

    header.querySelector('.close-btn').addEventListener('click', () => {
      isEnabled = false;
      chrome.storage?.sync?.set({ liqHeatmapEnabled: false });
      togglePanel();
    });

    togglePanel();
    startUpdateLoop();
    console.log('[LiqHeatmap] 플로팅 패널 삽입 완료');
  }

  function isPositionValid(pos) {
    if (!pos || typeof pos.top !== 'number' || typeof pos.left !== 'number') return false;
    return pos.top >= -50 && pos.top < window.innerHeight - 30
        && pos.left >= -50 && pos.left < window.innerWidth - 30;
  }

  function positionNextToChart() {
    if (!panel || !chartCanvas) return;

    const rect = chartCanvas.getBoundingClientRect();
    const left = Math.min(rect.right + 10, window.innerWidth - settings.overlayWidth - 10);
    const top = Math.max(rect.top, 60);

    panel.style.top = top + 'px';
    panel.style.left = left + 'px';
    console.log('[LiqHeatmap] 패널 위치:', { top, left });
  }

  function syncCanvasSize() {
    if (!hCanvas || !panel) return;
    const headerH = 32;
    const footerH = 24;
    const panelH = panel.clientHeight || settings.panelHeight;
    const canvasH = Math.max(100, panelH - headerH - footerH);
    const canvasW = panel.clientWidth || settings.overlayWidth;

    hCanvas.style.width = canvasW + 'px';
    hCanvas.style.height = canvasH + 'px';
  }

  function setupDrag(handle) {
    handle.addEventListener('mousedown', (e) => {
      if (e.target.classList.contains('close-btn')) return;
      isDragging = true;
      const rect = panel.getBoundingClientRect();
      dragOffsetX = e.clientX - rect.left;
      dragOffsetY = e.clientY - rect.top;
      e.preventDefault();
    });

    document.addEventListener('mousemove', (e) => {
      if (!isDragging) return;
      panel.style.left = Math.max(0, e.clientX - dragOffsetX) + 'px';
      panel.style.top = Math.max(0, e.clientY - dragOffsetY) + 'px';
    });

    document.addEventListener('mouseup', () => {
      if (!isDragging) return;
      isDragging = false;
      savePosition();
    });
  }

  function setupResize(handle) {
    handle.addEventListener('mousedown', (e) => {
      isResizing = true;
      resizeStartX = e.clientX;
      resizeStartY = e.clientY;
      resizeStartW = panel.clientWidth;
      resizeStartH = panel.clientHeight;
      document.body.style.cursor = 'nwse-resize';
      document.body.style.userSelect = 'none';
      e.preventDefault();
      e.stopPropagation();
    });

    document.addEventListener('mousemove', (e) => {
      if (!isResizing) return;

      const newW = Math.max(140, Math.min(500, resizeStartW + (e.clientX - resizeStartX)));
      const newH = Math.max(200, Math.min(800, resizeStartH + (e.clientY - resizeStartY)));

      panel.style.width = newW + 'px';
      panel.style.height = newH + 'px';
      settings.overlayWidth = newW;
      settings.panelHeight = newH;

      syncCanvasSize();
      update();
    });

    document.addEventListener('mouseup', () => {
      if (!isResizing) return;
      isResizing = false;
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
      saveSettings();
    });
  }

  function savePosition() {
    const rect = panel.getBoundingClientRect();
    chrome.storage?.sync?.set({
      liqHeatmapPosition: { top: rect.top, left: rect.left },
    });
  }

  function saveSettings() {
    chrome.storage?.sync?.set({ liqHeatmapSettings: settings });
  }

  function togglePanel() {
    if (!panel) return;
    panel.style.display = isEnabled ? 'flex' : 'none';
    if (isEnabled) {
      syncCanvasSize();
      update();
    }
  }

  function applySettings() {
    if (!panel) return;
    panel.style.opacity = settings.opacity;
    panel.style.width = settings.overlayWidth + 'px';
    panel.style.height = settings.panelHeight + 'px';
    syncCanvasSize();
    update();
  }

  function startUpdateLoop() {
    if (updateTimer) clearInterval(updateTimer);
    updateTimer = setInterval(() => {
      if (isEnabled) update();
    }, settings.updateIntervalMs);
  }

  function update() {
    const price = extractCurrentPrice();
    if (!price || price <= 0) return;

    const data = LiquidationCalc.generate(price, {
      priceBucketCount: settings.priceBucketCount,
      priceRangePercent: settings.priceRangePercent,
    });

    HistogramRenderer.render(data);
  }

  function extractCurrentPrice() {
    const titleMatch = document.title.match(/([\d,]+\.?\d*)/);
    if (titleMatch) {
      const price = parseFloat(titleMatch[1].replace(/,/g, ''));
      if (price > 0) return price;
    }

    const priceSelectors = [
      '[class*="showPrice"]',
      '[class*="markPrice"]',
      '[class*="contractPrice"]',
      '[class*="lastPrice"]',
      '.price',
    ];

    for (const sel of priceSelectors) {
      const el = document.querySelector(sel);
      if (el) {
        const price = parsePrice(el.textContent);
        if (price && price > 1) return price;
      }
    }

    return null;
  }

  function parsePrice(text) {
    if (!text) return null;
    const cleaned = text.replace(/[^0-9.]/g, '');
    const num = parseFloat(cleaned);
    return isNaN(num) ? null : num;
  }

  bootstrap();
})();
