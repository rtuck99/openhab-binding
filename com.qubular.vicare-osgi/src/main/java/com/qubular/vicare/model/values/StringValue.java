package com.qubular.vicare.model.values;

import com.qubular.vicare.model.Value;

import java.util.Objects;

public class StringValue extends Value {
    private final String value;

    public StringValue(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String getType() {
        return TYPE_STRING;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StringValue that = (StringValue) o;
        return Objects.equals(value, that.value);
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
