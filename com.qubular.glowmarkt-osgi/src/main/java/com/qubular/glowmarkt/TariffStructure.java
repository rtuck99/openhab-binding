package com.qubular.glowmarkt;

import java.util.List;

public class TariffStructure {
    private String weekName;
    private String source;
    private List<TariffPlanDetail> planDetail;

    public TariffStructure() {
    }

    private TariffStructure(String weekName, String source, List<TariffPlanDetail> planDetail) {
        this.weekName = weekName;
        this.source = source;
        this.planDetail = planDetail;
    }

    public String getWeekName() {
        return weekName;
    }

    public String getSource() {
        return source;
    }

    public List<TariffPlanDetail> getPlanDetails() {
        return planDetail;
    }

    public String getId() {
        return weekName != null ? "week_" + weekName : "default_structure";
    }

    public static class Builder {
        private String weekName;
        private String source;
        private List<TariffPlanDetail> planDetail;

        public Builder withWeekName(String weekName) {
            this.weekName = weekName;
            return this;
        }

        public Builder withSource(String source) {
            this.source = source;
            return this;
        }

        public Builder withPlanDetail(List<TariffPlanDetail> planDetail) {
            this.planDetail = planDetail;
            return this;
        }

        public TariffStructure build() {
            return new TariffStructure(weekName, source, planDetail);
        }
    }
}
