package guessmarket.engine.exception;

/**
 * An event number addresses no event in the listing it was chosen from. {@code activeOnly} says
 * whether that listing held only the events still open for trading.
 */
public class NoSuchEventException extends GuessMarketException {

    private static final long serialVersionUID = 1L;

    private final int requested;
    private final int available;
    private final boolean activeOnly;

    public NoSuchEventException(int requested, int available, boolean activeOnly) {
        super("event " + requested + " is outside 1.." + available);
        this.requested = requested;
        this.available = available;
        this.activeOnly = activeOnly;
    }

    public int requested() {
        return requested;
    }

    public int available() {
        return available;
    }

    public boolean activeOnly() {
        return activeOnly;
    }
}
