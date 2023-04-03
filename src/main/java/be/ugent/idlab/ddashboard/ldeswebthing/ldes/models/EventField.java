package be.ugent.idlab.ddashboard.ldeswebthing.ldes.models;

public enum EventField {
    TIMESTAMP("timestamp"),
    DATASET("dataset"),
    METRIC("metric"),
    PRODUCER("producer"),
    SOURCE("source"),
    VALUE("value"),
    TAGS("tags"),
    LOCATION("location"),
    GEOHASH("geohash"),
    ELEVATION("elevation"),
    TS_RECEIVED("tsReceived");

    public final String label;

    private EventField(String label) {
        this.label = label;
    }
}
