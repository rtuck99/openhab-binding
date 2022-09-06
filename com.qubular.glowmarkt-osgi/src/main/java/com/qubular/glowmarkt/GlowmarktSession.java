package com.qubular.glowmarkt;

import java.time.Instant;

public class GlowmarktSession {
    private String token;
    private Instant expiry;

    public GlowmarktSession(Instant exp, String token) {
        this.expiry = exp;
        this.token = token;
    }

    public String getToken() {
        return token;
    }

    public Instant getExpiry() {
        return expiry;
    }
}
