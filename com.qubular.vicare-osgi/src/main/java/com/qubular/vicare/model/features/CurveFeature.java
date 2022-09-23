package com.qubular.vicare.model.features;

import com.qubular.vicare.model.DimensionalValue;
import com.qubular.vicare.model.Feature;

public class CurveFeature extends Feature {
    private final DimensionalValue slope;
    private final DimensionalValue shift;

    public CurveFeature(String name, DimensionalValue slope, DimensionalValue shift) {
        super(name);
        this.slope = slope;
        this.shift = shift;
    }

    public DimensionalValue getSlope() {
        return slope;
    }

    public DimensionalValue getShift() {
        return shift;
    }

    @Override
    public void accept(Visitor v) {
        v.visit(this);
    }
}
