package com.qubular.vicare.model.features;

import com.qubular.vicare.model.CommandDescriptor;
import com.qubular.vicare.model.Feature;
import com.qubular.vicare.model.values.StringValue;
import com.qubular.vicare.model.Value;

import java.util.List;
import java.util.Map;

import static java.util.Collections.*;

public class TextFeature extends Feature {
    private final Map<String, StringValue> value;

    public TextFeature(String name, String propName, String value) {
        this(name, propName, value, emptyList());
    }

    public TextFeature(String name, String propName, String value, List<CommandDescriptor> commands) {
        super(name, commands);
        this.value = value == null ? emptyMap() : singletonMap(propName, new StringValue(value));
    }

    @Deprecated
    public String getValue() {
        return value.values().stream().findFirst().map(StringValue::getValue).orElse(null);
    }

    @Override
    public Map<String, ? extends Value> getProperties() {
        return value;
    }

    @Override
    public void accept(Visitor v) {
        v.visit(this);
    }

}
