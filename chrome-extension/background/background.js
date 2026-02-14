chrome.runtime.onInstalled.addListener(() => {
  chrome.storage.sync.set({
    liqHeatmapEnabled: true,
    liqHeatmapPosition: null,
    liqHeatmapSettings: {
      priceRangePercent: 10,
      priceBucketCount: 60,
      overlayWidth: 180,
      panelHeight: 400,
      opacity: 0.95,
      updateIntervalMs: 1000,
    },
  });
});

chrome.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
  if (changeInfo.status === 'complete' && tab.url) {
    const isBinanceFutures = tab.url.includes('binance') && tab.url.includes('futures');

    chrome.action.setBadgeText({
      tabId,
      text: isBinanceFutures ? 'ON' : '',
    });
    chrome.action.setBadgeBackgroundColor({
      tabId,
      color: '#F0B90B',
    });
  }
});
