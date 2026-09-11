package guessmarket.dto;

/** One execution. {@code sellerName} is null on a mint, where the shares did not exist before. */
public record FillRecord(FillKind kind,
                         String outcomeName,
                         String buyerName,
                         String sellerName,
                         long quantity,
                         double price,
                         double buyerCommission) {

    public double value() {
        return quantity * price;
    }
}
