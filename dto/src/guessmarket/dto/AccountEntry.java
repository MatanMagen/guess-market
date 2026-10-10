package guessmarket.dto;

/**
 * One line of a user's account. {@code amount} is signed: money in is positive, money out negative.
 * {@code eventName} is null for a movement that belongs to no event, such as a deposit.
 */
public record AccountEntry(int number,
                           long timeMillis,
                           EntryKind kind,
                           String eventName,
                           double amount,
                           double balanceAfter) {
}
