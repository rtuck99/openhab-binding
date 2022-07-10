package com.qubular.vicare.model.features;

import com.qubular.vicare.model.DimensionalValue;
import com.qubular.vicare.model.Feature;

import java.util.List;

public class StatisticsFeature extends Feature {
    private final List<DimensionalValue> statistics;

    public StatisticsFeature(String name, List<DimensionalValue> statistics) {
        super(name);
        this.statistics = List.copyOf(statistics);
    }

    public List<DimensionalValue> getStatistics() {
        return statistics;
    }

    @Override
    public void accept(Visitor v) {
        v.visit(this);
    }
}
