package com.liquidation.riskengine.api;

import com.liquidation.riskengine.domain.model.UserPosition;
import com.liquidation.riskengine.domain.service.state.RiskStateManager;
import com.liquidation.riskengine.domain.service.montecarlo.MonteCarloCalibrationLogger;
import com.liquidation.riskengine.domain.service.montecarlo.MonteCarloSimulationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/position")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PositionController {

    private final RiskStateManager riskStateManager;
    private final MonteCarloSimulationService mcService;
    private final MonteCarloCalibrationLogger calibrationLogger;
    private final SimpMessagingTemplate messagingTemplate;

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody UserPosition position) {
        if (position.getUserId() == null || position.getUserId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "userId는 필수입니다"));
        }
        if (position.getSymbol() == null || position.getSymbol().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "symbol은 필수입니다"));
        }
        if (position.getLiquidationPrice() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "liquidationPrice는 필수입니다"));
        }

        riskStateManager.registerPosition(position);

        String userId = position.getUserId().trim().toLowerCase();
        String symbol = position.getSymbol().toUpperCase();
        log.info("[Position API] 포지션 등록 요청: userId={}, symbol={}, liqPrice={}, side={}, leverage={}x",
                userId, symbol, position.getLiquidationPrice(),
                position.getPositionSide(), position.getLeverage());

        CompletableFuture.runAsync(() -> {
            try {
                mcService.simulate(userId, symbol, position.getLiquidationPrice(), position.getPositionSide())
                        .ifPresent(mcReport -> {
                            String dest = "/topic/mc/" + userId + "/" + symbol;
                            messagingTemplate.convertAndSend(dest, mcReport);
                            calibrationLogger.logPrediction(mcReport);
                            log.info("[Position API] 즉시 MC 완료: userId={}, symbol={}, risk={}",
                                    userId, symbol, mcReport.getRiskLevel());
                        });
            } catch (Exception e) {
                log.warn("[Position API] 즉시 MC 실패: userId={}, symbol={}", userId, symbol, e);
            }
        });

        return ResponseEntity.ok(Map.of(
                "success", true,
                "userId", userId,
                "symbol", symbol,
                "wsTopic", "/topic/risk/" + userId + "/" + symbol,
                "message", "포지션이 등록되었습니다. 해당 심볼의 실시간 위험 계산이 시작됩니다."));
    }

    @DeleteMapping("/unregister")
    public ResponseEntity<Map<String, Object>> unregister(
            @RequestParam String userId,
            @RequestParam String symbol) {
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "userId는 필수입니다"));
        }
        if (symbol == null || symbol.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "symbol은 필수입니다"));
        }

        String normalizedUserId = userId.trim().toLowerCase();
        String normalized = symbol.toUpperCase();
        UserPosition existing = riskStateManager.getPosition(normalizedUserId, normalized);
        if (existing == null) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "userId", normalizedUserId,
                    "symbol", normalized,
                    "message", "등록된 포지션이 없습니다."));
        }

        riskStateManager.removePosition(normalizedUserId, normalized);
        log.info("[Position API] 포지션 해제 요청: userId={}, symbol={}", normalizedUserId, normalized);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "userId", normalizedUserId,
                "symbol", normalized,
                "message", "포지션이 해제되었습니다. 해당 심볼의 위험 계산이 중단됩니다."));
    }

    @GetMapping("/list")
    public ResponseEntity<Collection<UserPosition>> listPositions(@RequestParam String userId) {
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.badRequest().body(java.util.List.of());
        }
        return ResponseEntity.ok(riskStateManager.getPositionsByUser(userId));
    }
}
