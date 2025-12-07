package chaos.xcom.launcher.exception;

public class AppException extends RuntimeException {

    private String message;

    public AppException() {
    }

    public AppException(String message) {
        this.message = message;
    }

    public AppException message(String template, Object... args) {
        this.message = String.format(template, args);
        return this;
    }

    public AppException cause(Throwable cause) {
        initCause(cause);
        return this;
    }

    @Override
    public String getMessage() {
        return this.message;
    }
}
