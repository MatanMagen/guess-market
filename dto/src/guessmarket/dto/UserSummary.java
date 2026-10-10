package guessmarket.dto;

/**
 * One user as the user list shows them. A user is {@code blocked} while an action has left their
 * balance below zero, and until they top it up the engine refuses everything they ask for.
 * {@code marketMaker} is true once they have uploaded at least one event.
 */
public record UserSummary(int displayNumber,
                          String name,
                          double balance,
                          boolean blocked,
                          int activeEventCount,
                          boolean marketMaker) {
}
