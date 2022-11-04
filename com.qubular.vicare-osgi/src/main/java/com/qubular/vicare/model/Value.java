package com.qubular.vicare.model;

public abstract class Value {
    public static final String TYPE_NUMBER = "number";
    public static final String TYPE_STRING = "string";
    public static final String TYPE_ARRAY = "array";
    public static final String TYPE_BOOLEAN = "boolean";

    public interface Visitor{
        void 
    }

    public abstract String getType();

    public abstract void accept(Visitor v);
}
