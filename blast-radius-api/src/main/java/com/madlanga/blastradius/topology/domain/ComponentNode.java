package com.madlanga.blastradius.topology.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ComponentNode {
    private final String id;
    private final String name;
    private final ComponentType type;
    private final String technology;
    private final Map<String, String> metadata;

    private ComponentNode(Builder builder) {
        this.id = requireText(builder.id, "id");
        this.name = requireText(builder.name, "name");
        this.type = Objects.requireNonNull(builder.type, "type must not be null");
        this.technology = normalizeOptional(builder.technology);
        this.metadata = Collections.unmodifiableMap(new LinkedHashMap<>(builder.metadata));
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public ComponentType getType() { return type; }
    public String getTechnology() { return technology; }
    public Map<String, String> getMetadata() { return metadata; }

    public static Builder builder() { return new Builder(); }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static final class Builder {
        private String id;
        private String name;
        private ComponentType type = ComponentType.UNKNOWN;
        private String technology;
        private Map<String, String> metadata = new LinkedHashMap<>();

        private Builder() {}

        public Builder id(String id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder type(ComponentType type) { this.type = type; return this; }
        public Builder technology(String technology) { this.technology = technology; return this; }
        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
            return this;
        }
        public ComponentNode build() { return new ComponentNode(this); }
    }
}
