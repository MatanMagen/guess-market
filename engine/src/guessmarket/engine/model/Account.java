package guessmarket.engine.model;

import java.io.Serializable;

/**
 * A money balance owned by an event.
 * <p>
 * Never reset once the event closes: the residual is what the market maker is left with after the
 * winners have been paid, and it is allowed to go negative.
 */
public class Account implements Serializable {

    private static final long serialVersionUID = 1L;

    private double balance;

    public Account(double openingBalance) {
        this.balance = openingBalance;
    }

    public double balance() {
        return balance;
    }

    public void deposit(double amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("deposit amount must not be negative: " + amount);
        }
        balance += amount;
    }

    public void withdraw(double amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("withdrawal amount must not be negative: " + amount);
        }
        balance -= amount;
    }
}
