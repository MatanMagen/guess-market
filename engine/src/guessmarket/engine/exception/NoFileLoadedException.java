package guessmarket.engine.exception;

/** A command needs a loaded system file and none has been loaded yet. */
public class NoFileLoadedException extends GuessMarketException {

    private static final long serialVersionUID = 1L;

    public NoFileLoadedException() {
        super("no system file is loaded");
    }
}
