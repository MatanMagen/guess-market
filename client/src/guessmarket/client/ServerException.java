package guessmarket.client;

/**
 * The server refused a request, or could not be reached. The message is always a sentence the user
 * can read: the server words its own refusals, and an unreachable server is worded here.
 */
public class ServerException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;

    public ServerException(int status, String message) {
        super(message);
        this.status = status;
    }

    /** The HTTP status, or 0 when the server could not be reached at all. */
    public int status() {
        return status;
    }
}
