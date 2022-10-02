package com.qubular.vicare.model;

import com.qubular.vicare.model.features.*;

import java.util.Collections;
import java.util.List;

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

    protected final List<CommandDescriptor> commands;

    private String name;

    public Feature(String name) {
        this(name, Collections.emptyList());
    }

    public Feature(String name, List<CommandDescriptor> commands) {
        this.name = name;
        this.commands = commands;
    }

    public List<CommandDescriptor> getCommands() {
        return commands;
    }

    public String getName() {
        return name;
    }

    public abstract void accept(Visitor v);
}
