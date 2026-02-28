(() => {
  'use strict';

  let panel = null;
  let hCanvas = null;
  let chartCanvas = null;
  let isEnabled = true;
  let updateTimer = null;
  let positionScrapeTimer = null;
  let settings = {
    priceRangePercent: 10,
    priceBucketCount: 60,
    overlayWidth: 180,
    panelHeight: 400,
    opacity: 0.95,
    updateIntervalMs: 1000,
  };

  let userPosition = {
    symbol: null,
    side: null,
    entryPrice: null,
    liquidationPrice: null,
    leverage: null,
    size: null,
    margin: null,
    pnl: null,
    roe: null,
    markPrice: null,
  };

  let riskWidgetTimer = null;
  const BACKEND_BASE_URL = 'http://localhost:8080';

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
    startPositionScraping();
    initRiskWidget();
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

  function startPositionScraping() {
    scrapePosition();
    if (positionScrapeTimer) clearInterval(positionScrapeTimer);
    positionScrapeTimer = setInterval(() => {
      if (isEnabled) scrapePosition();
    }, 2000);
  }

  function scrapePosition() {
    try {
      const posData = extractPositionFromDOM();
      if (posData && posData.found) {
        userPosition = { ...userPosition, ...posData };
        console.log('[LiqHeatmap] 포지션 감지:', userPosition);
      }
    } catch (e) {
      console.debug('[LiqHeatmap] 포지션 스크래핑 오류:', e);
    }
  }

  function extractPositionFromDOM() {
    const result = { found: false };

    const rows = document.querySelectorAll(
      '[class*="position"] tr, [class*="Position"] tr, ' +
      '[class*="openPositions"] tr, [class*="positions-table"] tr, ' +
      'table tr'
    );

    for (const row of rows) {
      const cells = row.querySelectorAll('td, div[class*="cell"], div[class*="Cell"]');
      const text = row.textContent || '';

      const hasSymbol = /BTCUSDT|ETHUSDT|BTC\/USDT|ETH\/USDT/i.test(text);
      const hasLiq = /liq|청산/i.test(text) || /\d+\.\d+/.test(text);

      if (!hasSymbol && !hasLiq) continue;

      const symbolMatch = text.match(/(BTC|ETH)(USDT|\/USDT)/i);
      if (symbolMatch) {
        result.symbol = symbolMatch[0].replace('/', '').toUpperCase();
      }

      const sideMatch = text.match(/\b(Long|Short|LONG|SHORT|Buy|Sell)\b/i);
      if (sideMatch) {
        result.side = sideMatch[0].toUpperCase();
        if (result.side === 'BUY') result.side = 'LONG';
        if (result.side === 'SELL') result.side = 'SHORT';
      }

      const leverageMatch = text.match(/(\d+)[xX×]/);
      if (leverageMatch) {
        result.leverage = parseInt(leverageMatch[1]);
      }

      const numbers = [];
      const numRegex = /(?<![a-zA-Z])(\d[\d,]*\.?\d*)(?![xX×%a-zA-Z])/g;
      let m;
      while ((m = numRegex.exec(text)) !== null) {
        const val = parseFloat(m[1].replace(/,/g, ''));
        if (val > 0 && !isNaN(val)) numbers.push(val);
      }

      if (numbers.length >= 2 && result.symbol) {
        result.found = true;
        break;
      }
    }

    if (!result.found) {
      result.found = scrapePositionPanel(result);
    }

    return result;
  }

  function scrapePositionPanel(result) {
    const selectors = [
      '[class*="positionInfo"]',
      '[class*="PositionInfo"]',
      '[class*="position-info"]',
      '[class*="contractDetail"]',
      '[class*="ContractDetail"]',
      '[class*="openPosition"]',
      '[class*="positionArea"]',
    ];

    let container = null;
    for (const sel of selectors) {
      container = document.querySelector(sel);
      if (container) break;
    }

    if (!container) {
      const allDivs = document.querySelectorAll('div');
      for (const div of allDivs) {
        const t = div.textContent || '';
        if (t.includes('Entry Price') && t.includes('Liq. Price') && t.length < 2000) {
          container = div;
          break;
        }
        if (t.includes('진입 가격') && t.includes('청산 가격') && t.length < 2000) {
          container = div;
          break;
        }
      }
    }

    if (!container) return false;

    const text = container.textContent || '';

    const symbolMatch = text.match(/(BTC|ETH)(USDT|\/USDT)/i);
    if (symbolMatch) result.symbol = symbolMatch[0].replace('/', '').toUpperCase();

    const sideMatch = text.match(/\b(Long|Short|LONG|SHORT)\b/i);
    if (sideMatch) result.side = sideMatch[0].toUpperCase();

    const leverageMatch = text.match(/(\d+)[xX×]/);
    if (leverageMatch) result.leverage = parseInt(leverageMatch[1]);

    const labelValuePairs = container.querySelectorAll('div, span, td');
    for (let i = 0; i < labelValuePairs.length; i++) {
      const el = labelValuePairs[i];
      const label = (el.textContent || '').trim().toLowerCase();
      const nextEl = labelValuePairs[i + 1];
      const nextText = nextEl ? (nextEl.textContent || '').trim() : '';

      if ((label.includes('entry price') || label.includes('진입 가격') || label === 'entry') && nextText) {
        const val = parsePrice(nextText);
        if (val) result.entryPrice = val;
      }

      if ((label.includes('liq. price') || label.includes('liquidation price') ||
           label.includes('청산 가격') || label === 'liq.') && nextText) {
        const val = parsePrice(nextText);
        if (val) result.liquidationPrice = val;
      }

      if ((label.includes('mark price') || label.includes('마크 가격') || label === 'mark') && nextText) {
        const val = parsePrice(nextText);
        if (val) result.markPrice = val;
      }

      if ((label.includes('size') || label.includes('수량') || label === 'qty') && nextText) {
        const val = parsePrice(nextText);
        if (val) result.size = val;
      }

      if ((label.includes('margin') || label.includes('증거금')) && nextText) {
        const val = parsePrice(nextText);
        if (val) result.margin = val;
      }

      if ((label.includes('pnl') || label.includes('손익')) && nextText) {
        const pnlMatch = nextText.match(/-?[\d,]+\.?\d*/);
        if (pnlMatch) result.pnl = parseFloat(pnlMatch[0].replace(/,/g, ''));
      }

      if ((label.includes('roe') || label.includes('수익률')) && nextText) {
        const roeMatch = nextText.match(/-?[\d.]+/);
        if (roeMatch) result.roe = parseFloat(roeMatch[0]);
      }
    }

    if (result.entryPrice || result.liquidationPrice) {
      result.found = true;
      return true;
    }

    const allText = text;
    const entryMatch = allText.match(/(?:Entry\s*(?:Price)?|진입\s*(?:가격)?)\s*:?\s*([\d,]+\.?\d*)/i);
    if (entryMatch) result.entryPrice = parseFloat(entryMatch[1].replace(/,/g, ''));

    const liqMatch = allText.match(/(?:Liq\.?\s*(?:Price)?|청산\s*(?:가격)?)\s*:?\s*([\d,]+\.?\d*)/i);
    if (liqMatch) result.liquidationPrice = parseFloat(liqMatch[1].replace(/,/g, ''));

    if (result.entryPrice || result.liquidationPrice) {
      result.found = true;
      return true;
    }

    return false;
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

  function initRiskWidget() {
    chrome.storage?.sync?.get(['liqRiskWidgetPosition'], (result) => {
      RiskWidgetRenderer.create(result.liqRiskWidgetPosition || null);
      startRiskWidgetLoop();
    });
  }

  function startRiskWidgetLoop() {
    if (riskWidgetTimer) clearInterval(riskWidgetTimer);
    fetchAndUpdateRiskWidget();
    riskWidgetTimer = setInterval(() => {
      if (isEnabled) fetchAndUpdateRiskWidget();
    }, 3000);
  }

  function fetchAndUpdateRiskWidget() {
    const currentPrice = extractCurrentPrice();
    if (!currentPrice || currentPrice <= 0) return;

    const liqPrice = userPosition.liquidationPrice || Math.round(currentPrice * 0.95 * 100) / 100;
    const side = userPosition.side || 'LONG';
    const symbol = extractSymbolFromPage() || 'BTCUSDT';

    fetchCascadeRisk(symbol, currentPrice, liqPrice, side);
  }

  function extractSymbolFromPage() {
    const urlMatch = window.location.pathname.match(/\/futures\/(\w+)/i);
    if (urlMatch) return urlMatch[1].toUpperCase();

    const titleMatch = document.title.match(/(BTC|ETH|BNB|SOL|XRP|DOGE|ADA)\w*USDT/i);
    if (titleMatch) return titleMatch[0].toUpperCase();

    return null;
  }

  function fetchCascadeRisk(symbol, currentPrice, liqPrice, side) {
    const url = `${BACKEND_BASE_URL}/api/risk/cascade?symbol=${symbol}&currentPrice=${currentPrice}&userLiquidationPrice=${liqPrice}&positionSide=${side}`;

    fetch(url)
      .then(res => {
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json();
      })
      .then(report => {
        console.log('[LiqHeatmap] Cascade risk:', report);
        RiskWidgetRenderer.update(report);
      })
      .catch(err => {
        console.debug('[LiqHeatmap] Risk API unavailable:', err.message);
      });
  }

  bootstrap();
})();
