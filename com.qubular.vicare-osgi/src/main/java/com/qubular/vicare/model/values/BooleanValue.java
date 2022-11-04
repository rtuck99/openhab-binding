package com.qubular.vicare.model.values;

import com.qubular.vicare.model.Value;

import java.util.Objects;

public class BooleanValue extends Value {
    public static final BooleanValue TRUE = new BooleanValue(true);
    public static final BooleanValue FALSE = new BooleanValue(false);
    private final boolean value;

    private BooleanValue(boolean value) {
        this.value = value;
    }

    public static BooleanValue valueOf(boolean value) {
        return value ? TRUE : FALSE;
    }

    public boolean getValue() {
        return value;
    }

    @Override
    public String getType() {
        return TYPE_BOOLEAN;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BooleanValue that = (BooleanValue) o;
        return value == that.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public void accept(Visitor v) {
        v.visit(this);
    }
}
