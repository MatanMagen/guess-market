package guessmarket.dto;

/**
 * One user as the user list shows them. A user is {@code blocked} once an action has taken their
 * balance below zero, and from then on the engine refuses everything they ask for.
 */
public record UserSummary(int displayNumber, String name, double balance, boolean blocked, int activeEventCount) {
}
