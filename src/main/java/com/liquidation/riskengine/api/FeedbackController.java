package com.liquidation.riskengine.api;

import com.liquidation.riskengine.domain.model.FeedbackRecord;
import com.liquidation.riskengine.domain.repository.FeedbackRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/feedback")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackRepository feedbackRepository;

    @PostMapping
    public ResponseEntity<Map<String, Object>> submit(@RequestBody FeedbackRequest req, HttpServletRequest request) {
        String message = req.message() == null ? "" : req.message().trim();
        if (message.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "의견 내용을 입력해주세요."
            ));
        }
        if (message.length() > 2000) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "의견은 2000자 이내로 입력해주세요."
            ));
        }

        String name = req.name() == null ? "" : req.name().trim();
        String lang = req.lang() == null ? "unknown" : req.lang().trim();
        String symbol = req.symbol() == null ? "-" : req.symbol().trim();
        String ip = request.getRemoteAddr();

        FeedbackRecord saved = feedbackRepository.save(FeedbackRecord.builder()
                .name(name.isBlank() ? "anonymous" : name)
                .message(message)
                .lang(lang)
                .symbol(symbol)
                .ipAddress(ip)
                .createdAtEpochMs(Instant.now().toEpochMilli())
                .build());

        log.info("[Feedback] saved id={}, name='{}', lang='{}', symbol='{}', ip='{}'",
                saved.getId(), saved.getName(), saved.getLang(), saved.getSymbol(), saved.getIpAddress());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "의견이 접수되었습니다.",
                "id", saved.getId()
        ));
    }

    @GetMapping("/admin/list")
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));

        Page<FeedbackRecord> result = feedbackRepository.findAllByOrderByCreatedAtEpochMsDesc(
                PageRequest.of(safePage, safeSize)
        );

        List<Map<String, Object>> items = result.getContent().stream()
                .map(row -> Map.<String, Object>of(
                        "id", row.getId(),
                        "name", row.getName(),
                        "message", row.getMessage(),
                        "lang", row.getLang(),
                        "symbol", row.getSymbol(),
                        "createdAtEpochMs", row.getCreatedAtEpochMs()
                ))
                .toList();

        return ResponseEntity.ok(Map.of(
                "success", true,
                "items", items,
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalPages", result.getTotalPages(),
                "totalElements", result.getTotalElements(),
                "hasNext", result.hasNext(),
                "hasPrevious", result.hasPrevious()
        ));
    }

    public record FeedbackRequest(
            String name,
            String message,
            String lang,
            String symbol
    ) {
    }
}
