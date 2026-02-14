document.addEventListener('DOMContentLoaded', () => {
  const toggleEl = document.getElementById('toggle-enabled');
  const priceRangeEl = document.getElementById('price-range');
  const bucketCountEl = document.getElementById('bucket-count');
  const overlayWidthEl = document.getElementById('overlay-width');
  const opacityEl = document.getElementById('opacity');

  chrome.storage.sync.get(['liqHeatmapEnabled', 'liqHeatmapSettings'], (result) => {
    if (result.liqHeatmapEnabled !== undefined) {
      toggleEl.checked = result.liqHeatmapEnabled;
    }
    if (result.liqHeatmapSettings) {
      const s = result.liqHeatmapSettings;
      if (s.priceRangePercent) priceRangeEl.value = s.priceRangePercent;
      if (s.priceBucketCount) bucketCountEl.value = s.priceBucketCount;
      if (s.overlayWidth) overlayWidthEl.value = s.overlayWidth;
      if (s.opacity) opacityEl.value = s.opacity;
    }
    updateLabels();
  });

  toggleEl.addEventListener('change', () => {
    const enabled = toggleEl.checked;
    chrome.storage.sync.set({ liqHeatmapEnabled: enabled });
    sendToContent({ type: 'TOGGLE_HEATMAP', enabled });
  });

  [priceRangeEl, bucketCountEl, overlayWidthEl, opacityEl].forEach((el) => {
    el.addEventListener('input', () => {
      updateLabels();
      saveAndSendSettings();
    });
  });

  function updateLabels() {
    document.getElementById('price-range-val').textContent = priceRangeEl.value + '%';
    document.getElementById('bucket-count-val').textContent = bucketCountEl.value;
    document.getElementById('overlay-width-val').textContent = overlayWidthEl.value;
    document.getElementById('opacity-val').textContent = Math.round(opacityEl.value * 100) + '%';
  }

  function saveAndSendSettings() {
    const settings = {
      priceRangePercent: parseInt(priceRangeEl.value),
      priceBucketCount: parseInt(bucketCountEl.value),
      overlayWidth: parseInt(overlayWidthEl.value),
      opacity: parseFloat(opacityEl.value),
    };
    chrome.storage.sync.set({ liqHeatmapSettings: settings });
    sendToContent({ type: 'UPDATE_SETTINGS', settings });
  }

  function sendToContent(msg) {
    chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
      if (tabs[0]?.id) {
        chrome.tabs.sendMessage(tabs[0].id, msg).catch(() => {});
      }
    });
  }
});
