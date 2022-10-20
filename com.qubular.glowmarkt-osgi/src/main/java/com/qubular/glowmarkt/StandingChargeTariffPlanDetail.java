package com.qubular.glowmarkt;

import java.math.BigDecimal;

public class StandingChargeTariffPlanDetail extends TariffPlanDetail {
    private BigDecimal standing;

    public StandingChargeTariffPlanDetail(BigDecimal standing) {
        this.standing = standing;
    }

    public BigDecimal getStanding() {
        return standing;
    }

    @Override
    public String getId() {
        return "standing_charge";
    }

    @Override
    public BigDecimal getAmount() {
        return standing;
    }
}
