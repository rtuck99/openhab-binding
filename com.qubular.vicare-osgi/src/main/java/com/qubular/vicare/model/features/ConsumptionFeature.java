package com.qubular.vicare.model.features;

import com.qubular.vicare.model.DimensionalValue;
import com.qubular.vicare.model.Feature;

public class ConsumptionFeature extends Feature {
    private final DimensionalValue today;
    private final DimensionalValue sevenDays;
    private final DimensionalValue month;

    public ConsumptionFeature(String name, DimensionalValue today, DimensionalValue sevenDays, DimensionalValue month) {
        super(name);
        this.today = today;
        this.sevenDays = sevenDays;
        this.month = month;
    }

    public DimensionalValue getToday() {
        return today;
    }

    public DimensionalValue getSevenDays() {
        return sevenDays;
    }

    public DimensionalValue getMonth() {
        return month;
    }

    @Override
    public void accept(Visitor v) {
        v.visit(this);
    }
}
