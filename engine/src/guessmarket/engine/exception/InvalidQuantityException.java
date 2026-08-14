package guessmarket.engine.exception;

/** A share quantity was not positive. */
public class InvalidQuantityException extends GuessMarketException {

    private static final long serialVersionUID = 1L;

    private final long requested;
    private final long minimum;

    public InvalidQuantityException(long requested, long minimum) {
        super("quantity " + requested + " is below the minimum of " + minimum);
        this.requested = requested;
        this.minimum = minimum;
    }

    public long requested() {
        return requested;
    }

    public long minimum() {
        return minimum;
    }
}
