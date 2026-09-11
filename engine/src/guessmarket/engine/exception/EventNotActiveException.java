package guessmarket.engine.exception;

import guessmarket.dto.EventLifecycle;

/** Trading was asked for on an event that has not been opened yet, or has already been resolved. */
public class EventNotActiveException extends GuessMarketException {

    private static final long serialVersionUID = 1L;

    private final String eventName;
    private final EventLifecycle status;

    public EventNotActiveException(String eventName, EventLifecycle status) {
        super("event '" + eventName + "' is " + status);
        this.eventName = eventName;
        this.status = status;
    }

    public String eventName() {
        return eventName;
    }

    public EventLifecycle status() {
        return status;
    }
}
