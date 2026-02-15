package com.liquidation.riskengine.domain.model;

import java.math.BigDecimal;
import java.util.List;

public record MaintenanceMarginTier(
        int tier,
        BigDecimal maxNotional,
        int maxLeverage,
        BigDecimal maintenanceMarginRate,
        BigDecimal maintenanceAmount
) {

    public static final List<MaintenanceMarginTier> BTCUSDT_TIERS = List.of(
            new MaintenanceMarginTier(1,  bd("50000"),        125, bd("0.0040"), bd("0")),
            new MaintenanceMarginTier(2,  bd("250000"),       100, bd("0.0050"), bd("50")),
            new MaintenanceMarginTier(3,  bd("1000000"),       50, bd("0.0100"), bd("1300")),
            new MaintenanceMarginTier(4,  bd("5000000"),       20, bd("0.0250"), bd("16300")),
            new MaintenanceMarginTier(5,  bd("10000000"),      10, bd("0.0500"), bd("141300")),
            new MaintenanceMarginTier(6,  bd("20000000"),       5, bd("0.1000"), bd("641300")),
            new MaintenanceMarginTier(7,  bd("50000000"),       4, bd("0.1250"), bd("1141300")),
            new MaintenanceMarginTier(8,  bd("100000000"),      3, bd("0.1500"), bd("2391300")),
            new MaintenanceMarginTier(9,  bd("200000000"),      2, bd("0.2500"), bd("12391300")),
            new MaintenanceMarginTier(10, bd("300000000"),      1, bd("0.5000"), bd("62391300"))
    );

    public static final List<MaintenanceMarginTier> ETHUSDT_TIERS = List.of(
            new MaintenanceMarginTier(1,  bd("10000"),        100, bd("0.0050"), bd("0")),
            new MaintenanceMarginTier(2,  bd("100000"),        75, bd("0.0065"), bd("15")),
            new MaintenanceMarginTier(3,  bd("500000"),        50, bd("0.0100"), bd("190")),
            new MaintenanceMarginTier(4,  bd("1000000"),       25, bd("0.0200"), bd("5190")),
            new MaintenanceMarginTier(5,  bd("2000000"),       10, bd("0.0500"), bd("35190")),
            new MaintenanceMarginTier(6,  bd("5000000"),        5, bd("0.1000"), bd("135190")),
            new MaintenanceMarginTier(7,  bd("10000000"),       3, bd("0.1250"), bd("260190")),
            new MaintenanceMarginTier(8,  bd("20000000"),       2, bd("0.2500"), bd("1510190")),
            new MaintenanceMarginTier(9,  bd("40000000"),       1, bd("0.5000"), bd("6510190"))
    );

    public static List<MaintenanceMarginTier> getTiersForSymbol(String symbol) {
        if (symbol == null) return BTCUSDT_TIERS;
        return switch (symbol.toUpperCase()) {
            case "ETHUSDT" -> ETHUSDT_TIERS;
            default -> BTCUSDT_TIERS;
        };
    }

    public static MaintenanceMarginTier findTier(BigDecimal notional, String symbol) {
        List<MaintenanceMarginTier> tiers = getTiersForSymbol(symbol);
        for (MaintenanceMarginTier t : tiers) {
            if (notional.compareTo(t.maxNotional()) <= 0) {
                return t;
            }
        }
        return tiers.get(tiers.size() - 1);
    }

    private static BigDecimal bd(String val) {
        return new BigDecimal(val);
    }
}
