package nl.stokpop.scramjet.error;

import nl.stokpop.scramjet.ScramjetException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.ArrayList;
import java.util.List;

@ControllerAdvice
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);

    private static final ResponseEntity<Object> systemBusyResponse;

    static {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Retry-After", "120");
        systemBusyResponse = ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .headers(HttpHeaders.readOnlyHttpHeaders(headers))
                .body("Sorry, system is busy, please try again in a little while.");
    }

    @ExceptionHandler(OutOfResourcesException.class)
    public ResponseEntity<Object> handleOutOfResources(OutOfResourcesException exception, final WebRequest request) {
        log.warn("Resources depleted [{}] with message: {}", request.getDescription(true), exception.getMessage());
        return systemBusyResponse;
    }

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<Object> handleGeneralException(final Exception ex, final WebRequest request) {
        String userMessage = "The scramjet failed for the following reason: %s".formatted(ex.getMessage());

        HttpStatus returnCode = HttpStatus.INTERNAL_SERVER_ERROR;

        final String devMessage;
        if (ex instanceof ScramjetException) {
            devMessage = "%s: %s".formatted(ex.getClass().getSimpleName(), ex.getMessage());
            log.error(devMessage);
        } else {
            devMessage = "%s: %s".formatted(ex.getClass().getSimpleName(), exceptionMessageChain(ex));
            log.error(devMessage, ex);
        }

        ErrorMessage errorMessage = new ErrorMessage(devMessage, userMessage, returnCode.value());
        return handleExceptionInternal(ex, errorMessage, new HttpHeaders(), returnCode, request);
    }

    private static List<String> exceptionMessageChain(Throwable throwable) {
        List<String> result = new ArrayList<>();
        while (throwable != null) {
            result.add(throwable.toString());
            throwable = throwable.getCause();
        }
        return result;
    }
}
