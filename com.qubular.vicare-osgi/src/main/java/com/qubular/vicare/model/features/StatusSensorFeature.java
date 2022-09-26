package com.qubular.vicare.model.features;

import com.qubular.vicare.model.Feature;
import com.qubular.vicare.model.Status;

public class StatusSensorFeature extends Feature {
    private final Status status;

    private final Boolean active;

    public StatusSensorFeature(String name, Status status, Boolean active) {
        super(name);
        this.status = status;
        this.active = active;
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
