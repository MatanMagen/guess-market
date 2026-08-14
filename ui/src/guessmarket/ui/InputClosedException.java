package guessmarket.ui;

/**
 * There is no more input: the console was fed from a file or a pipe that ran out, or the user
 * pressed Ctrl-Z. Treated as a request to quit rather than letting a NoSuchElementException
 * kill the application.
 */
public class InputClosedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    InputClosedException() {
        super("input has been closed");
    }
}
