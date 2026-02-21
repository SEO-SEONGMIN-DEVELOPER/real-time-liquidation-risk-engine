package com.liquidation.riskengine.domain.service;

import com.liquidation.riskengine.domain.model.CascadeRiskReport;
import com.liquidation.riskengine.domain.model.CascadeRiskReport.DensityLevel;
import com.liquidation.riskengine.domain.model.CascadeRiskReport.LiqCluster;
import com.liquidation.riskengine.domain.model.CascadeRiskReport.RiskLevel;
import com.liquidation.riskengine.domain.model.LiquidationEvent;
import com.liquidation.riskengine.domain.model.OpenInterestSnapshot;
import com.liquidation.riskengine.domain.model.OrderBookSnapshot;
import com.liquidation.riskengine.domain.model.OrderBookSnapshot.PriceLevel;
import com.liquidation.riskengine.domain.service.LiquidationPriceCalculator.EstimatedLiquidation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class CascadeRiskCalculator {

    private final LiquidationPriceCalculator liquidationPriceCalculator;
    private final CascadeRiskProperties cascadeProps;

    private static final MathContext MC = new MathContext(8, RoundingMode.HALF_UP);
    private static final Duration RECENT_LIQ_WINDOW = Duration.ofMinutes(30);

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(0);
    private static final ExecutorService ANALYSIS_POOL = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "risk-analysis-" + THREAD_COUNTER.incrementAndGet());
        t.setDaemon(true);
        return t;
    });

    public CascadeRiskReport fullAnalysis(
            BigDecimal currentPrice,
            BigDecimal userLiquidationPrice,
            String positionSide,
            String symbol,
            RiskStateManager state) {

        CascadeRiskReport report = analyzeDistance(currentPrice, userLiquidationPrice, positionSide, symbol);

        OrderBookSnapshot orderBook = state.getLatestOrderBook(symbol);
        OpenInterestSnapshot latestOi = state.getLatestOpenInterest(symbol);
        BigDecimal totalOi = latestOi != null ? latestOi.getOpenInterest() : null;
        List<LiquidationEvent> recentLiqs = state.getRecentLiquidations(symbol, RECENT_LIQ_WINDOW);

        CompletableFuture<Void> densityFuture = CompletableFuture.runAsync(() -> {
            if (orderBook != null) {
                analyzeOrderBookDensity(report, orderBook);
            }
        }, ANALYSIS_POOL);

        CompletableFuture<Void> clusterFuture = CompletableFuture.runAsync(
                () -> mapLiquidationClusters(report, totalOi), ANALYSIS_POOL);

        CompletableFuture<Void> pressureFuture = CompletableFuture.runAsync(
                () -> analyzeMarketPressure(report, latestOi, recentLiqs, orderBook, positionSide), ANALYSIS_POOL);

        CompletableFuture.allOf(densityFuture, clusterFuture, pressureFuture).join();

        synthesize(report);

        return report;
    }

    public CascadeRiskReport analyzeDistance(
            BigDecimal currentPrice,
            BigDecimal userLiquidationPrice,
            String positionSide,
            String symbol) {

        BigDecimal distance = currentPrice.subtract(userLiquidationPrice).abs();

        double distancePercent = distance
                .divide(currentPrice, MC)
                .multiply(BigDecimal.valueOf(100), MC)
                .setScale(4, RoundingMode.HALF_UP)
                .doubleValue();

        String direction = resolveDirection(positionSide);

        BigDecimal priceRangeLow = currentPrice.min(userLiquidationPrice);
        BigDecimal priceRangeHigh = currentPrice.max(userLiquidationPrice);

        CascadeRiskReport report = CascadeRiskReport.builder()
                .symbol(symbol.toUpperCase())
                .currentPrice(currentPrice)
                .userLiquidationPrice(userLiquidationPrice)
                .positionSide(positionSide.toUpperCase())
                .distance(distance)
                .distancePercent(distancePercent)
                .direction(direction)
                .priceRangeLow(priceRangeLow)
                .priceRangeHigh(priceRangeHigh)
                .timestamp(Instant.now().toEpochMilli())
                .build();

        log.info("{} | side={} | 현재가={} | 청산가={} | 거리={} ({}%) | 방향={} | 구간=[{} ~ {}]",
                symbol, positionSide, currentPrice, userLiquidationPrice,
                distance, String.format("%.2f", distancePercent),
                direction, priceRangeLow, priceRangeHigh);

        return report;
    }

    public CascadeRiskReport analyzeOrderBookDensity(CascadeRiskReport report, OrderBookSnapshot orderBook) {
        BigDecimal low = report.getPriceRangeLow();
        BigDecimal high = report.getPriceRangeHigh();

        boolean isLong = "LONG".equalsIgnoreCase(report.getPositionSide());
        List<PriceLevel> relevantSide = isLong ? orderBook.getBids() : orderBook.getAsks();

        BigDecimal depthBetween = BigDecimal.ZERO;
        BigDecimal notionalBetween = BigDecimal.ZERO;
        int levelCount = 0;

        if (relevantSide != null) {
            for (PriceLevel level : relevantSide) {
                if (level.getPrice().compareTo(low) >= 0 && level.getPrice().compareTo(high) <= 0) {
                    depthBetween = depthBetween.add(level.getQuantity());
                    notionalBetween = notionalBetween.add(
                            level.getPrice().multiply(level.getQuantity(), MC));
                    levelCount++;
                }
            }
        }

        BigDecimal totalQuantity = isLong
                ? orderBook.getBidTotalQuantity()
                : orderBook.getAskTotalQuantity();

        double depthRatio = 0.0;
        if (totalQuantity != null && totalQuantity.compareTo(BigDecimal.ZERO) > 0) {
            depthRatio = depthBetween
                    .divide(totalQuantity, MC)
                    .multiply(BigDecimal.valueOf(100), MC)
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        report.setDepthBetween(depthBetween);
        report.setNotionalBetween(notionalBetween.setScale(2, RoundingMode.HALF_UP));
        report.setLevelCount(levelCount);
        report.setDepthRatio(depthRatio);

        log.info("{} | 스캔={} | 구간 물량={} | 명목가치={} USDT | 호가 {}단계 | 비율={}%",
                report.getSymbol(),
                isLong ? "bids(매수벽)" : "asks(매도벽)",
                depthBetween.toPlainString(),
                notionalBetween.setScale(0, RoundingMode.HALF_UP).toPlainString(),
                levelCount,
                String.format("%.2f", depthRatio));

        return report;
    }

    public CascadeRiskReport mapLiquidationClusters(CascadeRiskReport report, BigDecimal totalOi) {
        BigDecimal low = report.getPriceRangeLow();
        BigDecimal high = report.getPriceRangeHigh();
        BigDecimal currentPrice = report.getCurrentPrice();
        boolean isLong = "LONG".equalsIgnoreCase(report.getPositionSide());

        List<EstimatedLiquidation> distribution =
                liquidationPriceCalculator.estimateDistribution(currentPrice, report.getSymbol(), totalOi);

        List<LiqCluster> clustersInPath = new ArrayList<>();
        BigDecimal estimatedLiqVolume = BigDecimal.ZERO;

        for (EstimatedLiquidation est : distribution) {
            BigDecimal liqPrice = isLong ? est.longLiquidationPrice() : est.shortLiquidationPrice();

            if (liqPrice.compareTo(low) >= 0 && liqPrice.compareTo(high) <= 0) {
                double distFromCurrent = currentPrice.subtract(liqPrice).abs()
                        .divide(currentPrice, MC)
                        .multiply(BigDecimal.valueOf(100), MC)
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue();

                clustersInPath.add(LiqCluster.builder()
                        .leverage(est.leverage())
                        .price(liqPrice)
                        .weight(est.weight())
                        .estimatedVolume(est.estimatedVolume())
                        .estimatedNotional(est.estimatedNotional())
                        .distanceFromCurrentPercent(distFromCurrent)
                        .build());

                estimatedLiqVolume = estimatedLiqVolume.add(
                        est.estimatedVolume() != null ? est.estimatedVolume() : BigDecimal.ZERO, MC);
            }
        }

        report.setClustersInPath(clustersInPath);
        report.setOverlappingTierCount(clustersInPath.size());
        report.setEstimatedLiqVolume(estimatedLiqVolume.setScale(4, RoundingMode.HALF_UP));

        log.info("{} | 구간 내 청산 클러스터 {}개 | 추정 물량={} BTC | 클러스터: {}",
                report.getSymbol(),
                clustersInPath.size(),
                estimatedLiqVolume.setScale(4, RoundingMode.HALF_UP).toPlainString(),
                clustersInPath.stream()
                        .map(c -> String.format("%dx(%.2f%%, vol=%s)",
                                c.getLeverage(), c.getDistanceFromCurrentPercent(),
                                c.getEstimatedVolume() != null ? c.getEstimatedVolume().toPlainString() : "N/A"))
                        .toList());

        return report;
    }

    public CascadeRiskReport analyzeMarketPressure(
            CascadeRiskReport report,
            OpenInterestSnapshot latestOi,
            List<LiquidationEvent> recentLiqs,
            OrderBookSnapshot orderBook,
            String positionSide) {

        int oiScore = calcOiPressureScore(latestOi, positionSide, report.getCurrentPrice());
        int liqScore = calcLiqIntensityScore(recentLiqs);
        int imbScore = calcImbalanceScore(orderBook, positionSide);
        int total = oiScore + liqScore + imbScore;

        report.setOiPressureScore(oiScore);
        report.setLiqIntensityScore(liqScore);
        report.setImbalanceScore(imbScore);
        report.setMarketPressureTotal(total);

        log.info("{} | OI압력={}/20 | 청산강도={}/20 | 불균형={}/20 | 시장압력 합계={}/60",
                report.getSymbol(), oiScore, liqScore, imbScore, total);

        return report;
    }

    private int calcOiPressureScore(OpenInterestSnapshot oi, String positionSide, BigDecimal currentPrice) {
        if (oi == null || oi.getChangePercent() == null) return 5;

        double changePct = oi.getChangePercent().doubleValue();
        double absChange = Math.abs(changePct);

        int baseScore;
        if (absChange >= 5.0) baseScore = 20;
        else if (absChange >= 3.0) baseScore = 16;
        else if (absChange >= 2.0) baseScore = 12;
        else if (absChange >= 1.0) baseScore = 8;
        else if (absChange >= 0.5) baseScore = 4;
        else baseScore = 0;

        boolean oiIncreasing = changePct > 0;

        if (oiIncreasing && absChange >= 1.0) {
            baseScore = Math.min(20, baseScore + 3);
        }

        return baseScore;
    }

    private int calcLiqIntensityScore(List<LiquidationEvent> recentLiqs) {
        if (recentLiqs == null || recentLiqs.isEmpty()) return 0;

        int count = recentLiqs.size();
        BigDecimal totalNotional = recentLiqs.stream()
                .map(LiquidationEvent::getNotionalValue)
                .filter(n -> n != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int countScore;
        if (count >= 50) countScore = 10;
        else if (count >= 30) countScore = 8;
        else if (count >= 15) countScore = 6;
        else if (count >= 5) countScore = 3;
        else countScore = 1;

        double notionalM = totalNotional.doubleValue() / 1_000_000.0;
        int notionalScore;
        if (notionalM >= 50) notionalScore = 10;
        else if (notionalM >= 20) notionalScore = 8;
        else if (notionalM >= 10) notionalScore = 6;
        else if (notionalM >= 5) notionalScore = 4;
        else if (notionalM >= 1) notionalScore = 2;
        else notionalScore = 0;

        return Math.min(20, countScore + notionalScore);
    }

    private int calcImbalanceScore(OrderBookSnapshot ob, String positionSide) {
        if (ob == null) return 5;

        BigDecimal bidQty = ob.getBidTotalQuantity() != null ? ob.getBidTotalQuantity() : BigDecimal.ZERO;
        BigDecimal askQty = ob.getAskTotalQuantity() != null ? ob.getAskTotalQuantity() : BigDecimal.ZERO;
        BigDecimal total = bidQty.add(askQty);

        if (total.compareTo(BigDecimal.ZERO) == 0) return 10;
        double bidRatio = bidQty.doubleValue() / total.doubleValue();
        double askRatio = 1.0 - bidRatio;

        boolean isLong = "LONG".equalsIgnoreCase(positionSide);

        double dangerousRatio = isLong ? askRatio : bidRatio;
        double directionalImbalance = Math.max(0, (dangerousRatio - 0.5) * 2);

        if (directionalImbalance >= 0.7) return 20;
        if (directionalImbalance >= 0.5) return 15;
        if (directionalImbalance >= 0.3) return 10;
        if (directionalImbalance >= 0.15) return 5;
        return 0;
    }

    public CascadeRiskReport synthesize(CascadeRiskReport report) {
        double densityScore = calcDensityScore(report);
        DensityLevel densityLevel = DensityLevel.fromScore(densityScore);

        CascadeRiskProperties.Synthesis syn = cascadeProps.getSynthesis();
        double marketPressureNorm = report.getMarketPressureTotal() * (100.0 / 60.0);
        double reachProb = densityScore * syn.getDensityWeight() + marketPressureNorm * syn.getPressureWeight();
        reachProb = Math.max(0, Math.min(100, reachProb));

        RiskLevel computed = RiskLevel.fromScore(reachProb);
        RiskLevel minLevel = distanceFloor(report.getDistancePercent());
        RiskLevel riskLevel = computed.ordinal() >= minLevel.ordinal() ? computed : minLevel;

        report.setDensityScore(Math.round(densityScore * 10.0) / 10.0);
        report.setDensityLevel(densityLevel);
        report.setCascadeReachProbability(Math.round(reachProb * 10.0) / 10.0);
        report.setRiskLevel(riskLevel);

        log.info("{} | densityScore={} ({}) | marketPressure={}/60 | reachProb={}% | riskLevel={} (floor={})",
                report.getSymbol(),
                String.format("%.1f", densityScore), densityLevel,
                report.getMarketPressureTotal(),
                String.format("%.1f", reachProb),
                riskLevel, minLevel);

        return report;
    }

    private RiskLevel distanceFloor(double distPct) {
        CascadeRiskProperties.Floor f = cascadeProps.getFloor();
        if (distPct <= f.getCriticalDistancePct()) return RiskLevel.CRITICAL;
        if (distPct <= f.getHighDistancePct()) return RiskLevel.HIGH;
        if (distPct <= f.getMediumDistancePct()) return RiskLevel.MEDIUM;
        return RiskLevel.LOW;
    }

    private double calcDensityScore(CascadeRiskReport r) {
        double distPct = r.getDistancePercent();
        double distScore = scoreDist(distPct);
        double depthScore = scoreDepthRatio(r.getDepthRatio());
        double levelScore = scoreLevelCount(r.getLevelCount());
        double clusterScore = scoreClusterOverlap(r.getOverlappingTierCount());
        double notionalScore = scoreNotional(r.getNotionalBetween());

        double threshold = cascadeProps.getProximityThresholdPct();
        double proximityFactor = Math.min(1.0, distPct / threshold);
        depthScore = depthScore * proximityFactor + 100 * (1 - proximityFactor);
        levelScore = levelScore * proximityFactor + 100 * (1 - proximityFactor);
        notionalScore = notionalScore * proximityFactor + 100 * (1 - proximityFactor);

        CascadeRiskProperties.Weights w = cascadeProps.getWeights();
        double baseDistWeight = w.getDistance();
        double dynamicDistWeight = baseDistWeight + 0.20 * (1 - proximityFactor);

        double otherTotal = w.getDepth() + w.getLevel() + w.getCluster() + w.getNotional();
        double otherScale = (1.0 - dynamicDistWeight) / otherTotal;

        return distScore * dynamicDistWeight
                + depthScore * w.getDepth() * otherScale
                + levelScore * w.getLevel() * otherScale
                + clusterScore * w.getCluster() * otherScale
                + notionalScore * w.getNotional() * otherScale;
    }

    private double scoreDist(double distPct) {
        if (distPct <= 1) return 100;
        if (distPct <= 2) return 85;
        if (distPct <= 3) return 70;
        if (distPct <= 5) return 50;
        if (distPct <= 8) return 30;
        if (distPct <= 15) return 15;
        return 5;
    }

    private double scoreDepthRatio(double depthRatio) {
        if (depthRatio < 3) return 100;
        if (depthRatio < 8) return 80;
        if (depthRatio < 15) return 60;
        if (depthRatio < 30) return 40;
        if (depthRatio < 50) return 20;
        return 5;
    }

    private double scoreLevelCount(int levels) {
        if (levels <= 1) return 100;
        if (levels <= 3) return 75;
        if (levels <= 5) return 50;
        if (levels <= 10) return 30;
        return 10;
    }

    private double scoreClusterOverlap(int tiers) {
        if (tiers >= 5) return 100;
        if (tiers >= 3) return 75;
        if (tiers >= 2) return 50;
        if (tiers >= 1) return 30;
        return 0;
    }

    private double scoreNotional(BigDecimal notional) {
        if (notional == null) return 50;
        double million = notional.doubleValue() / 1_000_000.0;
        if (million < 0.5) return 100;
        if (million < 2) return 75;
        if (million < 5) return 50;
        if (million < 20) return 25;
        return 5;
    }

    private String resolveDirection(String positionSide) {
        if ("LONG".equalsIgnoreCase(positionSide)) {
            return "DOWN";
        }
        if ("SHORT".equalsIgnoreCase(positionSide)) {
            return "UP";
        }
        return "UNKNOWN";
    }
}
