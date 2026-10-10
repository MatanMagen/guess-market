package guessmarket.engine.exception;

/** A login gave no name at all. */
public class InvalidUserNameException extends GuessMarketException {

    private static final long serialVersionUID = 1L;

    public InvalidUserNameException() {
        super("a user name must not be blank");
    }
}
