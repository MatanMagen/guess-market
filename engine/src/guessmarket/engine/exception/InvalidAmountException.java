package guessmarket.engine.exception;

/** A deposit was not a positive amount of money. */
public class InvalidAmountException extends GuessMarketException {

    private static final long serialVersionUID = 1L;

    private final double requested;

    public InvalidAmountException(double requested) {
        super("amount " + requested + " is not positive");
        this.requested = requested;
    }

    public double requested() {
        return requested;
    }
}
