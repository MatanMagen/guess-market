package guessmarket.engine.exception;

/** Somebody offered to sell more shares than he holds, counting what he already has on offer. */
public class InsufficientSharesException extends GuessMarketException {

    private static final long serialVersionUID = 1L;

    private final String userName;
    private final String outcomeName;
    private final long requested;
    private final long available;

    public InsufficientSharesException(String userName, String outcomeName, long requested, long available) {
        super("user '" + userName + "' offered " + requested + " of '" + outcomeName + "' holding " + available);
        this.userName = userName;
        this.outcomeName = outcomeName;
        this.requested = requested;
        this.available = available;
    }

    public String userName() {
        return userName;
    }

    public String outcomeName() {
        return outcomeName;
    }

    public long requested() {
        return requested;
    }

    public long available() {
        return available;
    }
}
