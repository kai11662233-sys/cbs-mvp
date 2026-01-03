package com.example.cbs_mvp.pricing;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ShipCostTable {

    // まずは固定（Freezeではテーブル化してもOK）
    public BigDecimal costYen(String sizeTier, BigDecimal weightKg) {
        String s = (sizeTier == null || sizeTier.isBlank()) ? "XL" : sizeTier.trim().toUpperCase();

        BigDecimal base;
        BigDecimal perKg;
        switch (s) {
            case "S" -> { base = bd("1800"); perKg = bd("1200"); }
            case "M" -> { base = bd("2200"); perKg = bd("1400"); }
            case "L" -> { base = bd("2800"); perKg = bd("1700"); }
            default  -> { base = bd("3500"); perKg = bd("2000"); } // XL
        }
        return base.add(perKg.multiply(weightKg));
    }

    private static BigDecimal bd(String v) { return new BigDecimal(v); }
}
