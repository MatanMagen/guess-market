package guessmarket.engine.model;

import guessmarket.dto.OrderSide;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The resting orders for one answer. Buy and sell sides are kept apart, each ranked by price and
 * then by arrival, so the order that has waited longest is served first at equal prices.
 * <p>
 * The book only stores and ranks. Deciding what crosses with what, moving shares and moving money
 * is {@link OrderBookEvent}'s job, because a fill can touch the other answer's book too.
 */
public class OrderBook implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Best bid first: highest price, and among equals the one that arrived earliest. */
    private static final Comparator<Order> BID_RANK =
            Comparator.comparingDouble(Order::price).reversed().thenComparingLong(Order::id);

    /** Best ask first: lowest price, and among equals the one that arrived earliest. */
    private static final Comparator<Order> ASK_RANK =
            Comparator.comparingDouble(Order::price).thenComparingLong(Order::id);

    private final String outcomeName;
    /** ArrayList rather than List so the fields are provably serializable. */
    private final ArrayList<Order> bids = new ArrayList<>();
    private final ArrayList<Order> asks = new ArrayList<>();
    private Double lastTradePrice;

    public OrderBook(String outcomeName) {
        this.outcomeName = outcomeName;
    }

    public String outcomeName() {
        return outcomeName;
    }

    public List<Order> bids() {
        return ranked(bids, BID_RANK);
    }

    public List<Order> asks() {
        return ranked(asks, ASK_RANK);
    }

    public Order bestBid() {
        List<Order> ranked = bids();
        return ranked.isEmpty() ? null : ranked.get(0);
    }

    public Order bestAsk() {
        List<Order> ranked = asks();
        return ranked.isEmpty() ? null : ranked.get(0);
    }

    /** Null until something has actually traded on this answer. */
    public Double lastTradePrice() {
        return lastTradePrice;
    }

    public void recordTradePrice(double price) {
        lastTradePrice = price;
    }

    /** Null unless both sides hold an order. */
    public Double mid() {
        Order bid = bestBid();
        Order ask = bestAsk();
        return bid == null || ask == null ? null : (bid.price() + ask.price()) / 2.0d;
    }

    /** Null unless both sides hold an order. */
    public Double spread() {
        Order bid = bestBid();
        Order ask = bestAsk();
        return bid == null || ask == null ? null : ask.price() - bid.price();
    }

    public void rest(Order order) {
        (order.side() == OrderSide.BUY ? bids : asks).add(order);
    }

    public void removeExhausted() {
        bids.removeIf(Order::isExhausted);
        asks.removeIf(Order::isExhausted);
    }

    /** Resolution kills every resting order: the book stops taking part in anything. */
    public void cancelAll() {
        bids.clear();
        asks.clear();
    }

    public List<Order> ordersOf(String userName) {
        List<Order> mine = new ArrayList<>();
        for (Order order : bids()) {
            if (order.userName().equals(userName)) {
                mine.add(order);
            }
        }
        for (Order order : asks()) {
            if (order.userName().equals(userName)) {
                mine.add(order);
            }
        }
        return mine;
    }

    private List<Order> ranked(List<Order> side, Comparator<Order> rank) {
        List<Order> copy = new ArrayList<>(side);
        copy.sort(rank);
        return copy;
    }
}
