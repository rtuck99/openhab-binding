package com.qubular.vicare.model;

import com.qubular.vicare.model.features.*;

public abstract class Feature {
    public interface Visitor {
        void visit(ConsumptionFeature f);
        void visit(NumericSensorFeature f);
        void visit(StatisticsFeature f);
        void visit(StatusSensorFeature f);
        void visit(TextFeature f);
        void visit(CurveFeature f);
        void visit(DatePeriodFeature datePeriodFeature);
    }

    private String name;

    public Feature(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public abstract void accept(Visitor v);
}
