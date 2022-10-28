package com.qubular.vicare.model.params;

import com.qubular.vicare.model.ParamDescriptor;

public class NumericParamDescriptor extends ParamDescriptor<Double> {
    private Double min;
    private Double max;

    private Double stepping;

    public NumericParamDescriptor(boolean required, String name, Double min, Double max, Double stepping) {
        super(required, name);
        this.min = min;
        this.max = max;
        this.stepping = stepping;
    }

    @Override
    public Class<Double> getType() {
        return Double.class;
    }

    @Override
    public boolean validate(Double aDouble) {
        return (max == null || aDouble <= max) &&
                (min == null || aDouble >= min);
    }

    @Override
    public void accept(Visitor v) {
        v.visit(this);
    }

    public Double getMin() {
        return min;
    }

    public Double getMax() {
        return max;
    }

    public Double getStepping() {
        return stepping;
    }
}
