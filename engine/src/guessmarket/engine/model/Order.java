package guessmarket.engine.model;

import guessmarket.dto.OrderSide;

import java.io.Serializable;

/**
 * One order resting in a book. Price is per share and never changes; only the outstanding
 * quantity moves, as the order is eaten into by the other side.
 */
public class Order implements Serializable {

    private static final long serialVersionUID = 1L;

    private final long id;
    private final String userName;
    private final OrderSide side;
    private final double price;
    private final long originalQuantity;
    private long remaining;

    public Order(long id, String userName, OrderSide side, long quantity, double price) {
        this.id = id;
        this.userName = userName;
        this.side = side;
        this.price = price;
        this.originalQuantity = quantity;
        this.remaining = quantity;
    }

    public long id() {
        return id;
    }

    public String userName() {
        return userName;
    }

    public OrderSide side() {
        return side;
    }

    public double price() {
        return price;
    }

    public long originalQuantity() {
        return originalQuantity;
    }

    public long remaining() {
        return remaining;
    }

    public boolean isExhausted() {
        return remaining <= 0;
    }

    void take(long quantity) {
        if (quantity <= 0 || quantity > remaining) {
            throw new IllegalArgumentException("cannot take " + quantity + " from an order holding " + remaining);
        }
        remaining -= quantity;
    }
}
