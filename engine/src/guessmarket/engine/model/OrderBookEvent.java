package guessmarket.engine.model;

import guessmarket.dto.EntryKind;
import guessmarket.dto.FillKind;
import guessmarket.dto.MarketMethod;
import guessmarket.dto.OrderSide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * An event traded peer to peer through a book of resting orders, one book per answer.
 * <h2>Where shares come from</h2>
 * Shares are only ever created in complete sets, one of each answer, against d paid into the
 * event's account. For the usual two answers a set is a pair. That happens twice: when the market
 * maker opens the event and buys the initial stock, and — if the event allows it — when buyers on
 * every answer between them offer at least d. Everything else is resale, which moves shares and
 * cash between users and leaves the account alone.
 * <p>
 * Because every set ever minted put exactly d into the account and pays out exactly d at
 * resolution, the account empties to nothing when the event closes.
 */
public class OrderBookEvent extends Event {

    private static final long serialVersionUID = 1L;

    /** The finest price step, and so the distance the best price sits below d. */
    public static final double PRICE_STEP = 0.01d;

    /** Prices are compared with a tolerance because they are carried as doubles. */
    private static final double EPSILON = 1e-9d;

    private final int basePrice;
    private final boolean mintAllowed;
    private final int initialInvestment;
    /** ArrayList rather than List so the field is provably serializable. */
    private final ArrayList<OrderBook> books = new ArrayList<>();

    private long nextOrderId = 1L;

    public OrderBookEvent(int id,
                          String name,
                          String description,
                          int commissionPercent,
                          CommissionType commissionType,
                          List<String> outcomeNames,
                          String marketMakerName,
                          int basePrice,
                          boolean mintAllowed,
                          int initialInvestment) {
        super(id, name, description, commissionPercent, commissionType, outcomeNames, marketMakerName);
        this.basePrice = basePrice;
        this.mintAllowed = mintAllowed;
        this.initialInvestment = initialInvestment;
        for (String outcomeName : outcomeNames) {
            books.add(new OrderBook(outcomeName));
        }
    }

    @Override
    public MarketMethod method() {
        return MarketMethod.ORDER_BOOK;
    }

    public int basePrice() {
        return basePrice;
    }

    public boolean isMintAllowed() {
        return mintAllowed;
    }

    public int initialInvestment() {
        return initialInvestment;
    }

    public List<OrderBook> books() {
        return Collections.unmodifiableList(books);
    }

    public OrderBook bookOf(int outcomeIndex) {
        return books.get(outcomeIndex);
    }

    public double minimumPrice() {
        return PRICE_STEP;
    }

    /** A share can never be worth more than the d it pays out, so the best offer stops one step short. */
    public double maximumPrice() {
        return basePrice - PRICE_STEP;
    }

    public boolean isPriceAllowed(double price) {
        return price >= minimumPrice() - EPSILON && price <= maximumPrice() + EPSILON;
    }

    /**
     * The initial stock is bought in whole pairs, so an initial investment that is not a multiple
     * of d buys as many pairs as it covers and the remainder is never taken.
     */
    public long initialPairs() {
        return initialInvestment / basePrice;
    }

    @Override
    public double openingCost() {
        return initialPairs() * (double) basePrice;
    }

    /** Opening hands the market maker one share of each answer per d he put in. */
    @Override
    protected void onOpened(User marketMaker) {
        long pairs = initialPairs();
        if (pairs <= 0) {
            return;
        }
        // One payment bought every side, so it is split evenly between them for the
        // "paid per answer" display; nothing else depends on how it is attributed.
        double paidPerSide = pairs * basePrice / (double) outcomes().size();
        for (int i = 0; i < outcomes().size(); i++) {
            outcomes().get(i).addShares(pairs);
            addHolding(marketMaker.name(), i, pairs, paidPerSide);
        }
    }

    /** How many shares a user could still sell: what he holds, less what he already has on offer. */
    public long sellableQuantity(String userName, int outcomeIndex) {
        long committed = 0L;
        for (Order order : bookOf(outcomeIndex).asks()) {
            if (order.userName().equals(userName)) {
                committed += order.remaining();
            }
        }
        return holdingOf(userName, outcomeIndex) - committed;
    }

