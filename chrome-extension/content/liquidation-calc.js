const LiquidationCalc = (() => {
  const LEVERAGE_TIERS = [
    { leverage: 100, weight: 0.05 },
    { leverage:  50, weight: 0.15 },
    { leverage:  25, weight: 0.25 },
    { leverage:  20, weight: 0.15 },
    { leverage:  10, weight: 0.20 },
    { leverage:   5, weight: 0.12 },
    { leverage:   3, weight: 0.05 },
    { leverage:   2, weight: 0.03 },
  ];

  function generate(currentPrice, opts = {}) {
    const bucketCount = opts.priceBucketCount || 60;
    const rangePercent = opts.priceRangePercent || 10;

    const minPrice = currentPrice * (1 - rangePercent / 100);
    const maxPrice = currentPrice * (1 + rangePercent / 100);
    const step = (maxPrice - minPrice) / bucketCount;

    const buckets = Array.from({ length: bucketCount }, (_, i) => ({
      price: minPrice + step * (i + 0.5), 
      longVol: 0,
      shortVol: 0,
    }));

    LEVERAGE_TIERS.forEach(({ leverage, weight }) => {
      const longLiqPrice = currentPrice * (1 - 1 / leverage);
      const longIdx = priceToBucketIndex(longLiqPrice, minPrice, step, bucketCount);
      if (longIdx >= 0) {
        spreadVolume(buckets, longIdx, weight, 'longVol', bucketCount, leverage);
      }

      const shortLiqPrice = currentPrice * (1 + 1 / leverage);
      const shortIdx = priceToBucketIndex(shortLiqPrice, minPrice, step, bucketCount);
      if (shortIdx >= 0) {
        spreadVolume(buckets, shortIdx, weight, 'shortVol', bucketCount, leverage);
      }
    });

    const maxVol = Math.max(
      ...buckets.map((b) => Math.max(b.longVol, b.shortVol)),
      0.001
    );
    buckets.forEach((b) => {
      b.longVol /= maxVol;
      b.shortVol /= maxVol;
    });

    return { buckets, minPrice, maxPrice, currentPrice };
  }

  function priceToBucketIndex(price, minPrice, step, bucketCount) {
    const idx = Math.floor((price - minPrice) / step);
    if (idx < 0 || idx >= bucketCount) return -1;
    return idx;
  }

  function spreadVolume(buckets, centerIdx, weight, field, bucketCount, leverage) {
    const spread = Math.max(1, Math.floor(bucketCount / (leverage * 0.8)));

    for (let offset = -spread; offset <= spread; offset++) {
      const idx = centerIdx + offset;
      if (idx < 0 || idx >= bucketCount) continue;

      const distance = Math.abs(offset) / (spread || 1);
      const gaussian = Math.exp(-3 * distance * distance);
      buckets[idx][field] += weight * gaussian;
    }
  }

  return { generate, LEVERAGE_TIERS };
})();
