package guessmarket.engine.model;

import java.io.Serializable;

/** One possible answer of an event, and how many of its shares have been bought. */
public class Outcome implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String name;
    private long sharesBought;

    public Outcome(String name) {
        this.name = name;
        this.sharesBought = 0L;
    }

    public String name() {
        return name;
    }

    public long sharesBought() {
        return sharesBought;
    }

    void addShares(long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("share quantity must be positive: " + quantity);
        }
        sharesBought += quantity;
    }
}
