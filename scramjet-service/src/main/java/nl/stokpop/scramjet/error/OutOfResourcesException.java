package nl.stokpop.scramjet.error;

public class OutOfResourcesException extends RuntimeException {

    public OutOfResourcesException(String message) {
        super(message);
    }
}
