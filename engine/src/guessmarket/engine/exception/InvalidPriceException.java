package guessmarket.engine.exception;

/**
 * An order named a price outside the range a share can be worth. A share pays d at resolution, so
 * nobody would ever pay d or more for it, and the book will not take such an order.
 */
public class InvalidPriceException extends GuessMarketException {

    private static final long serialVersionUID = 1L;

    private final double requested;
    private final double minimum;
    private final double maximum;

    public InvalidPriceException(double requested, double minimum, double maximum) {
        super("price " + requested + " is outside " + minimum + " to " + maximum);
        this.requested = requested;
        this.minimum = minimum;
        this.maximum = maximum;
    }

    public double requested() {
        return requested;
    }

    public double minimum() {
        return minimum;
    }

    public double maximum() {
        return maximum;
    }
}
