package guessmarket.engine.exception;

/**
 * Only opening an event is refused for lack of money. Ordinary trading is allowed to take a
 * balance below zero — that is what blocks the user — but a market maker who cannot fund his own
 * event simply cannot start it.
 */
public class InsufficientFundsException extends GuessMarketException {

    private static final long serialVersionUID = 1L;

    private final String userName;
    private final double required;
    private final double available;

    public InsufficientFundsException(String userName, double required, double available) {
        super("user '" + userName + "' needs " + required + " and holds " + available);
        this.userName = userName;
        this.required = required;
        this.available = available;
    }

    public String userName() {
        return userName;
    }

    public double required() {
        return required;
    }

    public double available() {
        return available;
    }
}
