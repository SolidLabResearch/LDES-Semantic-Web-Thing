package be.ugent.idlab.ddashboard.ldeswebthing.ldes.models;

public enum IngestMode {
    DEFAULT("default"), // Both stream and store
    STREAM_ONLY("stream_only"),
    STORE_ONLY("store_only");
    
    public final String label;

    private IngestMode(String label) {
        this.label = label;
    }
}
