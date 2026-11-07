package tech.cloudfocus.ReferenceData.model;

public class Collection {

    private String name;
    private String kind;
    private String version;
    private int count;

    public Collection() {
    }

    public Collection(String name, String kind, String version, int count) {
        this.name = name;
        this.kind = kind;
        this.version = version;
        this.count = count;
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

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }
}
