package com.qubular.vicare.model.features;

import com.qubular.vicare.model.values.DimensionalValue;
import com.qubular.vicare.model.Feature;

import java.util.Map;

public class CurveFeature extends Feature {
    private final Map<String, DimensionalValue> properties;

    public CurveFeature(String name, DimensionalValue slope, DimensionalValue shift) {
        super(name);
        properties = Map.of("slope", slope,
                            "shift", shift);
    }

    public DimensionalValue getSlope() {
        return properties.get("slope");
    }

    public DimensionalValue getShift() {
        return properties.get("shift");
    }

    @Override
    public Map<String, DimensionalValue> getProperties() {
        return properties;
    }

    @Override
    public void accept(Visitor v) {
        v.visit(this);
    }
}
