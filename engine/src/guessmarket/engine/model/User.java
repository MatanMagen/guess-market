package guessmarket.engine.model;

import java.io.Serializable;

/**
 * One trader and his money.
 * <p>
 * The exercise asks for an unusual rule: a user must not go into overdraft, but if an action takes
 * him there it is allowed to happen, he is told, and he is barred from acting again. So
 * {@link #pay} never refuses — it lets the balance go negative and sets {@link #isBlocked()},
 * and it is the engine that checks the flag before accepting the next request.
 */
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String name;
    private final double initialCash;
    private double balance;
    private boolean blocked;

    public User(String name, double initialCash) {
        this.name = name;
        this.initialCash = initialCash;
        this.balance = initialCash;
        this.blocked = false;
    }

    public String name() {
        return name;
    }

    public double initialCash() {
        return initialCash;
    }

    public double balance() {
        return balance;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public boolean canAfford(double amount) {
        return balance >= amount;
    }

    /** @return true if this payment is what pushed him under, which is also what blocks him. */
    public boolean pay(double amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("payment must not be negative: " + amount);
        }
        balance -= amount;
        if (balance < 0 && !blocked) {
            blocked = true;
            return true;
        }
        return false;
    }

    public void receive(double amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("receipt must not be negative: " + amount);
        }
        balance += amount;
    }
}
