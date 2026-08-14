package guessmarket.engine.model;

import java.io.Serializable;

/**
 * LMSR pricing for one event:
 * <pre>
 *   price(i) = e^(q_i / b) / SUM_j e^(q_j / b)
 *   C(q)     = b * ln( SUM_j e^(q_j / b) )
 * </pre>
 * A purchase costs the difference between C after it and C before it.
 */
public class LmsrMarket implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Paid at resolution for every share of the winning answer. */
    public static final double PAYOUT_PER_WINNING_SHARE = 1.0d;

    private final int liquidity;
    private final long[] quantities;

    public LmsrMarket(int liquidity, int outcomeCount) {
        if (liquidity <= 0) {
            throw new IllegalArgumentException("liquidity b must be positive: " + liquidity);
        }
        if (outcomeCount < 2) {
            throw new IllegalArgumentException("an event needs at least two answers: " + outcomeCount);
        }
        this.liquidity = liquidity;
        this.quantities = new long[outcomeCount];
    }

    public int liquidity() {
        return liquidity;
    }

    /** What the market maker commits to open the event: C with nothing bought yet, so b*ln(2) for two answers. */
    public double openingSubsidy() {
        return costOf(new long[quantities.length]);
    }

    public double price(int outcomeIndex) {
        double shift = maxScaledQuantity(quantities);
        double total = 0.0d;
        for (long quantity : quantities) {
            total += Math.exp(scaled(quantity) - shift);
        }
        return Math.exp(scaled(quantities[outcomeIndex]) - shift) / total;
    }

    /** Prices a purchase without applying it. */
    public double quoteBuy(int outcomeIndex, long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("share quantity must be positive: " + quantity);
        }
        long[] after = quantities.clone();
        after[outcomeIndex] += quantity;
        return costOf(after) - costOf(quantities);
    }

    public double applyBuy(int outcomeIndex, long quantity) {
        double cost = quoteBuy(outcomeIndex, quantity);
        quantities[outcomeIndex] += quantity;
        return cost;
    }

    public double currentCost() {
        return costOf(quantities);
    }

    // Both formulas are shifted by m = max(q_j / b) before exponentiating. A plain
    // Math.exp(q / b) overflows to infinity once q / b passes about 709, which on a
    // low liquidity event just means buying a lot of shares.
    private double costOf(long[] state) {
        double shift = maxScaledQuantity(state);
        double total = 0.0d;
        for (long quantity : state) {
            total += Math.exp(scaled(quantity) - shift);
        }
        return liquidity * (shift + Math.log(total));
    }

    private double maxScaledQuantity(long[] state) {
        double max = scaled(state[0]);
        for (int i = 1; i < state.length; i++) {
            max = Math.max(max, scaled(state[i]));
        }
        return max;
    }

    private double scaled(long quantity) {
        return (double) quantity / liquidity;
    }
}
