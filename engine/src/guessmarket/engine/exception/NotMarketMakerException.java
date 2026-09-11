package guessmarket.engine.exception;

/** Opening and closing an event belong to its market maker alone. */
public class NotMarketMakerException extends GuessMarketException {

    private static final long serialVersionUID = 1L;

    private final String eventName;
    private final String userName;
    private final String marketMakerName;

    public NotMarketMakerException(String eventName, String userName, String marketMakerName) {
        super("'" + userName + "' is not the market maker of event '" + eventName + "'");
        this.eventName = eventName;
        this.userName = userName;
        this.marketMakerName = marketMakerName;
    }

    public String eventName() {
        return eventName;
    }

    public String userName() {
        return userName;
    }

    public String marketMakerName() {
        return marketMakerName;
    }
}
