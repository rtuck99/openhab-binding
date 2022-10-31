package com.qubular.vicare.model;

public abstract class Value {
    public static final String TYPE_NUMBER = "number";
    public static final String TYPE_STRING = "string";
    public static final String TYPE_ARRAY = "array";

    public abstract String getType();
}
