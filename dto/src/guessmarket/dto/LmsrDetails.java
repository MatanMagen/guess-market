package guessmarket.dto;

import java.util.List;

/**
 * The LMSR side of an event.
 * <p>
 * {@code accountBalance} is the event's own pot. {@code marketMakerNetResult} measures it against
 * the subsidy the market maker put in, so it goes negative when he is out of pocket.
 * {@code tradeHistory} is newest first.
 */
public record LmsrDetails(int liquidity,
                          List<OutcomeState> outcomes,
                          double openingSubsidy,
                          double marketMakerNetResult,
                          double commissionCollected,
                          List<TradeRecord> tradeHistory) {

    public LmsrDetails {
        outcomes = List.copyOf(outcomes);
        tradeHistory = List.copyOf(tradeHistory);
    }
}
