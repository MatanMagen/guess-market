package guessmarket.engine.model;

import java.io.Serializable;
import java.util.List;

/**
 * What happened when an event closed. {@code subsidyReturned} is the LMSR leftover handed back to
 * the market maker; an order book event always empties exactly, so there it is 0.
 */
public record Settlement(String winningOutcomeName,
                         long winningShares,
                         double grossPayout,
                         double commissionCharged,
                         double netPaidToWinners,
                         double subsidyReturned,
                         List<Payment> payments) implements Serializable {

    public Settlement {
        payments = List.copyOf(payments);
    }
}
