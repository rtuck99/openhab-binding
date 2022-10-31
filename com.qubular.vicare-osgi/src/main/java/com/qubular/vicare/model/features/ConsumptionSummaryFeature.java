package com.qubular.vicare.model.features;

import com.qubular.vicare.model.values.DimensionalValue;

import java.util.Map;
import java.util.Optional;

import static java.util.Optional.ofNullable;

public class ConsumptionSummaryFeature extends ConsumptionFeature {
    private final Map<String, DimensionalValue> values;

    public ConsumptionSummaryFeature(String name, DimensionalValue today, DimensionalValue sevenDays, DimensionalValue month, DimensionalValue year) {
        super(name);
        this.values = Map.of("currentDay", today,
                             "lastSevenDays", sevenDays,
                             "currentMonth", month,
                             "currentYear", year);
    }

    @Override
    public Optional<DimensionalValue> getConsumption(Stat stat) {
        switch(stat) {
            case CURRENT_DAY:
                return ofNullable(values.get("currentDay"));
            case LAST_SEVEN_DAYS:
                return ofNullable(values.get("lastSevenDays"));
            case CURRENT_MONTH:
                return ofNullable(values.get("currentMonth"));
            case CURRENT_YEAR:
                return ofNullable(values.get("currentYear"));
        }
        return Optional.empty();
    }

    @Override
    public Map<String, DimensionalValue> getProperties() {
        return values;
    }

    @Override
    public void accept(Visitor v) {
        v.visit(this);
    }
}
