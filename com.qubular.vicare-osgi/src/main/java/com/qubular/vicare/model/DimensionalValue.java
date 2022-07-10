package com.qubular.vicare.model;

import java.util.Objects;

public class DimensionalValue {
    private Unit unit;
    private double value;

    public DimensionalValue(Unit unit, double value) {
        this.unit = unit;
        this.value = value;
    }

    public Unit getUnit() {
        return unit;
    }

    public double getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DimensionalValue that = (DimensionalValue) o;
        return Double.compare(that.value, value) == 0 && Objects.equals(unit, that.unit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(unit, value);
    }

    @Override
    public String toString() {
        return "DimensionalValue{" +
                "unit=" + unit +
                ", value=" + value +
                '}';
    }
}
