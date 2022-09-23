package com.qubular.vicare.model.features;

import com.qubular.vicare.model.Feature;
import com.qubular.vicare.model.Status;

import java.time.LocalDate;

public class DatePeriodFeature extends Feature {
    private final Status active;
    private final LocalDate start;
    private final LocalDate end;

    public DatePeriodFeature(String name, Status active, LocalDate start, LocalDate end) {
        super(name);
        this.active = active;
        this.start = start;
        this.end = end;
    }

    public Status getActive() {
        return active;
    }

    public LocalDate getStart() {
        return start;
    }

    public LocalDate getEnd() {
        return end;
    }

    @Override
    public void accept(Visitor v) {
        v.visit(this);
    }
}
