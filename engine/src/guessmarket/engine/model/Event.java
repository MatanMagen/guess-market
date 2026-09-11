package guessmarket.engine.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What every event has regardless of how it is traded: its description, its answers, its own
 * money, its market maker, who is taking part and what they hold.
 * <p>
 * An event is loaded {@link EventStatus#NOT_STARTED} with an empty account. Opening it is what
 * moves the market maker's money in — the subsidy for LMSR, the initial share purchase for an
 * order book — so the two subclasses decide what opening costs and what closing pays out.
 * <p>
 * Commission always ends up in the market maker's own pocket, never in the event's account.
 */
public abstract class Event implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int id;
    private final String name;
    private final String description;
    private final int commissionPercent;
    private final CommissionType commissionType;
    /** ArrayList rather than List so the field is provably serializable. */
    private final ArrayList<Outcome> outcomes;
    private final Account account;
    private final String marketMakerName;

    /** Per user, how many shares of each answer he holds. Index matches {@link #outcomes}. */
    private final LinkedHashMap<String, long[]> holdings = new LinkedHashMap<>();
    /** Per user, what he has paid for each answer, for the "amount paid per option" display. */
    private final LinkedHashMap<String, double[]> amountPaid = new LinkedHashMap<>();
    /** Per user, commission he has paid on this event. */
    private final LinkedHashMap<String, Double> commissionPaid = new LinkedHashMap<>();
    /** Insertion ordered: a user joins on his first action and never leaves. */
    private final LinkedHashSet<String> participants = new LinkedHashSet<>();

    private EventStatus status;
    private double commissionCollected;
    private int winningOutcomeIndex;

    protected Event(int id,
                    String name,
                    String description,
                    int commissionPercent,
                    CommissionType commissionType,
                    List<String> outcomeNames,
                    String marketMakerName) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.commissionPercent = commissionPercent;
        this.commissionType = commissionType;
        this.marketMakerName = marketMakerName;
        this.outcomes = new ArrayList<>();
        for (String outcomeName : outcomeNames) {
            this.outcomes.add(new Outcome(outcomeName));
        }
        this.account = new Account(0.0d);
        this.status = EventStatus.NOT_STARTED;
        this.commissionCollected = 0.0d;
        this.winningOutcomeIndex = -1;
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

    public String marketMakerName() {
        return marketMakerName;
    }

    public EventStatus status() {
        return status;
    }

    public List<Outcome> outcomes() {
        return Collections.unmodifiableList(outcomes);
    }

    public double accountBalance() {
        return account.balance();
    }

    public double commissionCollected() {
        return commissionCollected;
    }

    public Set<String> participants() {
        return Collections.unmodifiableSet(participants);
    }

    public Outcome winningOutcome() {
        return winningOutcomeIndex < 0 ? null : outcomes.get(winningOutcomeIndex);
    }

    public boolean hasOutcomeIndex(int outcomeIndex) {
        return outcomeIndex >= 0 && outcomeIndex < outcomes.size();
    }

    public boolean isMarketMaker(String userName) {
        return marketMakerName.equals(userName);
    }

    public double commissionRate() {
        return commissionPercent / 100.0d;
    }

    public long holdingOf(String userName, int outcomeIndex) {
        long[] mine = holdings.get(userName);
        return mine == null ? 0L : mine[outcomeIndex];
    }

    public double amountPaidBy(String userName, int outcomeIndex) {
        double[] mine = amountPaid.get(userName);
        return mine == null ? 0.0d : mine[outcomeIndex];
    }

    public double commissionPaidBy(String userName) {
        return commissionPaid.getOrDefault(userName, 0.0d);
    }

    public boolean isParticipant(String userName) {
        return participants.contains(userName);
    }

    /** The other answer of a binary event. */
    public int oppositeOf(int outcomeIndex) {
        return outcomeIndex == 0 ? 1 : 0;
    }

    /** What the market maker must put up to open. */
    public abstract double openingCost();

    /** LMSR or order book, for the front end to branch on without instanceof. */
    public abstract guessmarket.dto.MarketMethod method();

    /**
     * Moves the event from not started to active, taking the opening cost out of the market
     * maker's pocket and into the event's account.
     *
     * @throws IllegalStateException if the event has already been opened.
     */
    public void open(User marketMaker) {
        if (status != EventStatus.NOT_STARTED) {
            throw new IllegalStateException("event " + id + " has already been opened");
        }
        double cost = openingCost();
        marketMaker.pay(cost);
        account.deposit(cost);
        join(marketMaker.name());
        onOpened(marketMaker);
        status = EventStatus.ACTIVE;
    }

    /** Hook for whatever else opening means — an order book hands the market maker his shares. */
    protected void onOpened(User marketMaker) {
        // nothing by default
    }

    protected void requireActive() {
        if (status != EventStatus.ACTIVE) {
            throw new IllegalStateException("event " + id + " is not active");
        }
    }

    protected void markClosed(int winningIndex) {
        winningOutcomeIndex = winningIndex;
        status = EventStatus.CLOSED;
    }

    protected Account account() {
        return account;
    }

    protected void join(String userName) {
        participants.add(userName);
        holdings.computeIfAbsent(userName, key -> new long[outcomes.size()]);
        amountPaid.computeIfAbsent(userName, key -> new double[outcomes.size()]);
    }

    protected void addHolding(String userName, int outcomeIndex, long quantity, double paid) {
        join(userName);
        holdings.get(userName)[outcomeIndex] += quantity;
        amountPaid.get(userName)[outcomeIndex] += paid;
    }

    protected void recordCommissionPaid(String userName, double amount) {
        if (amount <= 0.0d) {
            return;
        }
        commissionPaid.merge(userName, amount, Double::sum);
        commissionCollected += amount;
    }

    /** Commission never sits in the event's account: it is the market maker's income. */
    protected void payCommissionToMarketMaker(String payerName, double amount, User marketMaker) {
        if (amount <= 0.0d) {
            return;
        }
        marketMaker.receive(amount);
        recordCommissionPaid(payerName, amount);
    }

    protected Map<String, long[]> holdingsView() {
        return Collections.unmodifiableMap(holdings);
    }
}
