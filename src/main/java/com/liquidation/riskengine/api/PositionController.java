package com.liquidation.riskengine.api;

import com.liquidation.riskengine.domain.model.UserPosition;
import com.liquidation.riskengine.domain.service.RiskStateManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
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

@Slf4j
@RestController
@RequestMapping("/api/position")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PositionController {

    private final RiskStateManager riskStateManager;

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody UserPosition position) {
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

        log.info("[Position API] 포지션 등록 요청: symbol={}, liqPrice={}, side={}, leverage={}x",
                position.getSymbol(), position.getLiquidationPrice(),
                position.getPositionSide(), position.getLeverage());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "symbol", position.getSymbol().toUpperCase(),
                "message", "포지션이 등록되었습니다. 해당 심볼의 실시간 위험 계산이 시작됩니다."));
    }

    @DeleteMapping("/unregister")
    public ResponseEntity<Map<String, Object>> unregister(@RequestParam String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "symbol은 필수입니다"));
        }

        String normalized = symbol.toUpperCase();
        UserPosition existing = riskStateManager.getPosition(normalized);
        if (existing == null) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "symbol", normalized,
                    "message", "등록된 포지션이 없습니다."));
        }

        riskStateManager.removePosition(normalized);
        log.info("[Position API] 포지션 해제 요청: symbol={}", normalized);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "symbol", normalized,
                "message", "포지션이 해제되었습니다. 해당 심볼의 위험 계산이 중단됩니다."));
    }

    @GetMapping("/list")
    public ResponseEntity<Collection<UserPosition>> listPositions() {
        return ResponseEntity.ok(riskStateManager.getAllPositions());
    }
}
