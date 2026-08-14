package guessmarket.dto;

import java.util.List;

/**
 * The full trading picture of one event.
 * <p>
 * {@code accountBalance} includes the opening subsidy. {@code marketMakerNetResult} is that
 * balance measured against the subsidy, so it goes negative when the market maker is out of
 * pocket. {@code tradeHistory} is newest first. {@code winningOutcomeName} is null while active.
 */
public record EventState(EventSummary summary,
                         int liquidity,
                         List<OutcomeState> outcomes,
                         double accountBalance,
                         double openingSubsidy,
                         double marketMakerNetResult,
                         double commissionCollected,
                         List<TradeRecord> tradeHistory,
                         String winningOutcomeName,
                         boolean closed) {

    public EventState {
        outcomes = List.copyOf(outcomes);
        tradeHistory = List.copyOf(tradeHistory);
    }
}
