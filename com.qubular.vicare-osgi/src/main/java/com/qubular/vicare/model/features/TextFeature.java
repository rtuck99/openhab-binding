package com.qubular.vicare.model.features;

import com.qubular.vicare.model.CommandDescriptor;
import com.qubular.vicare.model.Feature;
import com.qubular.vicare.model.values.StringValue;
import com.qubular.vicare.model.Value;

import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;

public class TextFeature extends Feature {
    private final String value;

    public TextFeature(String name, String value) {
        this(name, value, emptyList());
    }

    public TextFeature(String name, String value, List<CommandDescriptor> commands) {
        super(name, commands);
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public Map<String, ? extends Value> getProperties() {
        return Map.of("value", new StringValue(value));
    }

    @Override
    public void accept(Visitor v) {
        v.visit(this);
    }

}
