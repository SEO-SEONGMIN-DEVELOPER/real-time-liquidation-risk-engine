package com.liquidation.riskengine.domain.service.liquidation;

import com.liquidation.riskengine.domain.model.LiquidationEvent;
import com.liquidation.riskengine.domain.model.MaintenanceMarginTier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class LiquidationClusterMap {

    private static final Map<Integer, Double> PRIOR_WEIGHTS = Map.ofEntries(
            Map.entry(125, 0.03),
            Map.entry(100, 0.05),
            Map.entry(75,  0.07),
            Map.entry(50,  0.18),
            Map.entry(25,  0.22),
            Map.entry(20,  0.15),
            Map.entry(10,  0.16),
            Map.entry(5,   0.08),
            Map.entry(4,   0.03),
            Map.entry(3,   0.02),
            Map.entry(2,   0.01)
    );

    private static final double BUCKET_WIDTH_PCT = 0.2;
    private static final double DECAY_FACTOR = 0.995;
    private static final long DECAY_INTERVAL_MS = 60_000;
    private static final BigDecimal MIN_CLUSTER_VOLUME = new BigDecimal("0.00001");
    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);

    private final Map<String, ConcurrentHashMap<BigDecimal, BigDecimal>> longClusters = new ConcurrentHashMap<>();
    private final Map<String, ConcurrentHashMap<BigDecimal, BigDecimal>> shortClusters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> lastDecayTime = new ConcurrentHashMap<>();

    public void recordOiIncrease(String symbol, BigDecimal markPrice, BigDecimal oiDelta) {
        if (symbol == null || markPrice == null || oiDelta == null) return;
        if (oiDelta.compareTo(BigDecimal.ZERO) <= 0) return;

        String key = symbol.toUpperCase();
        List<MaintenanceMarginTier> tiers = MaintenanceMarginTier.getTiersForSymbol(key);

        ConcurrentHashMap<BigDecimal, BigDecimal> longs =
                longClusters.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
        ConcurrentHashMap<BigDecimal, BigDecimal> shorts =
                shortClusters.computeIfAbsent(key, k -> new ConcurrentHashMap<>());

        for (MaintenanceMarginTier tier : tiers) {
            int leverage = tier.maxLeverage();
            if (leverage <= 1) continue;

            Double weight = PRIOR_WEIGHTS.get(leverage);
            if (weight == null || weight <= 0) continue;

            BigDecimal imr = BigDecimal.ONE.divide(BigDecimal.valueOf(leverage), MC);
            BigDecimal mmr = tier.maintenanceMarginRate();
            BigDecimal volume = oiDelta.multiply(BigDecimal.valueOf(weight), MC);

            BigDecimal longLiqPrice = roundToBucket(
                    markPrice.multiply(BigDecimal.ONE.subtract(imr, MC).add(mmr, MC), MC));
            BigDecimal shortLiqPrice = roundToBucket(
                    markPrice.multiply(BigDecimal.ONE.add(imr, MC).subtract(mmr, MC), MC));

            longs.merge(longLiqPrice, volume, BigDecimal::add);
            shorts.merge(shortLiqPrice, volume, BigDecimal::add);
        }

        applyDecayIfNeeded(key);

        log.debug("[LiqCluster] {} | OI+ markPrice={} oiDelta={}", key,
                markPrice.toPlainString(), oiDelta.toPlainString());
    }

    public void recordLiquidation(LiquidationEvent event) {
        if (event == null) return;

        String key = event.getSymbol().toUpperCase();
        BigDecimal liqPrice = event.getAveragePrice() != null
                ? event.getAveragePrice() : event.getPrice();
        BigDecimal notional = event.getNotionalValue();
        if (liqPrice == null || notional == null
                || liqPrice.compareTo(BigDecimal.ZERO) <= 0) return;

        BigDecimal bucket = roundToBucket(liqPrice);
        BigDecimal volume = notional.divide(liqPrice, MC);

        Map<BigDecimal, BigDecimal> targetMap = event.isSell()
                ? longClusters.get(key)
                : shortClusters.get(key);

        if (targetMap == null) return;

        targetMap.merge(bucket, volume, (existing, delta) ->
                existing.subtract(delta).max(BigDecimal.ZERO));

        log.debug("[LiqCluster] {} | 청산({}) bucket={} vol={}", key,
                event.getSide(), bucket.toPlainString(), volume.toPlainString());
    }

    public Map<BigDecimal, BigDecimal> getLongClusters(String symbol) {
        if (symbol == null) return Map.of();
        Map<BigDecimal, BigDecimal> map = longClusters.get(symbol.toUpperCase());
        return map != null ? Collections.unmodifiableMap(map) : Map.of();
    }

    public Map<BigDecimal, BigDecimal> getShortClusters(String symbol) {
        if (symbol == null) return Map.of();
        Map<BigDecimal, BigDecimal> map = shortClusters.get(symbol.toUpperCase());
        return map != null ? Collections.unmodifiableMap(map) : Map.of();
    }

    public boolean hasData(String symbol) {
        if (symbol == null) return false;
        Map<BigDecimal, BigDecimal> map = longClusters.get(symbol.toUpperCase());
        return map != null && !map.isEmpty();
    }

    private BigDecimal roundToBucket(BigDecimal price) {
        double p = price.doubleValue();
        double bucketSize = Math.max(1.0, p * BUCKET_WIDTH_PCT / 100.0);
        double rounded = Math.round(p / bucketSize) * bucketSize;
        return BigDecimal.valueOf(rounded).setScale(1, RoundingMode.HALF_UP);
    }

    private void applyDecayIfNeeded(String symbol) {
        AtomicLong lastTime = lastDecayTime.computeIfAbsent(
                symbol, k -> new AtomicLong(System.currentTimeMillis()));
        long now = System.currentTimeMillis();
        long elapsed = now - lastTime.get();
        if (elapsed < DECAY_INTERVAL_MS) return;

        int periods = (int) (elapsed / DECAY_INTERVAL_MS);
        double multiplier = Math.pow(DECAY_FACTOR, periods);

        applyDecayToMap(longClusters.get(symbol), multiplier);
        applyDecayToMap(shortClusters.get(symbol), multiplier);

        lastTime.set(now);
    }

    private void applyDecayToMap(Map<BigDecimal, BigDecimal> map, double multiplier) {
        if (map == null) return;
        map.replaceAll((price, vol) -> vol.multiply(BigDecimal.valueOf(multiplier)));
        map.values().removeIf(vol -> vol.compareTo(MIN_CLUSTER_VOLUME) < 0);
    }
}
