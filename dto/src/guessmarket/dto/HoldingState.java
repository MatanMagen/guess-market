package guessmarket.dto;

/** How much of one answer somebody holds, and what they paid for it. */
public record HoldingState(String outcomeName, long quantity, double amountPaid) {
}
