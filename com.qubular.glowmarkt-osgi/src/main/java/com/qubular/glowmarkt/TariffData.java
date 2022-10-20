package com.qubular.glowmarkt;

import java.time.LocalDateTime;
import java.util.List;

public class TariffData {
    private LocalDateTime from;
    private List<TariffStructure> structure;

    public TariffData() {
    }

    private TariffData(LocalDateTime from, List<TariffStructure> structure) {
        this.from = from;
        this.structure = structure;
    }

    public LocalDateTime getFrom() {
        return from;
    }

    public List<TariffStructure> getStructure() {
        return structure;
    }

    public static class Builder {
        private LocalDateTime from;
        private List<TariffStructure> structure;

        public Builder withFrom(LocalDateTime from) {
            this.from = from;
            return this;
        }

        public Builder withStructure(List<TariffStructure> structure) {
            this.structure = structure;
            return this;
        }

        public TariffData build() {
            return new TariffData(from, structure);
        }
    }
}
