package guessmarket.engine.exception;

/** A command needs an event that still accepts trades, and every event has been closed. */
public class NoActiveEventsException extends GuessMarketException {

    private static final long serialVersionUID = 1L;

    public NoActiveEventsException() {
        super("every event has been closed");
    }
}
