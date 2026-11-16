package tech.cloudfocus.ReferenceData.model;

import io.swagger.v3.oas.annotations.media.Schema;

public class Collection {

    private String name;
    private String kind;
    private String version;
    private Integer count;
    private boolean enabled = true;

    public Collection() {
    }

    public Collection(String name, String kind, String version, int count) {
        this(name, kind, version, count, true);
    }

    public Collection(String name, String kind, String version, Integer count, boolean enabled) {
        this.name = name;
        this.kind = kind;
        this.version = version;
        this.count = count;
        this.enabled = enabled;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    @Schema(description = "Number of loaded items; null when the collection is disabled",
            types = {"integer", "null"}, format = "int32")
    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    @Schema(description = "Whether this service instance loads and serves the collection")
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
