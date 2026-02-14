const HistogramRenderer = (() => {
  const C = {
    bg:             '#181A20',
    cardBg:         '#1E2329',
    border:         '#2B3139',
    gridLine:       '#2B3139',
    textPrimary:    '#EAECEF',
    textSecondary:  '#848E9C',
    textTertiary:   '#5E6673',
    red:            '#F6465D',
    redDim:         'rgba(246,70,93,0.25)',
    green:          '#0ECB81',
    greenDim:       'rgba(14,203,129,0.25)',
    priceLabelBg:   '#F0B90B',
    priceLabelText: '#181A20',
    priceLineDash:  'rgba(240,185,11,0.45)',
  };

  const FONT = "'BinancePlex', -apple-system, 'Helvetica Neue', Arial, sans-serif";
  const PRICE_AXIS_W = 58;
  const PAD_TOP = 4;
  const PAD_BOTTOM = 4;

  let canvas = null;
  let ctx = null;

  function init(canvasEl) {
    canvas = canvasEl;
    ctx = canvas.getContext('2d');
  }

  function render(data) {
    if (!ctx || !canvas) return;

    const { buckets, minPrice, maxPrice, currentPrice } = data;

    const dpr = window.devicePixelRatio || 1;
    const w = canvas.clientWidth;
    const h = canvas.clientHeight;
    if (w <= 0 || h <= 0) return;

    canvas.width = w * dpr;
    canvas.height = h * dpr;
    ctx.scale(dpr, dpr);

    ctx.fillStyle = C.bg;
    ctx.fillRect(0, 0, w, h);

    if (!buckets || buckets.length === 0) return;

    const axisX = w - PRICE_AXIS_W;
    const chartW = axisX;
    const chartTop = PAD_TOP;
    const chartH = h - PAD_TOP - PAD_BOTTOM;
    const n = buckets.length;
    const barH = chartH / n;

    ctx.fillStyle = C.bg;
    ctx.fillRect(axisX, 0, PRICE_AXIS_W, h);

    ctx.strokeStyle = C.border;
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(axisX + 0.5, 0);
    ctx.lineTo(axisX + 0.5, h);
    ctx.stroke();

    drawPriceAxis(w, h, axisX, chartTop, chartH, n, barH, minPrice, maxPrice, currentPrice);
    drawBars(chartW, chartTop, chartH, n, barH, buckets);
    drawCurrentPrice(w, axisX, chartTop, chartH, currentPrice, minPrice, maxPrice);
  }

  function drawBars(chartW, chartTop, chartH, n, barH, buckets) {
    const maxBarW = chartW - 4;

    buckets.forEach((b, i) => {
      const y = chartTop + chartH - (i + 1) * barH;
      const gap = 0.5;

      if (b.longVol > 0.01) {
        const bw = b.longVol * maxBarW;
        ctx.fillStyle = C.red;
        ctx.fillRect(2, y + gap, bw, barH - gap * 2);
      }

      if (b.shortVol > 0.01) {
        const bw = b.shortVol * maxBarW;
        ctx.fillStyle = C.green;
        ctx.fillRect(2, y + gap, bw, barH - gap * 2);
      }
    });
  }

  function drawPriceAxis(w, h, axisX, chartTop, chartH, n, barH, minPrice, maxPrice, currentPrice) {
    const priceRange = maxPrice - minPrice;
    const labelCount = Math.max(3, Math.floor(chartH / 50));
    const step = Math.floor(n / labelCount);

    ctx.font = `11px ${FONT}`;
    ctx.textAlign = 'left';

    for (let i = step; i < n; i += step) {
      const y = chartTop + chartH - i * barH;
      const price = minPrice + (i / n) * priceRange;

      if (Math.abs(price - currentPrice) / currentPrice < 0.012) continue;

      ctx.strokeStyle = C.gridLine;
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.moveTo(0, Math.round(y) + 0.5);
      ctx.lineTo(axisX, Math.round(y) + 0.5);
      ctx.stroke();

      ctx.fillStyle = C.textSecondary;
      ctx.fillText(fmtPrice(price), axisX + 6, y + 4);
    }
  }

  function drawCurrentPrice(w, axisX, chartTop, chartH, currentPrice, minPrice, maxPrice) {
    const ratio = (currentPrice - minPrice) / (maxPrice - minPrice);
    const y = Math.round(chartTop + chartH - ratio * chartH);

    ctx.setLineDash([4, 3]);
    ctx.strokeStyle = C.priceLineDash;
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(0, y + 0.5);
    ctx.lineTo(axisX, y + 0.5);
    ctx.stroke();
    ctx.setLineDash([]);

    const label = fmtPriceFull(currentPrice);
    ctx.font = `bold 11px ${FONT}`;
    const tw = ctx.measureText(label).width;
    const labelW = tw + 10;
    const labelH = 18;
    const labelX = axisX + 1;
    const labelY = y - labelH / 2;

    ctx.fillStyle = C.priceLabelBg;
    ctx.fillRect(labelX, labelY, PRICE_AXIS_W - 2, labelH);

    ctx.beginPath();
    ctx.moveTo(labelX, y);
    ctx.lineTo(labelX - 4, y - 4);
    ctx.lineTo(labelX - 4, y + 4);
    ctx.closePath();
    ctx.fill();

    ctx.fillStyle = C.priceLabelText;
    ctx.textAlign = 'center';
    ctx.fillText(label, axisX + PRICE_AXIS_W / 2, y + 4);
    ctx.textAlign = 'left';
  }

  function fmtPrice(p) {
    if (p >= 1000) return p.toFixed(1).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
    if (p >= 1) return p.toFixed(2);
    return p.toFixed(4);
  }

  function fmtPriceFull(p) {
    if (p >= 1000) return p.toFixed(1).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
    if (p >= 1) return p.toFixed(2);
    return p.toFixed(4);
  }

  return { init, render };
})();
