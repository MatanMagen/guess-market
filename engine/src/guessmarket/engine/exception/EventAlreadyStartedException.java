package guessmarket.engine.exception;

/** An event can only be opened once, and a closed one can never be reopened. */
public class EventAlreadyStartedException extends GuessMarketException {

    private static final long serialVersionUID = 1L;

    private final String eventName;

    public EventAlreadyStartedException(String eventName) {
        super("event '" + eventName + "' has already been started");
        this.eventName = eventName;
    }

    public String eventName() {
        return eventName;
    }
}
