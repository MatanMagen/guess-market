package guessmarket.dto;

/** The current standing of one answer of an LMSR event. {@code price} is between 0 and 1. */
public record OutcomeState(int displayNumber, String name, double price, long sharesBought) {
}
