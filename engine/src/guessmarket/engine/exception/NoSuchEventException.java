package guessmarket.engine.exception;

/** An event number was named that the loaded file does not define. */
public class NoSuchEventException extends GuessMarketException {

    private static final long serialVersionUID = 1L;

    private final int eventId;

    public NoSuchEventException(int eventId) {
        super("no event with id " + eventId);
        this.eventId = eventId;
    }

    public int eventId() {
        return eventId;
    }
}
