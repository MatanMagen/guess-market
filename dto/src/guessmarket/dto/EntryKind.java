package guessmarket.dto;

/** Why a user's balance moved. */
public enum EntryKind {
    /** The user loaded money into the account. */
    DEPOSIT,
    /** A market maker funded an LMSR event's subsidy when he started it. */
    SUBSIDY_PAID,
    /** A market maker bought an order book event's initial stock when he started it. */
    INITIAL_STOCK,
    SHARES_BOUGHT,
    SHARES_SOLD,
    COMMISSION_PAID,
    /** A market maker's income from his own event. */
    COMMISSION_RECEIVED,
    /** Winning shares paid out when an event closed. */
    WINNINGS,
    /** What was left of an LMSR subsidy, back to the market maker at close. */
    SUBSIDY_RETURNED
}
