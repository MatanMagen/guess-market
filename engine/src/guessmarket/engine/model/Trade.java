package guessmarket.engine.model;

import java.io.Serializable;

/**
 * A completed purchase. Immutable, so an event's history is an append only log.
 * {@code commissionPaid} is 0 for events that charge on close.
 */
public record Trade(String outcomeName, long quantity, double sharesCost, double commissionPaid)
        implements Serializable {

    public double totalPaid() {
        return sharesCost + commissionPaid;
    }
}
