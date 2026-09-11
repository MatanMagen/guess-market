package guessmarket.dto;

import java.util.List;

/**
 * The order book side of an event. {@code basePrice} is d: what one winning share pays at
 * resolution, and therefore the ceiling every order price sits under.
 */
public record OrderBookDetails(int basePrice,
                               boolean mintAllowed,
                               int initialInvestment,
                               double commissionCollected,
                               List<OutcomeBook> books) {

    public OrderBookDetails {
        books = List.copyOf(books);
    }
}
