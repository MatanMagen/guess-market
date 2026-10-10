package guessmarket.engine.exception;

/** Somebody tried to log in under a name another user already holds. */
public class UserAlreadyExistsException extends GuessMarketException {

    private static final long serialVersionUID = 1L;

    private final String userName;

    public UserAlreadyExistsException(String userName) {
        super("user '" + userName + "' already exists");
        this.userName = userName;
    }

    public String userName() {
        return userName;
    }
}
