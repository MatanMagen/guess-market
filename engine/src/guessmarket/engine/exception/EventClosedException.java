package guessmarket.engine.exception;

/** A resolved event was asked to trade, or to close a second time. */
public class EventClosedException extends GuessMarketException {

    private static final long serialVersionUID = 1L;

    private final String eventName;

    public EventClosedException(String eventName) {
        super("event '" + eventName + "' is closed");
        this.eventName = eventName;
    }

    public String eventName() {
        return eventName;
    }
}
