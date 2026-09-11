package guessmarket.dto;

import java.util.List;

/**
 * One user's involvement in one event, in whichever terms that event trades.
 * <p>
 * {@code lmsrTrades} is filled for an LMSR event and is newest first; {@code holdings} and
 * {@code restingOrders} are filled for an order book event. {@code profitOrLoss} is only
 * meaningful once the event has closed.
 */
public record UserParticipation(int eventId,
                                String eventName,
                                MarketMethod method,
                                EventLifecycle status,
                                boolean marketMaker,
                                List<TradeRecord> lmsrTrades,
                                List<HoldingState> holdings,
                                List<OrderView> restingOrders,
                                double commissionPaid,
                                double profitOrLoss,
                                String winningOutcomeName) {

    public UserParticipation {
        lmsrTrades = List.copyOf(lmsrTrades);
        holdings = List.copyOf(holdings);
        restingOrders = List.copyOf(restingOrders);
    }
}
