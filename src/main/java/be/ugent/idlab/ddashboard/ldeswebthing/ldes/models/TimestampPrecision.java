package be.ugent.idlab.ddashboard.ldeswebthing.ldes.models;

public enum TimestampPrecision {
    SECONDS("seconds"),
    MILLISECONDS("milliseconds"),
    MICROSECONDS("microseconds");

    public final String label;

    private TimestampPrecision(String label) {
        this.label = label;
    }
}
