package com.qubular.glowmarkt;

public class Resource {
    private String resourceId;
    private String classifier;
    private String name;
    private String description;
    private String baseUnit;
    private boolean active;

    public Resource() {
    }

    private Resource(String resourceId, String classifier, String name, String description, String baseUnit, boolean active) {
        this.resourceId = resourceId;
        this.classifier = classifier;
        this.name = name;
        this.description = description;
        this.baseUnit = baseUnit;
        this.active = active;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getClassifier() {
        return classifier;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getBaseUnit() {
        return baseUnit;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isConsumption() {
        return classifier != null && classifier.endsWith(".consumption");
    }

    public static class Builder {
        private String resourceId;
        private String classifier;
        private String name;
        private String description;
        private String baseUnit;
        private boolean active;

        public Builder withResourceId(String resourceId) {
            this.resourceId = resourceId;
            return this;
        }

        public Builder withClassifier(String classifier) {
            this.classifier = classifier;
            return this;
        }

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder withDescription(String description) {
            this.description = description;
            return this;
        }

        public Builder withBaseUnit(String baseUnit) {
            this.baseUnit = baseUnit;
            return this;
        }

        public Builder withActive(boolean active) {
            this.active = active;
            return this;
        }

        public Resource build() {
            return new Resource(resourceId, classifier, name, description, baseUnit, active);
        }
    }
}
