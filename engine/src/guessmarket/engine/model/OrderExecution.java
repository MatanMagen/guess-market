package guessmarket.engine.model;

import java.io.Serializable;
import java.util.List;

/** What one submitted order actually did. */
public record OrderExecution(long orderId,
                             long quantityFilled,
                             long quantityResting,
                             List<Fill> fills,
                             double cashSpent,
                             double cashReceived,
                             double commissionPaid) implements Serializable {

    public OrderExecution {
        fills = List.copyOf(fills);
    }
}
