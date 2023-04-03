package be.ugent.idlab.ddashboard.ldeswebthing.ldes.models;

public class LDESException extends Exception {

    public LDESException() {

    }
    
    public LDESException(String message) {
        super(message);
    }

    public LDESException(Throwable cause) {
        super(cause);
    }

    public LDESException(String message, Throwable cause) {
        super (message, cause);
    }
}
