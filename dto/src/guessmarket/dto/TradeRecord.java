package guessmarket.dto;

/**
 * One line of LMSR trade history. {@code commissionPaid} is 0 for events that charge on close.
 */
public record TradeRecord(String userName,
                          String outcomeName,
                          long quantity,
                          double sharesCost,
                          double commissionPaid,
                          double totalPaid) {
}
