package guessmarket.dto;

import java.util.List;

/**
 * The full trading picture of one event. Exactly one of {@code lmsr} and {@code orderBook} is
 * present, matching the summary's method. {@code winningOutcomeName} is null until the event closes.
 */
public record EventState(EventSummary summary,
                         LmsrDetails lmsr,
                         OrderBookDetails orderBook,
                         List<ParticipantState> participants,
                         String winningOutcomeName,
                         boolean closed) {

    public EventState {
        participants = List.copyOf(participants);
    }
}
