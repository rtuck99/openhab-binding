package com.qubular.vicare.model;

import java.util.Objects;

public class Unit {
    public static final Unit CELSIUS = new Unit("celsius");
    public static final Unit KILOWATT_HOUR = new Unit("kilowattHour");
    public static final Unit LITER = new Unit("liter");
    public static final Unit KELVIN = new Unit("kelvin");
    public static final Unit CUBIC_METRES_PER_HOUR = new Unit("cubicMeter/hour");
    public static final Unit HOUR = new Unit("hour");

    private final String name;

    public Unit(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Unit unit = (Unit) o;
        return Objects.equals(name, unit.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return "Unit{" +
                "name='" + name + '\'' +
                '}';
    }
}
