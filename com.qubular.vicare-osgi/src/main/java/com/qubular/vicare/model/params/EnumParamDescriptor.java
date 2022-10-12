package com.qubular.vicare.model.params;

import com.qubular.vicare.model.ParamDescriptor;

import java.util.Set;

public class EnumParamDescriptor extends ParamDescriptor<String> {
    private final Set<String> allowedValues;

    public EnumParamDescriptor(boolean required, String name, Set<String> allowedValues) {
        super(required, name);
        this.allowedValues = allowedValues;
    }

    @Override
    public Class<String> getType() {
        return String.class;
    }

    @Override
    public boolean validate(String s) {
        return allowedValues.contains(s);
    }

    @Override
    public void accept(Visitor v) {
        v.visit(this);
    }

    public Set<String> getAllowedValues() {
        return allowedValues;
    }
}
