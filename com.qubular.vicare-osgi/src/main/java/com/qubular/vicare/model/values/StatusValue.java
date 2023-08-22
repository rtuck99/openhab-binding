package com.qubular.vicare.model.values;

import com.qubular.vicare.model.Value;

import java.util.Objects;

public class StatusValue extends Value {
    public static final StatusValue NA = new StatusValue("N/A");
    public static final StatusValue ON = new StatusValue("on");
    public static final StatusValue OFF = new StatusValue("off");
    public static final StatusValue NOT_CONNECTED = new StatusValue("notConnected");
    public static final StatusValue CONNECTED = new StatusValue("connected");

    private final String name;

    public StatusValue(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StatusValue status = (StatusValue) o;
        return Objects.equals(name, status.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return "Status{" +
                "name='" + name + '\'' +
                '}';
    }

    @Override
    public String getType() {
        return TYPE_STRING;
    }

    @Override
    public void accept(Visitor v) {
        v.visit(this);
    }
}
