package com.qubular.vicare.model;

import java.util.List;

public class Installation {
    private final long id;
    private final String description;
    private final List<Gateway> gateways;
    private final String aggregatedStatus;

    public Installation(long id, String description, List<Gateway> gateways, String aggregatedStatus) {
        this.id = id;
        this.description = description;
        this.gateways = gateways;
        this.aggregatedStatus = aggregatedStatus;
    }

    public long getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public List<Gateway> getGateways() {
        return gateways;
    }

    public String getAggregatedStatus() {
        return aggregatedStatus;
    }
}
