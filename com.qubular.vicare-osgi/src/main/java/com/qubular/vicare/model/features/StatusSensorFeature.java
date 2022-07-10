package com.qubular.vicare.model.features;

import com.qubular.vicare.model.Feature;
import com.qubular.vicare.model.Status;

public class StatusSensorFeature extends Feature {
    private final Status status;

    public StatusSensorFeature(String name, Status status) {
        super(name);
        this.status = status;
    }

    public Status getStatus() {
        return status;
    }

    @Override
    public void accept(Visitor v) {
        v.visit(this);
    }
}
