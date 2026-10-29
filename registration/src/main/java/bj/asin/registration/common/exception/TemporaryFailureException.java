package bj.asin.registration.common.exception;

public class TemporaryFailureException extends RuntimeException {

    public TemporaryFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
