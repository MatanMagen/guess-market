package guessmarket.engine.exception;

/**
 * Root of every fault the engine reports.
 * <p>
 * Unchecked on purpose. There are nine of these already and later exercises will add more;
 * checked ones would force every front end to declare or catch each in turn, when what a front
 * end actually wants is one place that turns any engine fault into something readable.
 * <p>
 * The message here is for logs. What the user sees is built by the front end from the getters.
 */
public abstract class GuessMarketException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    protected GuessMarketException(String diagnostic) {
        super(diagnostic);
    }

    protected GuessMarketException(String diagnostic, Throwable cause) {
        super(diagnostic, cause);
    }
}
