package guessmarket.dto;

/** What a purchase cost, and how the event stands afterwards. */
public record PurchaseResult(String outcomeName,
                             long quantity,
                             double sharesCost,
                             double commissionPaid,
                             double totalPaid,
                             boolean commissionCharged,
                             EventState stateAfterPurchase) {
}
