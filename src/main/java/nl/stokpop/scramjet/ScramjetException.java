package nl.stokpop.scramjet;

public class ScramjetException extends RuntimeException {

    public ScramjetException(final String message) {
        super(message);
    }

    public ScramjetException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
