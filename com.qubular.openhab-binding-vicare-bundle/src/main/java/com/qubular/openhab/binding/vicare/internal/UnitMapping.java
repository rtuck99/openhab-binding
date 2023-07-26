package com.qubular.openhab.binding.vicare.internal;

import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.types.State;

import javax.measure.Unit;
import java.util.HashMap;
import java.util.Map;

public enum UnitMapping {
    CUBIC_METRE(com.qubular.vicare.model.Unit.CUBIC_METRE, tech.units.indriya.unit.Units.CUBIC_METRE, "Number:Volume"),
    KILOWATT_HOUR(com.qubular.vicare.model.Unit.KILOWATT_HOUR,  Units.KILOWATT_HOUR, "Number:Energy"),
    LITRE(com.qubular.vicare.model.Unit.LITRE,  tech.units.indriya.unit.Units.LITRE, "Number:Volume"),
    CELSIUS(com.qubular.vicare.model.Unit.CELSIUS,  tech.units.indriya.unit.Units.CELSIUS, "Number:Temperature"),
    KELVIN(com.qubular.vicare.model.Unit.KELVIN,  tech.units.indriya.unit.Units.KELVIN, "Number:Temperature"),
    HOUR(com.qubular.vicare.model.Unit.HOUR,  tech.units.indriya.unit.Units.HOUR, "Number:Time"),
    ;

    private static final Map<com.qubular.vicare.model.Unit, UnitMapping> mappings = new HashMap<>();
    private final com.qubular.vicare.model.Unit vicareUnit;
    private final Unit<?> openhabUnit;
    private final String itemType;

    UnitMapping(com.qubular.vicare.model.Unit vicareUnit, Unit openhabUnit, String itemType) {
        this.vicareUnit = vicareUnit;
        this.openhabUnit = openhabUnit;
        this.itemType = itemType;
    }

    public static State apiToOpenHab(com.qubular.vicare.model.Unit unit, Double value) {
        UnitMapping unitMapping = unit == null ? null : mappings.get(unit);
        return unitMapping == null ?
                new DecimalType(value) :
                new QuantityType<>(value, unitMapping.openhabUnit);
    }

    public static String apiToItemType(com.qubular.vicare.model.Unit unit) {
        UnitMapping unitMapping = mappings.get(unit);
        return unitMapping != null ?
                unitMapping.itemType :
                "Number";
    }

    static {
        for(UnitMapping unitMapping : values()) {
            mappings.put(unitMapping.vicareUnit, unitMapping);
        }
    }
}
