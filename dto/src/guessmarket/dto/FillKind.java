package guessmarket.dto;

/**
 * How a fill came about. A TRADE moves existing shares between two users; a MINT creates new ones
 * out of cash paid into the event's account, so it has a buyer but no seller.
 */
public enum FillKind {
    TRADE,
    MINT
}
