package com.liquidation.riskengine.domain.model;

import java.math.BigDecimal;

public record PriceTick(long timestamp, BigDecimal price) {

    public PriceTick {
        if (price == null) {
            throw new IllegalArgumentException("price must not be null");
        }
        if (timestamp <= 0) {
            throw new IllegalArgumentException("timestamp must be positive");
        }
    }
}
