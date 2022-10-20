package com.qubular.glowmarkt;

import java.math.BigDecimal;

public class PerUnitTariffPlanDetail extends TariffPlanDetail {
    private Integer tier;
    private BigDecimal rate;

    public PerUnitTariffPlanDetail(Integer tier, BigDecimal rate) {
        this.tier = tier;
        this.rate = rate;
    }

    public Integer getTier() {
        return tier;
    }

    public BigDecimal getRate() {
        return rate;
    }

    @Override
    public String getId() {
        return tier != null ? "rate_" + tier : "rate";
    }

    @Override
    public BigDecimal getAmount() {
        return rate;
    }
}
