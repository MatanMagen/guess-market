package guessmarket.dto;

import java.util.List;

/** The headline details of one event. {@code displayNumber} is its position in the listing it came from. */
public record EventSummary(int displayNumber,
                           int id,
                           String name,
                           String description,
                           int commissionPercent,
                           CommissionPolicy commissionType,
                           List<String> outcomeNames,
                           EventLifecycle status) {

    public EventSummary {
        outcomeNames = List.copyOf(outcomeNames);
    }
}
