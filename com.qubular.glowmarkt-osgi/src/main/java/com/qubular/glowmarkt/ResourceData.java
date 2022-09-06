package com.qubular.glowmarkt;

import java.time.Instant;

public class ResourceData {
    private final double reading;
    private final Instant timestamp;

    public ResourceData(double reading, Instant timestamp) {
        this.reading = reading;
        this.timestamp = timestamp;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public double getReading() {
        return reading;
    }
}
