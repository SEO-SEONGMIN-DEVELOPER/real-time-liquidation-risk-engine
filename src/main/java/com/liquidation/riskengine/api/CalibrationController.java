package com.liquidation.riskengine.api;

import com.liquidation.riskengine.domain.repository.McPredictionRepository;
import com.liquidation.riskengine.domain.service.montecarlo.McCalibrationMetrics;
import com.liquidation.riskengine.domain.service.montecarlo.McCalibrationMetrics.CalibrationReport;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/calibration")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CalibrationController {

    private final McCalibrationMetrics calibrationMetrics;
    private final McPredictionRepository repository;

    @GetMapping("/report")
    public ResponseEntity<CalibrationReport> getReport(
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) Integer horizon) {
        String sym = symbol != null ? symbol.toUpperCase() : null;
        return ResponseEntity.ok(calibrationMetrics.calculate(sym, horizon));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        long total = repository.count();
        long verified = repository.countByVerifiedTrue();
        return ResponseEntity.ok(Map.of(
                "totalPredictions", total,
                "verifiedPredictions", verified,
                "pendingVerification", total - verified));
    }
}
