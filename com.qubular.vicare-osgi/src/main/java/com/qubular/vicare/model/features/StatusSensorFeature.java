package com.qubular.vicare.model.features;

import com.qubular.vicare.model.values.BooleanValue;
import com.qubular.vicare.model.Feature;
import com.qubular.vicare.model.values.StatusValue;
import com.qubular.vicare.model.Value;

import java.util.HashMap;
import java.util.Map;

public class StatusSensorFeature extends Feature {
    private final Map<String, Value> properties;

    public StatusSensorFeature(String name, Map<String, Value> values) {
        super(name);
        properties = values;
    }

    public StatusSensorFeature(String name, StatusValue status, Boolean active) {
        this(name, propertyMap(status, active));
    }

    private static Map<String, Value> propertyMap(StatusValue status, Boolean active) {
        final Map<String, Value> properties;
        properties = new HashMap<>();
        properties.put("status", status);
        if (active != null) {
            properties.put("active", BooleanValue.valueOf(active));
        }
        return properties;
    }

    public StatusValue getStatus() {
        return (StatusValue) properties.get("status");
    }

    public Boolean isActive() {
        BooleanValue active = (BooleanValue) properties.get("active");
        return active == null ? null : active.getValue();
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
