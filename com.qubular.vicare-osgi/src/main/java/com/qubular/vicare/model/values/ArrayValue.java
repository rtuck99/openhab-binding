package com.qubular.vicare.model.values;

import com.qubular.vicare.model.Unit;
import com.qubular.vicare.model.Value;

public class ArrayValue extends Value {
    @Override
    public String getType() {
        return TYPE_ARRAY;
    }

    private double[] values;

    private final Unit unit;

    public ArrayValue(Unit unit, double[] values) {
        this.unit = unit;
        this.values = values;
    }

    public double[] getValues() {
        return values;
    }

    public Unit getUnit() {
        return unit;
    }
}
