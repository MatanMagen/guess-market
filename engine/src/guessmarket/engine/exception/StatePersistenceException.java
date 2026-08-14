package guessmarket.engine.exception;

/** A saved system state could not be written, or could not be read back. */
public class StatePersistenceException extends GuessMarketException {

    private static final long serialVersionUID = 1L;

    public enum Direction {
        SAVING,
        LOADING
    }

    private final Direction direction;
    private final String reason;

    public StatePersistenceException(Direction direction, String reason, Throwable cause) {
        super(direction + " the state failed: " + reason, cause);
        this.direction = direction;
        this.reason = reason;
    }

    public Direction direction() {
        return direction;
    }

    /** Short enough to read as the tail of a sentence. */
    public String reason() {
        return reason;
    }
}
