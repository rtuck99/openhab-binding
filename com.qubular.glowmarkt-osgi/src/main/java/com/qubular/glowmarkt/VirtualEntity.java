package com.qubular.glowmarkt;

import java.util.List;

public class VirtualEntity {
    private String name;
    private String veId;
    private String veTypeId;
    private List<Resource> resources;

    public VirtualEntity() {
    }

    private VirtualEntity(String name, String veId, String veTypeId, List<Resource> resources) {
        this.name = name;
        this.veId = veId;
        this.veTypeId = veTypeId;
        this.resources = resources;
    }

    public String getName() {
        return name;
    }

    public String getVirtualEntityId() {
        return veId;
    }

    public String getVirtualEntityTypeId() {
        return veTypeId;
    }

    public List<Resource> getResources() {
        return resources;
    }

    public static class Builder {
        private String name;
        private String veId;
        private String veTypeId;
        private List<Resource> resources;

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder withVeId(String veId) {
            this.veId = veId;
            return this;
        }

        public Builder withVeTypeId(String veTypeId) {
            this.veTypeId = veTypeId;
            return this;
        }

        public Builder withResources(List<Resource> resources) {
            this.resources = resources;
            return this;
        }

        public VirtualEntity build() {
            return new VirtualEntity(name, veId, veTypeId, resources);
        }
    }
}
