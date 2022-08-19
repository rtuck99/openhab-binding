package com.qubular.vicare.model;

import java.util.Objects;

public class Status {
    public static final Status NA = new Status("N/A");
    public static final Status ON = new Status("on");
    public static final Status OFF = new Status("off");

    private final String name;

    public Status(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Status status = (Status) o;
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
}
