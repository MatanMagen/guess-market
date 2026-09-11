package guessmarket.dto;

import java.util.List;

/**
 * One answer's order book. Bids run highest price first, asks lowest first, so the head of each
 * list is the side's best price.
 * <p>
 * The four price readings are null when the book cannot produce them: {@code lastTradePrice}
 * until something trades, {@code mid} and {@code spread} until both sides hold an order.
 */
public record OutcomeBook(int displayNumber,
                          String outcomeName,
                          List<OrderView> bids,
                          List<OrderView> asks,
                          Double lastTradePrice,
                          Double bestBid,
                          Double bestAsk,
                          Double mid,
                          Double spread,
                          long sharesOutstanding) {

    public OutcomeBook {
        bids = List.copyOf(bids);
        asks = List.copyOf(asks);
    }
}
