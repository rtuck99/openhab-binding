package com.qubular.vicare.model.features;

import com.qubular.vicare.model.*;
import com.qubular.vicare.model.values.BooleanValue;
import com.qubular.vicare.model.values.LocalDateValue;
import com.qubular.vicare.model.values.StatusValue;

import java.time.LocalDate;
import java.util.Map;

public class DatePeriodFeature extends Feature {
    private final Map<String, Value> properties;

    public DatePeriodFeature(String name, boolean active, LocalDate start, LocalDate end) {
        super(name);
        properties = Map.of("active", BooleanValue.valueOf(active),
                            "start", LocalDateValue.of(start),
                            "end", LocalDateValue.of(end));
    }

    public StatusValue getActive() {
        return BooleanValue.TRUE.equals(properties.get("active")) ? StatusValue.ON : StatusValue.OFF;
    }

    public LocalDate getStart() {
        return ((LocalDateValue) properties.get("start")).getValue();
    }

    public LocalDate getEnd() {
        return ((LocalDateValue) properties.get("end")).getValue();
    }

    @Override
    public Map<String, ? extends Value> getProperties() {
        return properties;
    }

    @Override
    public void accept(Visitor v) {
        v.visit(this);
    }
}