    /**
     * Puts one order through the market: first against the opposite side of its own book, then —
     * for a buy on an event that allows it — against the other answer's demand, minting new pairs.
     * Whatever is left rests in the book.
     */
    public OrderExecution submit(User trader,
                                 int outcomeIndex,
                                 OrderSide side,
                                 long quantity,
                                 double price,
                                 Map<String, User> users) {
        requireActive();
        User marketMaker = users.get(marketMakerName());
        List<Fill> fills = new ArrayList<>();
        Tally tally = new Tally(trader.name());
        long remaining = side == OrderSide.BUY
                ? fillBuy(trader, outcomeIndex, quantity, price, users, marketMaker, fills, tally)
                : fillSell(trader, outcomeIndex, quantity, price, users, marketMaker, fills, tally);

        long orderId = nextOrderId++;
        if (remaining > 0) {
            bookOf(outcomeIndex).rest(new Order(orderId, trader.name(), side, remaining, price));
        }
        join(trader.name());
        return new OrderExecution(orderId, quantity - remaining, remaining, fills,
                tally.spent, tally.received, tally.commission);
    }

    private long fillBuy(User buyer,
                         int outcomeIndex,
                         long quantity,
                         double price,
                         Map<String, User> users,
                         User marketMaker,
                         List<Fill> fills,
                         Tally tally) {
        long remaining = quantity;
        OrderBook book = bookOf(outcomeIndex);

        while (remaining > 0) {
            Order ask = book.bestAsk();
            if (ask == null || ask.price() > price + EPSILON) {
                break;
            }
            long taken = Math.min(remaining, ask.remaining());
            trade(buyer, users.get(ask.userName()), outcomeIndex, taken, ask.price(), marketMaker, fills, tally);
            ask.take(taken);
            book.removeExhausted();
            book.recordTradePrice(ask.price());
            remaining -= taken;
        }

        if (mintAllowed) {
            while (remaining > 0) {
                // A set needs a buyer on every other answer. The orders already waiting are
                // honoured at their own prices; the arriving one pays whatever is left of d, which
                // is never worse than the limit it named.
                List<Order> restingBids = new ArrayList<>();
                List<Integer> restingOutcomes = new ArrayList<>();
                double restingTotal = 0.0d;
                long taken = remaining;
                for (int other = 0; other < books.size(); other++) {
                    if (other == outcomeIndex) {
                        continue;
                    }
                    Order bid = bookOf(other).bestBid();
                    if (bid == null) {
                        restingBids = null;
                        break;
                    }
                    restingBids.add(bid);
                    restingOutcomes.add(other);
                    restingTotal += bid.price();
                    taken = Math.min(taken, bid.remaining());
                }
                // With more than two answers the others alone can already cover d, which would leave
                // the arriving order paying nothing. ponytail: no set is minted then; split the
                // surplus between the legs if such books ever matter.
                if (restingBids == null || restingTotal + price < basePrice - EPSILON
                        || restingTotal > basePrice - PRICE_STEP + EPSILON) {
                    break;
                }
                double arrivingPrice = basePrice - restingTotal;
                mint(buyer, outcomeIndex, arrivingPrice, restingBids, restingOutcomes, taken,
                        users, marketMaker, fills, tally);
                book.recordTradePrice(arrivingPrice);
                remaining -= taken;
            }
        }
        return remaining;
    }

    private long fillSell(User seller,
                          int outcomeIndex,
                          long quantity,
                          double price,
                          Map<String, User> users,
                          User marketMaker,
                          List<Fill> fills,
                          Tally tally) {
        long remaining = quantity;
        OrderBook book = bookOf(outcomeIndex);

        while (remaining > 0) {
            Order bid = book.bestBid();
            if (bid == null || bid.price() < price - EPSILON) {
                break;
            }
            long taken = Math.min(remaining, bid.remaining());
            trade(users.get(bid.userName()), seller, outcomeIndex, taken, bid.price(), marketMaker, fills, tally);
            bid.take(taken);
            book.removeExhausted();
            book.recordTradePrice(bid.price());
            remaining -= taken;
        }
        return remaining;
    }

