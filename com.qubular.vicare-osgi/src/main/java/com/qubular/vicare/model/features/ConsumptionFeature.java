package com.qubular.vicare.model.features;

import com.qubular.vicare.model.values.DimensionalValue;
import com.qubular.vicare.model.Feature;

import java.util.Optional;

public abstract class ConsumptionFeature extends Feature {
    public DimensionalValue getSevenDays() {
        return getConsumption(Stat.LAST_SEVEN_DAYS).orElse(null);
    }

    public enum Stat {
        CURRENT_DAY,
        LAST_SEVEN_DAYS,
        CURRENT_MONTH,
        CURRENT_YEAR,
        PREVIOUS_DAY,
        CURRENT_WEEK,
        PREVIOUS_WEEK,
        PREVIOUS_MONTH,
        PREVIOUS_YEAR
    }

    public ConsumptionFeature(String name) {
        super(name);
    }

    public abstract Optional<DimensionalValue> getConsumption(Stat stat);
}
