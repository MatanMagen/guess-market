package guessmarket.engine.exception;

import guessmarket.dto.MarketMethod;

/** An order book action was asked of an LMSR event, or the other way round. */
public class WrongMarketMethodException extends GuessMarketException {

    private static final long serialVersionUID = 1L;

    private final String eventName;
    private final MarketMethod expected;
    private final MarketMethod actual;

    public WrongMarketMethodException(String eventName, MarketMethod expected, MarketMethod actual) {
        super("event '" + eventName + "' is " + actual + ", not " + expected);
        this.eventName = eventName;
        this.expected = expected;
        this.actual = actual;
    }

    public String eventName() {
        return eventName;
    }

    public MarketMethod expected() {
        return expected;
    }

    public MarketMethod actual() {
        return actual;
    }
}