    /** Existing shares change hands: the money goes between the two users, not through the account. */
    private void trade(User buyer,
                       User seller,
                       int outcomeIndex,
                       long quantity,
                       double price,
                       User marketMaker,
                       List<Fill> fills,
                       Tally tally) {
        double value = quantity * price;
        double commission = commissionType() == CommissionType.ON_PURCHASE ? value * commissionRate() : 0.0d;

        buyer.pay(value, EntryKind.SHARES_BOUGHT, name());
        seller.receive(value, EntryKind.SHARES_SOLD, name());
        chargeCommission(buyer, commission, marketMaker);

        addHolding(buyer.name(), outcomeIndex, quantity, value);
        addHolding(seller.name(), outcomeIndex, -quantity, -value);

        fills.add(new Fill(FillKind.TRADE, outcomes().get(outcomeIndex).name(),
                buyer.name(), seller.name(), quantity, price, commission));
        tally.bought(buyer.name(), value, commission);
        tally.sold(seller.name(), value);
    }

    /**
     * New sets come into existence: every buyer pays the account, and nobody sells anything. The
     * resting bids are consumed here; the arriving order's remainder is the caller's to track.
     */
    private void mint(User arrivingBuyer,
                      int arrivingOutcome,
                      double arrivingPrice,
                      List<Order> restingBids,
                      List<Integer> restingOutcomes,
                      long quantity,
                      Map<String, User> users,
                      User marketMaker,
                      List<Fill> fills,
                      Tally tally) {
        buyIntoSet(arrivingBuyer, arrivingOutcome, arrivingPrice, quantity, marketMaker, fills, tally);
        for (int i = 0; i < restingBids.size(); i++) {
            Order bid = restingBids.get(i);
            int outcomeIndex = restingOutcomes.get(i);
            buyIntoSet(users.get(bid.userName()), outcomeIndex, bid.price(), quantity, marketMaker, fills, tally);
            bid.take(quantity);
            bookOf(outcomeIndex).removeExhausted();
            bookOf(outcomeIndex).recordTradePrice(bid.price());
        }
    }

    /** One buyer's leg of a mint. */
    private void buyIntoSet(User buyer,
                            int outcomeIndex,
                            double price,
                            long quantity,
                            User marketMaker,
                            List<Fill> fills,
                            Tally tally) {
        double value = quantity * price;
        double commission = commissionType() == CommissionType.ON_PURCHASE ? value * commissionRate() : 0.0d;

        buyer.pay(value, EntryKind.SHARES_BOUGHT, name());
        account().deposit(value);
        chargeCommission(buyer, commission, marketMaker);

        outcomes().get(outcomeIndex).addShares(quantity);
        addHolding(buyer.name(), outcomeIndex, quantity, value);

        fills.add(new Fill(FillKind.MINT, outcomes().get(outcomeIndex).name(),
                buyer.name(), null, quantity, price, commission));
        tally.bought(buyer.name(), value, commission);
    }

    /**
     * Resolves the event. Every winning share pays d out of the account, which is exactly what the
     * account holds, and every resting order is cancelled.
     */
    public Settlement close(int winningIndex, Map<String, User> users) {
        requireActive();
        User marketMaker = users.get(marketMakerName());
        List<Payment> payments = new ArrayList<>();
        double grossTotal = 0.0d;
        double commissionTotal = 0.0d;

        for (String participant : participants()) {
            long shares = holdingOf(participant, winningIndex);
            if (shares <= 0) {
                continue;
            }
            double gross = shares * (double) basePrice;
            double commission = commissionType() == CommissionType.ON_CLOSE ? gross * commissionRate() : 0.0d;
            double net = gross - commission;

            account().withdraw(gross);
            User winner = users.get(participant);
            winner.receive(gross, EntryKind.WINNINGS, name());
            chargeCommission(winner, commission, marketMaker);

            payments.add(new Payment(participant, shares, gross, commission, net));
            grossTotal += gross;
            commissionTotal += commission;
        }

        for (OrderBook book : books) {
            book.cancelAll();
        }
        markClosed(winningIndex);
        return new Settlement(outcomes().get(winningIndex).name(),
                outcomes().get(winningIndex).sharesBought(),
                grossTotal, commissionTotal, grossTotal - commissionTotal, 0.0d, payments);
    }

    /** Cash movement seen from the point of view of the trader who submitted the order. */
    private static final class Tally {

        private final String submitter;
        private double spent;
        private double received;
        private double commission;

        private Tally(String submitter) {
            this.submitter = submitter;
        }

        private void bought(String buyerName, double value, double commissionCharged) {
            if (submitter.equals(buyerName)) {
                spent += value;
                commission += commissionCharged;
            }
        }

        private void sold(String sellerName, double value) {
            if (submitter.equals(sellerName)) {
                received += value;
            }
        }
    }
}
