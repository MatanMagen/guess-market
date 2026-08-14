package guessmarket.dto;

/** How an event settled, and how it stands afterwards. */
public record CloseResult(String winningOutcomeName,
                          long winningShares,
                          double grossPayout,
                          double commissionCharged,
                          double netPaidToWinners,
                          boolean commissionAppliedOnClose,
                          EventState stateAfterClose) {
}
