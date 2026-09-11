package guessmarket.engine.exception;

/** A user was named that the loaded file does not define. */
public class NoSuchUserException extends GuessMarketException {

    private static final long serialVersionUID = 1L;

    private final String userName;

    public NoSuchUserException(String userName) {
        super("no user named '" + userName + "'");
        this.userName = userName;
    }

    public String userName() {
        return userName;
    }
}
