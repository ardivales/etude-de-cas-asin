package bj.asin.registration.common.exception;

public class BusinessException extends RuntimeException {

    private final String reason;

    public BusinessException(String reason, String message) {
        super(message);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
