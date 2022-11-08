package com.qubular.vicare.model.params;

import com.qubular.vicare.model.ParamDescriptor;

public class StringParamDescriptor extends ParamDescriptor<String> {
    public StringParamDescriptor(boolean required, String name) {
        super(required, name);
    }

    @Override
    public Class<String> getType() {
        return String.class;
    }

    @Override
    public boolean validate(String s) {
        return true;
    }

    @Override
    public void accept(Visitor v) {
        v.visit(this);
    }
}
