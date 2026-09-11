package guessmarket.dto;

import java.util.List;

/**
 * An event somebody wants to bring into being, as they described it. The engine checks it against
 * exactly the rules a loaded file has to satisfy before it accepts it.
 * <p>
 * The two factories exist because the settings that matter depend on the trading method: an LMSR
 * event needs only its liquidity, and an order book needs its base value, its initial stock and
 * whether it mints. Whichever is not relevant is left at zero and ignored.
 */
public record NewEvent(String creatorName,
                       String name,
                       String description,
                       int commissionPercent,
                       CommissionPolicy commissionType,
                       List<String> outcomeNames,
                       MarketMethod method,
                       int liquidity,
                       int basePrice,
                       boolean mintAllowed,
                       int initialInvestment) {

    public NewEvent {
        outcomeNames = List.copyOf(outcomeNames);
    }

    public static NewEvent lmsr(String creatorName, String name, String description,
                                int commissionPercent, CommissionPolicy commissionType,
                                List<String> outcomeNames, int liquidity) {
        return new NewEvent(creatorName, name, description, commissionPercent, commissionType,
                outcomeNames, MarketMethod.LMSR, liquidity, 0, false, 0);
    }

    public static NewEvent orderBook(String creatorName, String name, String description,
                                     int commissionPercent, CommissionPolicy commissionType,
                                     List<String> outcomeNames,
                                     int basePrice, boolean mintAllowed, int initialInvestment) {
        return new NewEvent(creatorName, name, description, commissionPercent, commissionType,
                outcomeNames, MarketMethod.ORDER_BOOK, 0, basePrice, mintAllowed, initialInvestment);
    }
}
