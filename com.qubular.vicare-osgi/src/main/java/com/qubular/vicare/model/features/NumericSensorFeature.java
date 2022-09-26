package com.qubular.vicare.model.features;

import com.qubular.vicare.model.DimensionalValue;
import com.qubular.vicare.model.Feature;
import com.qubular.vicare.model.Status;

public class NumericSensorFeature extends Feature {
    private final DimensionalValue value;
    private final Status status;

    private final Boolean active;

    public NumericSensorFeature(String name, DimensionalValue value, Status status, Boolean active) {
        super(name);
        this.value = value;
        this.status = status;
        this.active = active;
    }

    public DimensionalValue getValue() {
        return value;
    }

    public Status getStatus() {
        return status;
    }

    public Boolean isActive() {
        return active;
    }

    @Override
    public void accept(Visitor v) {
        v.visit(this);
    }
}
