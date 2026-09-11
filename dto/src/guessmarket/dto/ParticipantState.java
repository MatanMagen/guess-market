package guessmarket.dto;

import java.util.List;

/**
 * One participant of an event as the event screen shows them. Somebody counts as a participant
 * from their first order, so {@code holdings} can be all zeros while orders are still resting.
 */
public record ParticipantState(String userName, List<HoldingState> holdings, long restingOrders) {

    public ParticipantState {
        holdings = List.copyOf(holdings);
    }
}
