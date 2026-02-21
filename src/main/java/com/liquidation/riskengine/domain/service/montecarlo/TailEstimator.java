package com.liquidation.riskengine.domain.service.montecarlo;

import com.liquidation.riskengine.domain.model.PriceTick;
import com.liquidation.riskengine.domain.service.PriceHistoryBuffer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TailEstimator {

    static final double NU_FLOOR = 3.0;
    static final double NU_CEILING = 30.0;
    static final int MIN_TICKS = 30;

    private final PriceHistoryBuffer priceHistoryBuffer;

    public double estimateDegreesOfFreedom(String symbol, Duration window) {
        List<PriceTick> ticks = priceHistoryBuffer.getRecentPrices(symbol, window);

        if (ticks.size() < MIN_TICKS) {
            log.debug("[TailEstimator] 데이터 부족: symbol={}, ticks={}, fallback nu={}",
                    symbol, ticks.size(), NU_CEILING);
            return NU_CEILING;
        }

        double[] logReturns = computeLogReturns(ticks);
        if (logReturns.length < MIN_TICKS - 1) {
            return NU_CEILING;
        }

        double kurtosis = excessKurtosis(logReturns);

        double nu;
        if (kurtosis <= 0) {
            nu = NU_CEILING;
        } else {
            nu = 6.0 / kurtosis + 4.0;
        }

        nu = Math.max(NU_FLOOR, Math.min(NU_CEILING, nu));

        log.debug("[TailEstimator] symbol={}, kurtosis={:.4f}, nu={:.2f}, ticks={}",
                symbol, kurtosis, nu, ticks.size());

        return nu;
    }

    private double[] computeLogReturns(List<PriceTick> ticks) {
        double[] returns = new double[ticks.size() - 1];
        for (int i = 1; i < ticks.size(); i++) {
            double prev = ticks.get(i - 1).price().doubleValue();
            double curr = ticks.get(i).price().doubleValue();
            if (prev > 0) {
                returns[i - 1] = Math.log(curr / prev);
            }
        }
        return returns;
    }

    private double excessKurtosis(double[] data) {
        int n = data.length;
        double mean = 0;
        for (double v : data) mean += v;
        mean /= n;

        double m2 = 0, m4 = 0;
        for (double v : data) {
            double d = v - mean;
            double d2 = d * d;
            m2 += d2;
            m4 += d2 * d2;
        }
        m2 /= n;
        m4 /= n;

        if (m2 == 0) return 0;
        return (m4 / (m2 * m2)) - 3.0;
    }
}
