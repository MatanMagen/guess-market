package guessmarket.engine.model;

import java.io.Serializable;

/** One winner's share of the pot when an event resolves. */
public record Payment(String userName, long winningShares, double gross, double commission, double net)
        implements Serializable {
}
