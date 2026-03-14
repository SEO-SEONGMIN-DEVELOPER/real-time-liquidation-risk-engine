package com.liquidation.riskengine.domain.service.montecarlo;

import com.liquidation.riskengine.domain.model.PriceTick;
import com.liquidation.riskengine.domain.service.PriceHistoryBuffer;
import com.liquidation.riskengine.infra.binance.scheduler.FundingRateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DriftEstimator {

    static final double MINUTES_PER_YEAR = 365.25 * 24 * 60;
    static final int FUNDING_PERIODS_PER_DAY = 3;

    private final FundingRateService fundingRateService;
    private final PriceHistoryBuffer priceHistoryBuffer;
    private final DriftProperties properties;

    public double estimate(String symbol) {
        if (!properties.isEnabled()) return 0.0;

        double muFunding = estimateFundingDrift(symbol);
        double muMomentum = estimateMomentumDrift(symbol);

        double mu = muFunding * properties.getFundingWeight()
                  + muMomentum * properties.getMomentumWeight();

        double cap = properties.getMaxAnnualDrift();
        mu = Math.max(-cap, Math.min(cap, mu));

        log.debug("[Drift] symbol={}, muFunding={:.4f}, muMomentum={:.4f}, mu={:.4f}",
                symbol, muFunding, muMomentum, mu);

        return mu;
    }

    private double estimateFundingDrift(String symbol) {
        double fundingRate = fundingRateService.getFundingRate(symbol);
        return -fundingRate * FUNDING_PERIODS_PER_DAY * 365;
    }

    private double estimateMomentumDrift(String symbol) {
        Duration window = parseMomentumWindow(properties.getMomentumWindow());
        List<PriceTick> ticks = priceHistoryBuffer.getRecentPrices(symbol, window);

        if (ticks.size() < 2) return 0.0;

        PriceTick first = ticks.getFirst();
        PriceTick last = ticks.getLast();

        double logReturn = Math.log(last.price().doubleValue() / first.price().doubleValue());
        double periodMinutes = (last.timestamp() - first.timestamp()) / 60_000.0;
        if (periodMinutes <= 0) return 0.0;

        return logReturn * (MINUTES_PER_YEAR / periodMinutes);
    }

    private Duration parseMomentumWindow(String label) {
        if (label == null) return Duration.ofHours(1);
        return switch (label) {
            case "5m"  -> Duration.ofMinutes(5);
            case "15m" -> Duration.ofMinutes(15);
            case "30m" -> Duration.ofMinutes(30);
            case "1h"  -> Duration.ofHours(1);
            case "4h"  -> Duration.ofHours(4);
            default    -> Duration.ofHours(1);
        };
    }
}
