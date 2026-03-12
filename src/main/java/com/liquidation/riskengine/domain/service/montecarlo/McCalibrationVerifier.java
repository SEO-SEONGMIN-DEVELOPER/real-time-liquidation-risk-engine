package com.liquidation.riskengine.domain.service.montecarlo;

import com.liquidation.riskengine.domain.model.McPredictionRecord;
import com.liquidation.riskengine.domain.repository.McPredictionRepository;
import com.liquidation.riskengine.domain.service.MarkPriceCache;
import com.liquidation.riskengine.domain.service.PriceHistoryBuffer;
import com.liquidation.riskengine.domain.service.PriceHistoryBuffer.MinMax;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class McCalibrationVerifier {

    private final McPredictionRepository repository;
    private final MarkPriceCache markPriceCache;
    private final PriceHistoryBuffer priceHistoryBuffer;

    @Scheduled(fixedDelayString = "${montecarlo.calibration.verify-interval-ms:60000}")
    public void verifyExpiredPredictions() {
        long now = System.currentTimeMillis();
        List<McPredictionRecord> pending = repository
                .findByVerifiedFalseAndDeadlineEpochMsLessThanEqual(now);

        if (pending.isEmpty()) return;

        int verified = 0;
        for (McPredictionRecord record : pending) {
            BigDecimal currentPrice = markPriceCache.get(record.getSymbol());
            if (currentPrice == null) continue;

            boolean isLong = "LONG".equalsIgnoreCase(record.getPositionSide());

            MinMax minMax = priceHistoryBuffer.getMinMaxInRange(
                    record.getSymbol(),
                    record.getPredictionEpochMs(),
                    record.getDeadlineEpochMs());

            boolean hit;
            if (minMax != null) {
                hit = isLong
                        ? minMax.min() <= record.getLiquidationPrice()
                        : minMax.max() >= record.getLiquidationPrice();
                record.setPriceMinDuringHorizon(minMax.min());
                record.setPriceMaxDuringHorizon(minMax.max());
            } else {
                double price = currentPrice.doubleValue();
                hit = isLong
                        ? price <= record.getLiquidationPrice()
                        : price >= record.getLiquidationPrice();
            }

            record.setVerified(true);
            record.setActualHit(hit);
            record.setPriceAtDeadline(currentPrice.doubleValue());
            record.setVerifiedEpochMs(now);
            verified++;
        }

        repository.saveAll(pending);
        log.info("[Calibration] 검증 완료: {}건 (first-passage), 미검증 잔여={}",
                verified, repository.findByVerifiedFalseAndDeadlineEpochMsLessThanEqual(
                        System.currentTimeMillis()).size());
    }
}
