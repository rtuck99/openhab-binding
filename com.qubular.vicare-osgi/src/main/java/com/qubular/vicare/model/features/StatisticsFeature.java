package com.qubular.vicare.model.features;

import com.qubular.vicare.model.DimensionalValue;
import com.qubular.vicare.model.Feature;

import java.util.List;
import java.util.Map;

public class StatisticsFeature extends Feature {
    private final Map<String, DimensionalValue> statistics;

    public StatisticsFeature(String name, Map<String, DimensionalValue> statistics) {
        super(name);
        this.statistics = Map.copyOf(statistics);
    }

    public Map<String, DimensionalValue> getStatistics() {
        return statistics;
    }

    @Override
    public void accept(Visitor v) {
        v.visit(this);
    }
}
