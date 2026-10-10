package guessmarket.engine.model;

import guessmarket.dto.EntryKind;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One trader, his money, and the record of every time it moved.
 * <p>
 * The exercise asks for an unusual rule: a user must not go into overdraft, but if an action takes
 * him there it is allowed to happen, he is told, and he is barred from acting until he is back
 * above zero. So {@link #pay} never refuses — it lets the balance go negative, and it is the
 * engine that checks {@link #isBlocked()} before accepting the next request. Since exercise 3 a
 * user can load money into his account, which is how a blocked user gets unblocked.
 */
public class User implements Serializable {

    private static final long serialVersionUID = 2L;

    private final String name;
    private double balance;
    /** ArrayList rather than List so the field is provably serializable. */
    private final ArrayList<Entry> ledger = new ArrayList<>();

    public User(String name) {
        this.name = name;
        this.balance = 0.0d;
    }

    public String name() {
        return name;
    }

    public double balance() {
        return balance;
    }

    public boolean isBlocked() {
        return balance < 0;
    }

    public boolean canAfford(double amount) {
        return balance >= amount;
    }

    /** Oldest first. */
    public List<Entry> ledger() {
        return Collections.unmodifiableList(ledger);
    }

    /** @param eventName the event this payment belongs to, or null. */
    public void pay(double amount, EntryKind kind, String eventName) {
        if (amount < 0) {
            throw new IllegalArgumentException("payment must not be negative: " + amount);
        }
        move(-amount, kind, eventName);
    }

    /** @param eventName the event this receipt belongs to, or null. */
    public void receive(double amount, EntryKind kind, String eventName) {
        if (amount < 0) {
            throw new IllegalArgumentException("receipt must not be negative: " + amount);
        }
        move(amount, kind, eventName);
    }

    // A zero movement, such as the commission on a 0% event, changes nothing and would only be
    // noise on the account screen, so it is not recorded.
    private void move(double signedAmount, EntryKind kind, String eventName) {
        if (signedAmount == 0.0d) {
            return;
        }
        balance += signedAmount;
        ledger.add(new Entry(ledger.size() + 1, System.currentTimeMillis(), kind, eventName,
                signedAmount, balance));
    }

    /** One line of the account: money in is positive, money out negative. */
    public record Entry(int number, long timeMillis, EntryKind kind, String eventName,
                        double amount, double balanceAfter) implements Serializable {
    }
}
