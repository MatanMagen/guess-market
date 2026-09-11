package guessmarket.dto;

import java.util.List;

/**
 * What became of one submitted order: what it executed against, and how much of it is left
 * resting in the book. A rejected order never gets this far — that is an exception instead.
 */
public record OrderResult(long orderId,
                          String outcomeName,
                          OrderSide side,
                          long quantitySubmitted,
                          long quantityFilled,
                          long quantityResting,
                          List<FillRecord> fills,
                          double cashSpent,
                          double cashReceived,
                          double commissionPaid,
                          EventState stateAfterOrder) {

    public OrderResult {
        fills = List.copyOf(fills);
    }
}
