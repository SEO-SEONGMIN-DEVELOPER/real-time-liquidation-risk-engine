package com.liquidation.riskengine.domain.service;

import com.liquidation.riskengine.domain.model.CascadeRiskReport;
import com.liquidation.riskengine.domain.model.CascadeRiskReport.LiqCluster;
import com.liquidation.riskengine.domain.model.OrderBookSnapshot;
import com.liquidation.riskengine.domain.model.OrderBookSnapshot.PriceLevel;
import com.liquidation.riskengine.domain.service.LiquidationPriceCalculator.EstimatedLiquidation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CascadeRiskCalculator {

    private final LiquidationPriceCalculator liquidationPriceCalculator;

    private static final MathContext MC = new MathContext(8, RoundingMode.HALF_UP);

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

    public CascadeRiskReport mapLiquidationClusters(CascadeRiskReport report) {
        BigDecimal low = report.getPriceRangeLow();
        BigDecimal high = report.getPriceRangeHigh();
        BigDecimal currentPrice = report.getCurrentPrice();
        boolean isLong = "LONG".equalsIgnoreCase(report.getPositionSide());

        List<EstimatedLiquidation> distribution =
                liquidationPriceCalculator.estimateDistribution(currentPrice, report.getSymbol());

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
                        .distanceFromCurrentPercent(distFromCurrent)
                        .build());

                estimatedLiqVolume = estimatedLiqVolume.add(
                        BigDecimal.valueOf(est.weight()), MC);
            }
        }

        report.setClustersInPath(clustersInPath);
        report.setOverlappingTierCount(clustersInPath.size());
        report.setEstimatedLiqVolume(estimatedLiqVolume.setScale(4, RoundingMode.HALF_UP));

        log.info("{} | 구간 내 청산 클러스터 {}개 | 총 가중치={} | 클러스터: {}",
                report.getSymbol(),
                clustersInPath.size(),
                estimatedLiqVolume.setScale(4, RoundingMode.HALF_UP).toPlainString(),
                clustersInPath.stream()
                        .map(c -> String.format("%dx(%.2f%%, w=%.2f)", c.getLeverage(), c.getDistanceFromCurrentPercent(), c.getWeight()))
                        .toList());

        return report;
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
