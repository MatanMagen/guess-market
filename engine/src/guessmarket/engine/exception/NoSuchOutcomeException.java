package guessmarket.engine.exception;

/** An answer number addresses no answer of the chosen event. */
public class NoSuchOutcomeException extends GuessMarketException {

    private static final long serialVersionUID = 1L;

    private final String eventName;
    private final int requested;
    private final int available;

    public NoSuchOutcomeException(String eventName, int requested, int available) {
        super("answer " + requested + " of '" + eventName + "' is outside 1.." + available);
        this.eventName = eventName;
        this.requested = requested;
        this.available = available;
    }

    public String eventName() {
        return eventName;
    }

    public int requested() {
        return requested;
    }

    public int available() {
        return available;
    }
}
