package com.liquidation.riskengine.api;

import com.liquidation.riskengine.domain.repository.CascadePredictionRepository;
import com.liquidation.riskengine.domain.repository.McPredictionRepository;
import com.liquidation.riskengine.domain.service.calibration.CalibrationCorrector;
import com.liquidation.riskengine.domain.service.cascade.CascadeCalibrationMetrics;
import com.liquidation.riskengine.domain.service.montecarlo.McCalibrationMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/calibration")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CalibrationController {

    private final McCalibrationMetrics mcMetrics;
    private final McPredictionRepository mcRepository;
    private final CascadeCalibrationMetrics cascadeMetrics;
    private final CascadePredictionRepository cascadeRepository;
    private final CalibrationCorrector calibrationCorrector;

    @GetMapping("/mc/report")
    public ResponseEntity<McCalibrationMetrics.CalibrationReport> getMcReport(
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) Integer horizon) {
        String sym = symbol != null ? symbol.toUpperCase() : null;
        return ResponseEntity.ok(mcMetrics.calculate(sym, horizon));
    }

    @GetMapping("/mc/stats")
    public ResponseEntity<Map<String, Object>> getMcStats() {
        long total = mcRepository.count();
        long verified = mcRepository.countByVerifiedTrue();
        return ResponseEntity.ok(Map.of(
                "totalPredictions", total,
                "verifiedPredictions", verified,
                "pendingVerification", total - verified));
    }

    @GetMapping("/cascade/report")
    public ResponseEntity<CascadeCalibrationMetrics.CalibrationReport> getCascadeReport(
            @RequestParam(required = false) String symbol) {
        String sym = symbol != null ? symbol.toUpperCase() : null;
        return ResponseEntity.ok(cascadeMetrics.calculate(sym));
    }

    @GetMapping("/cascade/stats")
    public ResponseEntity<Map<String, Object>> getCascadeStats() {
        long total = cascadeRepository.count();
        long verified = cascadeRepository.countByVerifiedTrue();
        return ResponseEntity.ok(Map.of(
                "totalPredictions", total,
                "verifiedPredictions", verified,
                "pendingVerification", total - verified));
    }

    @GetMapping("/report")
    public ResponseEntity<McCalibrationMetrics.CalibrationReport> getReport(
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) Integer horizon) {
        String sym = symbol != null ? symbol.toUpperCase() : null;
        return ResponseEntity.ok(mcMetrics.calculate(sym, horizon));
    }

    @GetMapping("/status")
    public ResponseEntity<CalibrationCorrector.CalibrationStatus> getCalibrationStatus() {
        return ResponseEntity.ok(calibrationCorrector.getStatus());
    }

    @PostMapping("/refit")
    public ResponseEntity<CalibrationCorrector.CalibrationStatus> triggerRefit() {
        calibrationCorrector.dailyFit();
        return ResponseEntity.ok(calibrationCorrector.getStatus());
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        long mcTotal = mcRepository.count();
        long mcVerified = mcRepository.countByVerifiedTrue();
        long cascadeTotal = cascadeRepository.count();
        long cascadeVerified = cascadeRepository.countByVerifiedTrue();
        return ResponseEntity.ok(Map.of(
                "mc", Map.of(
                        "totalPredictions", mcTotal,
                        "verifiedPredictions", mcVerified,
                        "pendingVerification", mcTotal - mcVerified),
                "cascade", Map.of(
                        "totalPredictions", cascadeTotal,
                        "verifiedPredictions", cascadeVerified,
                        "pendingVerification", cascadeTotal - cascadeVerified)));
    }
}
