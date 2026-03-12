package com.liquidation.riskengine.domain.service.montecarlo;

import com.liquidation.riskengine.domain.model.McPredictionRecord;
import com.liquidation.riskengine.domain.repository.McPredictionRepository;
import com.liquidation.riskengine.domain.service.MarkPriceCache;
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

            double price = currentPrice.doubleValue();
            boolean isLong = "LONG".equalsIgnoreCase(record.getPositionSide());
            boolean hit = isLong
                    ? price <= record.getLiquidationPrice()
                    : price >= record.getLiquidationPrice();

            record.setVerified(true);
            record.setActualHit(hit);
            record.setPriceAtDeadline(price);
            record.setVerifiedEpochMs(now);
            verified++;
        }

        repository.saveAll(pending);
        log.info("[Calibration] 검증 완료: {}건 처리, 총 미검증 잔여={}",
                verified, repository.findByVerifiedFalseAndDeadlineEpochMsLessThanEqual(
                        System.currentTimeMillis()).size());
    }
}
