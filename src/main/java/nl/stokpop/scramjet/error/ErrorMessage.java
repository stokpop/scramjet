package nl.stokpop.scramjet.error;

public record ErrorMessage(String developerMessage, String userMessage, int errorCode) {
}
