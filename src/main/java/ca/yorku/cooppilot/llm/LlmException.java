package ca.yorku.cooppilot.llm;

/** The provider could not be reached or rejected the request. Never contains the API key. */
public class LlmException extends RuntimeException {

    private final int status;

    public LlmException(String message, int status) {
        super(message);
        this.status = status;
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
        this.status = -1;
    }

    /** HTTP status, or -1 for network errors. 429 and 5xx are worth retrying. */
    public int status() {
        return status;
    }

    public boolean retryable() {
        return status == -1 || status == 429 || status >= 500;
    }
}
