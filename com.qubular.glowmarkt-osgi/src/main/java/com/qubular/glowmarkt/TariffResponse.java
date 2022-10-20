package com.qubular.glowmarkt;

import java.util.List;

public class TariffResponse {
    private List<TariffData> data;

    private String name;

    public TariffResponse() {
    }

    private TariffResponse(List<TariffData> data, String name) {
        this.data = data;
        this.name = name;
    }

    public List<TariffData> getData() {
        return data;
    }

    public static class Builder {
        private List<TariffData> data;
        private String name;

        public Builder withData(List<TariffData> data) {
            this.data = data;
            return this;
        }

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public TariffResponse build() {
            return new TariffResponse(data, name);
        }
    }

    public String getName() {
        return name;
    }
}
