package com.liquidation.riskengine.api;

import com.liquidation.riskengine.domain.model.MonteCarloReport;
import com.liquidation.riskengine.domain.model.UserPosition;
import com.liquidation.riskengine.domain.service.RiskStateManager;
import com.liquidation.riskengine.domain.service.montecarlo.MonteCarloSimulationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/mc")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MonteCarloController {

    private final MonteCarloSimulationService mcService;
    private final RiskStateManager riskStateManager;

    @GetMapping("/simulate")
    public ResponseEntity<Object> simulate(@RequestParam String symbol) {
        String key = symbol.toUpperCase();

        UserPosition position = riskStateManager.getPosition(key);
        if (position == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "symbol", key,
                    "message", "등록된 포지션이 없습니다. /api/position/register로 먼저 등록하세요."));
        }

        log.info("[MC API] 온디맨드 시뮬레이션 요청: symbol={}", key);

        return mcService.simulate(key, position.getLiquidationPrice(), position.getPositionSide())
                .<ResponseEntity<Object>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok(Map.of(
                        "success", false,
                        "symbol", key,
                        "message", "시뮬레이션 실행 불가: 현재가 없음 또는 MC 비활성 상태")));
    }

    @GetMapping("/latest")
    public ResponseEntity<Object> latest(@RequestParam String symbol) {
        String key = symbol.toUpperCase();

        return mcService.getLatest(key)
                .<ResponseEntity<Object>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok(Map.of(
                        "success", false,
                        "symbol", key,
                        "message", "아직 MC 결과가 없습니다. /api/mc/simulate로 먼저 실행하거나 파이프라인 실행을 대기하세요.")));
    }
}
