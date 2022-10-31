package com.qubular.vicare.model.features;

import com.qubular.vicare.model.CommandDescriptor;
import com.qubular.vicare.model.values.DimensionalValue;
import com.qubular.vicare.model.Feature;
import com.qubular.vicare.model.Value;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MultiValueFeature extends Feature {
    private final Map<String, DimensionalValue> properties;

    public MultiValueFeature(String name, Map<String, DimensionalValue> values) {
        this(name, Collections.emptyList(), values);
    }

    public MultiValueFeature(String name, List<CommandDescriptor> commands, Map<String, DimensionalValue> values) {
        super(name, commands);
        this.properties = Map.copyOf(values);
    }

    public Map<String, DimensionalValue> getValues() {
        return properties;
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
