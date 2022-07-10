package com.qubular.vicare.model.features;

import com.qubular.vicare.model.Feature;

public class TextFeature extends Feature {
    private final String value;

    public TextFeature(String name, String value) {
        super(name);
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public void accept(Visitor v) {
        v.visit(this);
    }
}
