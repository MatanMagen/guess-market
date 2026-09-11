package guessmarket.dto;

/** What an LMSR purchase cost one user, and how the event stands afterwards. */
public record PurchaseResult(String userName,
                             String outcomeName,
                             long quantity,
                             double sharesCost,
                             double commissionPaid,
                             double totalPaid,
                             boolean commissionCharged,
                             EventState stateAfterPurchase) {
}
