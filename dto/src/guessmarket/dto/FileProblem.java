package guessmarket.dto;

import java.io.Serializable;
import java.util.List;

/**
 * One reason a file was rejected, as data rather than as a sentence, so the wording is left to
 * whichever interface is facing the user.
 * <p>
 * {@code eventPosition} and {@code eventName} are null when the fault belongs to the file as a
 * whole. Which values go with each kind is listed on {@link ProblemKind}.
 */
public record FileProblem(ProblemKind kind, Integer eventPosition, String eventName, List<String> values)
        implements Serializable {

    public FileProblem {
        values = values == null ? List.of() : List.copyOf(values);
    }

    public static FileProblem ofFile(ProblemKind kind, String... values) {
        return new FileProblem(kind, null, null, List.of(values));
    }

    public static FileProblem ofEvent(ProblemKind kind, int eventPosition, String eventName, String... values) {
        return new FileProblem(kind, eventPosition, eventName, List.of(values));
    }

    public boolean belongsToAnEvent() {
        return eventPosition != null;
    }

    /** Reads a value, or an empty string if this kind does not carry one at that index. */
    public String value(int index) {
        return index >= 0 && index < values.size() ? values.get(index) : "";
    }
}
