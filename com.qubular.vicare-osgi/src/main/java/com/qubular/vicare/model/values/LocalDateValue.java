package com.qubular.vicare.model.values;

import com.qubular.vicare.model.Value;

import java.time.LocalDate;
import java.util.Objects;

public class LocalDateValue extends Value {
    private final LocalDate value;

    public LocalDateValue(LocalDate value) {
        this.value = value;
    }

    @Override
    public String getType() {
        return TYPE_STRING;
    }

    public LocalDate getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LocalDateValue that = (LocalDateValue) o;
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
