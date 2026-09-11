package guessmarket.engine.exception;

import guessmarket.dto.FileProblem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A new event could not be accepted.
 * <p>
 * Carries the same {@link FileProblem} records a rejected file does, because the rules are the
 * same rules: where they came from, a file or a form, does not change what makes an event sound.
 * That also means the front end already knows how to word every one of them.
 */
public class InvalidEventException extends GuessMarketException {

    private static final long serialVersionUID = 1L;

    /** ArrayList rather than List so the field is provably serializable. */
    private final ArrayList<FileProblem> problems;

    public InvalidEventException(List<FileProblem> problems) {
        super("the event definition was rejected with " + problems.size() + " problem(s)");
        if (problems.isEmpty()) {
            throw new IllegalArgumentException("a rejected event must carry at least one problem");
        }
        this.problems = new ArrayList<>(problems);
    }

    public List<FileProblem> problems() {
        return Collections.unmodifiableList(problems);
    }
}
