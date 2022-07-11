package com.qubular.vicare.model;

import java.util.List;

public class Installation {
    private long id;
    private String description;
    private List<Gateway> gateways;
    private String aggregatedStatus;

    /** For Gson */
    Installation() {
    }

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
