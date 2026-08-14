package guessmarket.dto;

/** The current standing of one answer. {@code price} is between 0 and 1. */
public record OutcomeState(int displayNumber, String name, double price, long sharesBought) {
}
