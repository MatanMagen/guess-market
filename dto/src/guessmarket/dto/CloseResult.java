package guessmarket.dto;

import java.util.List;

/**
 * How an event settled. {@code payouts} names every user who was paid and what they took home
 * after commission; {@code subsidyReturned} is the LMSR leftover handed back to the market maker,
 * and is 0 for an order book event, whose account always empties exactly.
 */
public record CloseResult(String winningOutcomeName,
                          long winningShares,
                          double grossPayout,
                          double commissionCharged,
                          double netPaidToWinners,
                          double subsidyReturned,
                          boolean commissionAppliedOnClose,
                          List<Payout> payouts,
                          EventState stateAfterClose) {

    public CloseResult {
        payouts = List.copyOf(payouts);
    }

    public record Payout(String userName, long winningShares, double gross, double commission, double net) {
    }
}
