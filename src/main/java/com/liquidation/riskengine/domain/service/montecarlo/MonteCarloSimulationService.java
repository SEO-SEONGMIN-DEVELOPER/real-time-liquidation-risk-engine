package com.liquidation.riskengine.domain.service.montecarlo;

import com.liquidation.riskengine.domain.model.CascadeRiskReport;
import com.liquidation.riskengine.domain.model.MonteCarloReport;
import com.liquidation.riskengine.domain.model.VolatilitySnapshot;
import com.liquidation.riskengine.domain.service.GarchEstimator.GarchResult;
import com.liquidation.riskengine.domain.service.MarkPriceCache;
import com.liquidation.riskengine.domain.service.VolatilityEstimator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonteCarloSimulationService {

    private final MarkPriceCache markPriceCache;
    private final VolatilityEstimator volatilityEstimator;
    private final PricePathGenerator pricePathGenerator;
    private final LiquidationDetector liquidationDetector;
    private final MonteCarloProperties properties;
    private final DriftEstimator driftEstimator;
    private final TailEstimator tailEstimator;

    private final Map<String, MonteCarloReport> latestReports = new ConcurrentHashMap<>();

    public Optional<MonteCarloReport> simulate(String symbol,
                                               BigDecimal liquidationPrice,
                                               String positionSide) {
        return simulate(symbol, liquidationPrice, positionSide, null);
    }

    public Optional<MonteCarloReport> simulate(String symbol,
                                               BigDecimal liquidationPrice,
                                               String positionSide,
                                               CascadeRiskReport cascadeReport) {
        if (!properties.isEnabled()) {
            log.debug("[MC] 비활성 상태, 시뮬레이션 스킵: symbol={}", symbol);
            return Optional.empty();
        }

        BigDecimal currentPrice = markPriceCache.get(symbol);
        if (currentPrice == null) {
            log.warn("[MC] 현재가 없음, 시뮬레이션 불가: symbol={}", symbol);
            return Optional.empty();
        }

        long startNano = System.nanoTime();

        VolatilitySnapshot volSnap = volatilityEstimator.estimate(symbol);
        double sigma = volSnap.getSigmaForLabel(properties.getVolatilityWindow());

        int totalSteps = properties.maxHorizonMinutes() / properties.getTimeStepMinutes();
        double[] sigmaSchedule = null;

        Duration volWindow = parseVolatilityWindow(properties.getVolatilityWindow());
        GarchResult garchResult = volatilityEstimator.estimateGarch(symbol, volWindow);
        if (garchResult != null) {
            sigmaSchedule = garchResult.forecastSigmaScheduleAnnualized(totalSteps);
            sigma = garchResult.getAnnualizedSigma();
            log.debug("[MC] GARCH σ schedule 적용: symbol={}, σ_0={:.4f}, steps={}", symbol, sigma, totalSteps);
        }

        double mu = driftEstimator.estimate(symbol);

        if (cascadeReport != null) {
            double muCascade = calcCascadeDrift(cascadeReport);
            sigma = applyCascadeSigmaBoost(sigma, cascadeReport);
            if (sigmaSchedule != null) {
                double boost = calcSigmaMultiplier(cascadeReport);
                for (int i = 0; i < sigmaSchedule.length; i++) {
                    sigmaSchedule[i] *= boost;
                }
            }
            mu += muCascade;
            double cap = 2.0;
            mu = Math.max(-cap, Math.min(cap, mu));
            log.debug("[MC] Cascade 통합: muCascade={:.4f}, totalMu={:.4f}, σ_boosted={:.4f}",
                    muCascade, mu, sigma);
        }

        double nu = properties.getDegreesOfFreedom();
        if (properties.isUseFatTail()) {
            nu = tailEstimator.estimateDegreesOfFreedom(symbol, volWindow);
        }

        SimulationRequest request = SimulationRequest.builder()
                .startPrice(currentPrice.doubleValue())
                .sigma(sigma)
                .mu(mu)
                .pathCount(properties.getPathCount())
                .timeStepMinutes(properties.getTimeStepMinutes())
                .horizonMinutes(properties.maxHorizonMinutes())
                .useFatTail(properties.isUseFatTail())
                .degreesOfFreedom(nu)
                .sigmaSchedule(sigmaSchedule)
                .build();

        double[][] paths = pricePathGenerator.generate(request);

        MonteCarloReport report = liquidationDetector.detect(
                symbol,
                paths,
                liquidationPrice.doubleValue(),
                positionSide,
                sigma,
                properties.getTimeStepMinutes(),
                properties.horizonsArray());

        long totalMicros = (System.nanoTime() - startNano) / 1_000;
        log.info("[MC] 시뮬레이션 완료: symbol={}, side={}, σ={:.4f}, μ={:.4f}, ν={:.1f}, cascade={}, risk={}, paths={}, total={}μs",
                symbol, positionSide, sigma, mu, nu, cascadeReport != null,
                report.getRiskLevel(), properties.getPathCount(), totalMicros);

        latestReports.put(symbol.toUpperCase(), report);

        return Optional.of(report);
    }

    private double calcCascadeDrift(CascadeRiskReport cascade) {
        double pressureNorm = cascade.getMarketPressureTotal() / 60.0;
        double sign = "LONG".equalsIgnoreCase(cascade.getPositionSide()) ? -1.0 : 1.0;
        double scaleFactor = 0.5;
        return sign * pressureNorm * scaleFactor;
    }

    private double applyCascadeSigmaBoost(double sigma, CascadeRiskReport cascade) {
        return sigma * calcSigmaMultiplier(cascade);
    }

    private double calcSigmaMultiplier(CascadeRiskReport cascade) {
        double pressureNorm = cascade.getMarketPressureTotal() / 60.0;
        return 1.0 + pressureNorm * 0.3;
    }

    public Optional<MonteCarloReport> getLatest(String symbol) {
        if (symbol == null) return Optional.empty();
        return Optional.ofNullable(latestReports.get(symbol.toUpperCase()));
    }

    private Duration parseVolatilityWindow(String label) {
        if (label == null) return Duration.ofHours(1);
        return switch (label) {
            case "1m" -> Duration.ofMinutes(1);
            case "5m" -> Duration.ofMinutes(5);
            case "1h" -> Duration.ofHours(1);
            case "24h" -> Duration.ofHours(24);
            default -> Duration.ofHours(1);
        };
    }
}
