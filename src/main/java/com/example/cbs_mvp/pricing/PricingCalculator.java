package com.example.cbs_mvp.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

import com.example.cbs_mvp.ops.SystemFlagService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PricingCalculator {

    private final ShipCostTable shipCostTable;
    private final SystemFlagService flags;
    private final com.example.cbs_mvp.repo.PricingRuleRepository pricingRuleRepo;

    public PricingResponse calculate(PricingRequest in) {
        // Params（system_flags から読む。無ければデフォルト）
        BigDecimal fxBuffer = bd(flags.get("FX_BUFFER"), "0.03");
        BigDecimal domesticShip = bd(flags.get("DOMESTIC_SHIP"), "800");
        BigDecimal packingMisc = bd(flags.get("PACKING_MISC"), "300");
        BigDecimal plInbound = bd(flags.get("PL_INBOUND"), "200");
        BigDecimal plPickPack = bd(flags.get("PL_PICKPACK"), "500");
        BigDecimal ebayFeeRate = bd(flags.get("EBAY_FEE_RATE"), "0.15");
        BigDecimal refundResRate = bd(flags.get("REFUND_RES_RATE"), "0.05");
        BigDecimal profitMinYen = bd(flags.get("PROFIT_MIN_YEN"), "3000");
        BigDecimal profitMinRate = bd(flags.get("PROFIT_MIN_RATE"), "0.20"); // 0.20
        BigDecimal defaultWeight = bd(flags.get("DEFAULT_WEIGHT_KG"), "1.500");
        String defaultSize = s(flags.get("DEFAULT_SIZE_TIER"), "XL");

        // Apply Rules
        java.util.List<com.example.cbs_mvp.entity.PricingRule> rules = pricingRuleRepo
                .findAll(org.springframework.data.domain.Sort.by("priority").descending());

        for (com.example.cbs_mvp.entity.PricingRule r : rules) {
            boolean match = false;
            if ("SOURCE_PRICE".equals(r.getConditionType())) {
                BigDecimal p = nz(in.getSourcePriceYen());
                boolean minOk = r.getConditionMin() == null || p.compareTo(r.getConditionMin()) >= 0;
                boolean maxOk = r.getConditionMax() == null || p.compareTo(r.getConditionMax()) < 0; // Less than
                                                                                                     // strictly for
                                                                                                     // ranges
                match = minOk && maxOk;
            } else if ("WEIGHT".equals(r.getConditionType())) {
                BigDecimal w = (in.getWeightKg() == null) ? defaultWeight : in.getWeightKg();
                boolean minOk = r.getConditionMin() == null || w.compareTo(r.getConditionMin()) >= 0;
                boolean maxOk = r.getConditionMax() == null || w.compareTo(r.getConditionMax()) < 0;
                match = minOk && maxOk;
            }

            if (match) {
                if ("PROFIT_MIN_YEN".equals(r.getTargetField())) {
                    profitMinYen = r.getAdjustmentValue();
                } else if ("PROFIT_MIN_RATE".equals(r.getTargetField())) {
                    profitMinRate = r.getAdjustmentValue();
                }
            }
        }

        // F/G: Safe Weight/Size
        BigDecimal safeWeight = (in.getWeightKg() == null) ? defaultWeight : in.getWeightKg();
        String safeSize = (in.getSizeTier() == null || in.getSizeTier().isBlank())
                ? defaultSize
                : in.getSizeTier().trim().toUpperCase();

        // H: Safe FX
        BigDecimal fxSafe = in.getFxRate().multiply(BigDecimal.ONE.add(fxBuffer));

        // L: Intl Ship
        BigDecimal intlShip = shipCostTable.costYen(safeSize, safeWeight);

        // M: Total Cost
        BigDecimal totalCost = nz(in.getSourcePriceYen())
                .add(domesticShip)
                .add(packingMisc)
                .add(plInbound)
                .add(plPickPack)
                .add(intlShip);

        // I: Rec Sell USD
        BigDecimal minProfitByRate = totalCost.multiply(profitMinRate);
        BigDecimal requiredProfit = profitMinYen.max(minProfitByRate);

        BigDecimal divisor = BigDecimal.ONE.subtract(ebayFeeRate).subtract(refundResRate);
        BigDecimal yenRevenueNeeded = totalCost.add(requiredProfit)
                .divide(divisor, 10, RoundingMode.HALF_UP);

        BigDecimal recSellUsd = yenRevenueNeeded
                .divide(fxSafe, 10, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.CEILING); // ★USDは2桁切り上げ

        // J: Use Sell USD
        BigDecimal useSellUsd = (in.getTargetSellUsd() == null) ? recSellUsd : in.getTargetSellUsd();

        // K/N/O/P (JPY metrics rounded to 0 decimals)
        BigDecimal sellYen = useSellUsd.multiply(fxSafe).setScale(0, RoundingMode.HALF_UP);
        BigDecimal feesAndReserve = sellYen.multiply(ebayFeeRate.add(refundResRate)).setScale(0, RoundingMode.HALF_UP);

        BigDecimal expectedCostJpy = totalCost.setScale(0, RoundingMode.HALF_UP);
        BigDecimal expectedProfitJpy = sellYen.subtract(expectedCostJpy).subtract(feesAndReserve);

        BigDecimal profitRate = expectedCostJpy.compareTo(BigDecimal.ZERO) > 0
                ? expectedProfitJpy.divide(expectedCostJpy, 6, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Q: PROFIT GATE
        boolean profitAmountOk = expectedProfitJpy.compareTo(profitMinYen) >= 0;
        // expectedProfitJpy >= expectedCostJpy * profitMinRate
        boolean profitRateOk = expectedProfitJpy.compareTo(expectedCostJpy.multiply(profitMinRate)) >= 0;

        boolean gateProfitOk = profitAmountOk && profitRateOk;

        // Exposure Gate (optional guardrail)
        BigDecimal maxWorstCaseLoss = bd(flags.get("MAX_WORST_CASE_LOSS"), "30000");
        // 最悪損失 = 仕入 + 送料 + 手数料 − 回収見込み（返品なし想定） (Salvage = 0)
        BigDecimal worstCaseLossJpy = expectedCostJpy.add(feesAndReserve); // Very simplified worst case scenario
        boolean exposureRisk = worstCaseLossJpy.compareTo(maxWorstCaseLoss) > 0;

        // W: WARN（入力売価 < 推奨売価）
        String warn = "";
        if (in.getTargetSellUsd() != null && in.getTargetSellUsd().compareTo(recSellUsd) < 0) {
            warn = "⚠️Price Low";
        }
        if (exposureRisk) {
            warn = warn.isEmpty() ? "⚠️Exposure Risk" : warn + ", ⚠️Exposure Risk";
        }

        return PricingResponse.builder()
                .safeWeightKg(safeWeight)
                .safeSizeTier(safeSize)
                .fxSafe(fxSafe)

                .calcSourcePriceYen(nz(in.getSourcePriceYen()).setScale(0, RoundingMode.HALF_UP))
                .usedFeeRate(ebayFeeRate)

                .intlShipCostYen(intlShip.setScale(0, RoundingMode.HALF_UP))
                .expectedCostJpy(expectedCostJpy)

                .recSellUsd(recSellUsd)
                .useSellUsd(useSellUsd)
                .sellYen(sellYen)

                .feesAndReserveYen(feesAndReserve)
                .expectedProfitJpy(expectedProfitJpy)
                .profitRate(profitRate)

                .gateProfitOk(gateProfitOk)
                .warn(warn)
                .build();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal bd(String s, String def) {
        String v = (s == null || s.isBlank()) ? def : s.trim();
        return new BigDecimal(v);
    }

    private static String s(String v, String def) {
        return (v == null || v.isBlank()) ? def : v.trim();
    }
}
