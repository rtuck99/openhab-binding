package com.qubular.vicare.model.params;

import com.qubular.vicare.model.EnumParamDescriptor;

public abstract class ParamDescriptor<T> {
    public interface Visitor{
        void visit(EnumParamDescriptor d);
    }

    private final boolean required;
    private final String name;

    public ParamDescriptor(boolean required, String name) {
        this.required = required;
        this.name = name;
    }

    public abstract Class<T> getType();

    public abstract boolean validate(T t);

    public boolean isRequired() {
        return required;
    }

    public String getName() {
        return name;
    }

    public abstract void accept(Visitor v);
}
