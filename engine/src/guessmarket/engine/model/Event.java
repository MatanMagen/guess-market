package guessmarket.engine.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One binary event traded with LMSR. Owns its answers, its pricing, its money and its history, and
 * all of it changes only through {@link #buy} and {@link #close}.
 * <h2>Where the money sits</h2>
 * The account is the pot from appendix A: it opens holding the subsidy C(0,0), grows with every
 * purchase and commission, and shrinks when the winners are paid. Since the subsidy came out of the
 * market maker's own pocket, the figure that says whether he gained or lost is
 * {@link #marketMakerNetResult()}, and that is the one that can be negative.
 */
public class Event implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int id;
    private final String name;
    private final String description;
    private final int commissionPercent;
    private final CommissionType commissionType;
    /** ArrayList rather than List so the field is provably serializable. */
    private final ArrayList<Outcome> outcomes;
    private final LmsrMarket market;
    private final Account account;
    /** ArrayList rather than List so the field is provably serializable. */
    private final ArrayList<Trade> trades;
    private final double openingSubsidy;

    private EventStatus status;
    private double commissionCollected;
    private int winningOutcomeIndex;

    public Event(int id,
                 String name,
                 String description,
                 int commissionPercent,
                 CommissionType commissionType,
                 List<String> outcomeNames,
                 int liquidity) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.commissionPercent = commissionPercent;
        this.commissionType = commissionType;
        this.outcomes = new ArrayList<>();
        for (String outcomeName : outcomeNames) {
            this.outcomes.add(new Outcome(outcomeName));
        }
        this.market = new LmsrMarket(liquidity, this.outcomes.size());
        this.trades = new ArrayList<>();
        this.status = EventStatus.ACTIVE;
        this.commissionCollected = 0.0d;
        this.winningOutcomeIndex = -1;
        this.openingSubsidy = market.openingSubsidy();
        this.account = new Account(openingSubsidy);
    }

    public int id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public int commissionPercent() {
        return commissionPercent;
    }

    public CommissionType commissionType() {
        return commissionType;
    }

    public EventStatus status() {
        return status;
    }

    public int liquidity() {
        return market.liquidity();
    }

    public List<Outcome> outcomes() {
        return Collections.unmodifiableList(outcomes);
    }

    /** In the order the trades happened. */
    public List<Trade> trades() {
        return Collections.unmodifiableList(trades);
    }

    public double accountBalance() {
        return account.balance();
    }

    public double openingSubsidy() {
        return openingSubsidy;
    }

    /** Negative means the market maker ended up subsidising the event. */
    public double marketMakerNetResult() {
        return account.balance() - openingSubsidy;
    }

    public double commissionCollected() {
        return commissionCollected;
    }

    public double price(int outcomeIndex) {
        return market.price(outcomeIndex);
    }

    /** Null while the event is still active. */
    public Outcome winningOutcome() {
        return winningOutcomeIndex < 0 ? null : outcomes.get(winningOutcomeIndex);
    }

    public boolean hasOutcomeIndex(int outcomeIndex) {
        return outcomeIndex >= 0 && outcomeIndex < outcomes.size();
    }

    /**
     * Buys shares of one answer. On an on-purchase event the commission goes on top of the LMSR
     * price and lands in the account with it.
     *
     * @throws IllegalStateException if the event is already closed.
     */
    public Trade buy(int outcomeIndex, long quantity) {
        if (status != EventStatus.ACTIVE) {
            throw new IllegalStateException("event " + id + " is closed and cannot be traded");
        }
        double sharesCost = market.applyBuy(outcomeIndex, quantity);
        double commission = commissionType == CommissionType.ON_PURCHASE
                ? sharesCost * commissionPercent / 100.0d
                : 0.0d;

        outcomes.get(outcomeIndex).addShares(quantity);
        account.deposit(sharesCost);
        if (commission > 0.0d) {
            account.deposit(commission);
            commissionCollected += commission;
        }

        Trade trade = new Trade(outcomes.get(outcomeIndex).name(), quantity, sharesCost, commission);
        trades.add(trade);
        return trade;
    }

    /**
     * Resolves the event and pays the winners. Every winning share is worth
     * {@link LmsrMarket#PAYOUT_PER_WINNING_SHARE}; on an on-close event the commission comes off
     * that winning position first and stays in the account.
     *
     * @throws IllegalStateException if the event is already closed.
     */
    public Settlement close(int winningIndex) {
        if (status != EventStatus.ACTIVE) {
            throw new IllegalStateException("event " + id + " is already closed");
        }
        long winningShares = outcomes.get(winningIndex).sharesBought();
        double grossPayout = winningShares * LmsrMarket.PAYOUT_PER_WINNING_SHARE;
        double commission = commissionType == CommissionType.ON_CLOSE
                ? grossPayout * commissionPercent / 100.0d
                : 0.0d;
        double netPayout = grossPayout - commission;

        if (commission > 0.0d) {
            commissionCollected += commission;
        }
        account.withdraw(netPayout);

        winningOutcomeIndex = winningIndex;
        status = EventStatus.CLOSED;
        return new Settlement(outcomes.get(winningIndex).name(), winningShares, grossPayout, commission, netPayout);
    }

    /** What happened when the event was closed. {@code commissionCharged} is 0 for on-purchase events. */
    public record Settlement(String winningOutcomeName,
                             long winningShares,
                             double grossPayout,
                             double commissionCharged,
                             double netPaidToWinners) implements Serializable {
    }
}
