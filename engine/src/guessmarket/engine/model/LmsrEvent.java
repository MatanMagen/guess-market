package guessmarket.engine.model;

import guessmarket.dto.MarketMethod;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * An event priced by the logarithmic market scoring rule: anyone can buy at any time without a
 * counterparty, because the market maker's subsidy stands behind every share.
 * <p>
 * Opening costs the market maker C(0,0) = b·ln 2, which sits in the event's account until
 * resolution. Since C(q) is never smaller than the largest q, the account always covers the
 * winners, and whatever is left over goes back to the market maker when the event closes.
 */
public class LmsrEvent extends Event {

    private static final long serialVersionUID = 1L;

    private final LmsrMarket market;
    /** ArrayList rather than List so the field is provably serializable. */
    private final ArrayList<Trade> trades = new ArrayList<>();

    public LmsrEvent(int id,
                     String name,
                     String description,
                     int commissionPercent,
                     CommissionType commissionType,
                     List<String> outcomeNames,
                     String marketMakerName,
                     int liquidity) {
        super(id, name, description, commissionPercent, commissionType, outcomeNames, marketMakerName);
        this.market = new LmsrMarket(liquidity, outcomeNames.size());
    }

    @Override
    public MarketMethod method() {
        return MarketMethod.LMSR;
    }

    @Override
    public double openingCost() {
        return market.openingSubsidy();
    }

    public int liquidity() {
        return market.liquidity();
    }

    public double price(int outcomeIndex) {
        return market.price(outcomeIndex);
    }

    public double quoteBuy(int outcomeIndex, long quantity) {
        return market.quoteBuy(outcomeIndex, quantity);
    }

    /** In the order the trades happened. */
    public List<Trade> trades() {
        return Collections.unmodifiableList(trades);
    }

    public List<Trade> tradesOf(String userName) {
        List<Trade> mine = new ArrayList<>();
        for (Trade trade : trades) {
            if (trade.userName().equals(userName)) {
                mine.add(trade);
            }
        }
        return mine;
    }

    /** What the market maker put in, which is what {@link #marketMakerNetResult} measures against. */
    public double openingSubsidy() {
        return market.openingSubsidy();
    }

    /** Negative means the market maker is out of pocket on this event. */
    public double marketMakerNetResult() {
        return accountBalance() - openingSubsidy();
    }

    /**
     * Buys shares of one answer for a user. On an on-purchase event the commission goes on top of
     * the LMSR price and straight to the market maker.
     */
    public Trade buy(User buyer, int outcomeIndex, long quantity, User marketMaker) {
        requireActive();
        double sharesCost = market.applyBuy(outcomeIndex, quantity);
        double commission = commissionType() == CommissionType.ON_PURCHASE ? sharesCost * commissionRate() : 0.0d;

        buyer.pay(sharesCost + commission);
        account().deposit(sharesCost);
        payCommissionToMarketMaker(buyer.name(), commission, marketMaker);

        outcomes().get(outcomeIndex).addShares(quantity);
        addHolding(buyer.name(), outcomeIndex, quantity, sharesCost);

        Trade trade = new Trade(buyer.name(), outcomes().get(outcomeIndex).name(), quantity, sharesCost, commission);
        trades.add(trade);
        return trade;
    }

    /**
     * Resolves the event. Every winning share pays 1; on an on-close event the commission comes
     * off that payout first and goes to the market maker. Whatever subsidy is left then follows.
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
            double gross = shares * LmsrMarket.PAYOUT_PER_WINNING_SHARE;
            double commission = commissionType() == CommissionType.ON_CLOSE ? gross * commissionRate() : 0.0d;
            double net = gross - commission;

            account().withdraw(gross);
            users.get(participant).receive(net);
            payCommissionToMarketMaker(participant, commission, marketMaker);

            payments.add(new Payment(participant, shares, gross, commission, net));
            grossTotal += gross;
            commissionTotal += commission;
        }

        double leftover = accountBalance();
        if (leftover > 0.0d) {
            account().withdraw(leftover);
            marketMaker.receive(leftover);
        }

        markClosed(winningIndex);
        return new Settlement(outcomes().get(winningIndex).name(),
                outcomes().get(winningIndex).sharesBought(),
                grossTotal, commissionTotal, grossTotal - commissionTotal, Math.max(0.0d, leftover), payments);
    }

}
