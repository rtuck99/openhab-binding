package com.qubular.vicare.model.features;

import com.qubular.vicare.model.CommandDescriptor;
import com.qubular.vicare.model.DimensionalValue;
import com.qubular.vicare.model.Feature;
import com.qubular.vicare.model.Status;

import java.util.Collections;
import java.util.List;

public class NumericSensorFeature extends Feature {
    private final DimensionalValue value;
    private final Status status;
    private final Boolean active;
    private final String propertyName;

    public NumericSensorFeature(String name, String propertyName, List<CommandDescriptor> commands, DimensionalValue value, Status status, Boolean active) {
        super(name, commands);
        this.value = value;
        this.status = status;
        this.active = active;
        this.propertyName = propertyName;
    }

    public NumericSensorFeature(String name, String propertyName, DimensionalValue value, Status status, Boolean active) {
        this(name, propertyName, Collections.emptyList(), value, status, active);
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

    public String getPropertyName() {
        return propertyName;
    }

    @Override
    public void accept(Visitor v) {
        v.visit(this);
    }
}
