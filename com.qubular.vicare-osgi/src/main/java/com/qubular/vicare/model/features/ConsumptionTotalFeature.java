package com.qubular.vicare.model.features;

import com.qubular.vicare.model.values.ArrayValue;
import com.qubular.vicare.model.values.DimensionalValue;
import com.qubular.vicare.model.Value;

import java.util.Map;
import java.util.Optional;

import static java.util.Optional.empty;
import static java.util.Optional.of;

public class ConsumptionTotalFeature extends ConsumptionFeature {
    private final Map<String, Value> properties;

    public ConsumptionTotalFeature(String name, Map<String, Value> properties) {
        super(name);
        this.properties = properties;
    }

    @Override
    public Map<String, ? extends Value> getProperties() {
        return properties;
    }

    @Override
    public void accept(Visitor v) {
        v.visit(this);
    }

    @Override
    public Optional<DimensionalValue> getConsumption(Stat stat) {
        Value value;
        switch(stat) {
            case CURRENT_DAY:
            case PREVIOUS_DAY:
                value = properties.get("day");
                break;
            case CURRENT_WEEK:
            case PREVIOUS_WEEK:
                value = properties.get("week");
                break;
            case CURRENT_MONTH:
            case PREVIOUS_MONTH:
                value = properties.get("month");
                break;
            case CURRENT_YEAR:
            case PREVIOUS_YEAR:
                value = properties.get("year");
                break;
            default:
                return empty();
        }
        if (value instanceof ArrayValue) {
            ArrayValue arrayValue = (ArrayValue) value;
            switch(stat) {
                case CURRENT_DAY:
                case CURRENT_WEEK:
                case CURRENT_MONTH:
                case CURRENT_YEAR:
                    if (arrayValue.getValues().length > 0) {
                        return of(new DimensionalValue(arrayValue.getUnit(), arrayValue.getValues()[0]));
                    }
                    break;
                case PREVIOUS_DAY:
                case PREVIOUS_WEEK:
                case PREVIOUS_MONTH:
                case PREVIOUS_YEAR:
                    if (arrayValue.getValues().length > 1) {
                        return of(new DimensionalValue(arrayValue.getUnit(), arrayValue.getValues()[1]));
                    }
            }
        }
        return empty();
    }

}
