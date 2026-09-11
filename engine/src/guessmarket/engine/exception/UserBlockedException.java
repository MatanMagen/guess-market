package guessmarket.engine.exception;

/**
 * A user whose balance has been taken below zero asked to act. The exercise bars him from then
 * on, and there is no way to put money back into an account, so this is permanent.
 */
public class UserBlockedException extends GuessMarketException {

    private static final long serialVersionUID = 1L;

    private final String userName;
    private final double balance;

    public UserBlockedException(String userName, double balance) {
        super("user '" + userName + "' is blocked on a balance of " + balance);
        this.userName = userName;
        this.balance = balance;
    }

    public String userName() {
        return userName;
    }

    public double balance() {
        return balance;
    }
}
