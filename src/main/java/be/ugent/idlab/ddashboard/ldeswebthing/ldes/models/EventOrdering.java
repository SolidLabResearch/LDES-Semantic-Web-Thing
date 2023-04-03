package be.ugent.idlab.ddashboard.ldeswebthing.ldes.models;

public enum EventOrdering {
    ASCENDING("asc"),
    DESCENDING("desc");

    public final String label;

    private EventOrdering(String label) {
        this.label = label;
    }
}
