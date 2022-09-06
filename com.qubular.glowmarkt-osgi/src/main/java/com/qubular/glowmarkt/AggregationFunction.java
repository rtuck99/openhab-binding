package com.qubular.glowmarkt;

public enum AggregationFunction {
    SUM("sum");
    private String value;

    AggregationFunction(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
