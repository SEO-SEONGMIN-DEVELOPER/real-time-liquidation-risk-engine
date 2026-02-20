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

@Slf4j
@Service
@RequiredArgsConstructor
public class CascadeRiskCalculator {

    private final LiquidationPriceCalculator liquidationPriceCalculator;

    private static final MathContext MC = new MathContext(8, RoundingMode.HALF_UP);
    private static final Duration RECENT_LIQ_WINDOW = Duration.ofMinutes(30);

    public CascadeRiskReport fullAnalysis(
            BigDecimal currentPrice,
            BigDecimal userLiquidationPrice,
            String positionSide,
            String symbol,
            RiskStateManager state) {

        CascadeRiskReport report = analyzeDistance(currentPrice, userLiquidationPrice, positionSide, symbol);

        OrderBookSnapshot orderBook = state.getLatestOrderBook(symbol);
        if (orderBook != null) {
            analyzeOrderBookDensity(report, orderBook);
        }

        OpenInterestSnapshot latestOi = state.getLatestOpenInterest(symbol);
        BigDecimal totalOi = latestOi != null ? latestOi.getOpenInterest() : null;
        mapLiquidationClusters(report, totalOi);

        List<LiquidationEvent> recentLiqs = state.getRecentLiquidations(symbol, RECENT_LIQ_WINDOW);
        analyzeMarketPressure(report, latestOi, recentLiqs, orderBook);

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
            OrderBookSnapshot orderBook) {

        int oiScore = calcOiPressureScore(latestOi);
        int liqScore = calcLiqIntensityScore(recentLiqs);
        int imbScore = calcImbalanceScore(orderBook);
        int total = oiScore + liqScore + imbScore;

        report.setOiPressureScore(oiScore);
        report.setLiqIntensityScore(liqScore);
        report.setImbalanceScore(imbScore);
        report.setMarketPressureTotal(total);

        log.info("{} | OI압력={}/20 | 청산강도={}/20 | 불균형={}/20 | 시장압력 합계={}/60",
                report.getSymbol(), oiScore, liqScore, imbScore, total);

        return report;
    }

    private int calcOiPressureScore(OpenInterestSnapshot oi) {
        if (oi == null || oi.getChangePercent() == null) return 5;
        double changePercent = Math.abs(oi.getChangePercent().doubleValue());

        if (changePercent >= 5.0) return 20;
        if (changePercent >= 3.0) return 16;
        if (changePercent >= 2.0) return 12;
        if (changePercent >= 1.0) return 8;
        if (changePercent >= 0.5) return 4;
        return 0;
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

    private int calcImbalanceScore(OrderBookSnapshot ob) {
        if (ob == null) return 5;

        BigDecimal bidQty = ob.getBidTotalQuantity() != null ? ob.getBidTotalQuantity() : BigDecimal.ZERO;
        BigDecimal askQty = ob.getAskTotalQuantity() != null ? ob.getAskTotalQuantity() : BigDecimal.ZERO;
        BigDecimal total = bidQty.add(askQty);

        if (total.compareTo(BigDecimal.ZERO) == 0) return 10;
        double bidRatio = bidQty.doubleValue() / total.doubleValue();
        double imbalance = Math.abs(bidRatio - 0.5) * 2;

        if (imbalance >= 0.7) return 20;
        if (imbalance >= 0.5) return 15;
        if (imbalance >= 0.3) return 10;
        if (imbalance >= 0.15) return 5;
        return 0;
    }

    public CascadeRiskReport synthesize(CascadeRiskReport report) {
        double densityScore = calcDensityScore(report);
        DensityLevel densityLevel = DensityLevel.fromScore(densityScore);

        double reachProb = calcCascadeReachProbability(report);

        double combinedScore = densityScore * 0.6 + report.getMarketPressureTotal() * (100.0 / 60.0) * 0.4;
        combinedScore = Math.max(0, Math.min(100, combinedScore));
        RiskLevel riskLevel = RiskLevel.fromScore(combinedScore);

        report.setDensityScore(Math.round(densityScore * 10.0) / 10.0);
        report.setDensityLevel(densityLevel);
        report.setCascadeReachProbability(Math.round(reachProb * 10.0) / 10.0);
        report.setRiskLevel(riskLevel);

        log.info("{} | densityScore={} ({}) | reachProb={}% | riskLevel={}",
                report.getSymbol(),
                String.format("%.1f", densityScore), densityLevel,
                String.format("%.1f", reachProb),
                riskLevel);

        return report;
    }

    private double calcDensityScore(CascadeRiskReport r) {
        double distScore = scoreDist(r.getDistancePercent());
        double depthScore = scoreDepthRatio(r.getDepthRatio());
        double levelScore = scoreLevelCount(r.getLevelCount());
        double clusterScore = scoreClusterOverlap(r.getOverlappingTierCount());
        double notionalScore = scoreNotional(r.getNotionalBetween());

        return distScore * 0.30
                + depthScore * 0.30
                + levelScore * 0.15
                + clusterScore * 0.15
                + notionalScore * 0.10;
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

    private double calcCascadeReachProbability(CascadeRiskReport r) {
        double distFactor = Math.max(0, 100 - r.getDistancePercent() * 15);

        double depthFactor = Math.max(0, 100 - r.getDepthRatio() * 3);

        double clusterFactor = Math.min(100, r.getOverlappingTierCount() * 25);

        double marketBoost = r.getMarketPressureTotal() * (100.0 / 60.0) * 0.15;

        double prob = distFactor * 0.40 + depthFactor * 0.35 + clusterFactor * 0.25 + marketBoost;
        return Math.max(0, Math.min(100, prob));
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
