package com.qubular.vicare.model;

import com.qubular.vicare.model.values.*;

public abstract class Value {
    public static final String TYPE_NUMBER = "number";
    public static final String TYPE_STRING = "string";
    public static final String TYPE_ARRAY = "array";
    public static final String TYPE_BOOLEAN = "boolean";

    public interface Visitor{
        void visit(ArrayValue v);

        void visit(BooleanValue v);

        void visit(DimensionalValue v);

        void visit(LocalDateValue v);

        void visit(StatusValue v);

        void visit(StringValue v);
    }

    public abstract String getType();

    public abstract void accept(Visitor v);
}
