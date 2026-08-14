package guessmarket.engine.exception;

import guessmarket.dto.FileProblem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A Guess Market file could not be accepted.
 * <p>
 * Carries every fault found at once, not one per attempt, so the user can fix the file in a
 * single pass. A file that raises this has changed nothing: whatever was loaded before it still is.
 */
public class InvalidFileException extends GuessMarketException {

    private static final long serialVersionUID = 1L;

    private final String path;
    /** ArrayList rather than List so the field is provably serializable. */
    private final ArrayList<FileProblem> problems;

    public InvalidFileException(String path, List<FileProblem> problems) {
        super("file '" + path + "' rejected with " + problems.size() + " problem(s)");
        if (problems.isEmpty()) {
            throw new IllegalArgumentException("a rejected file must carry at least one problem");
        }
        this.path = path;
        this.problems = new ArrayList<>(problems);
    }

    public String path() {
        return path;
    }

    public List<FileProblem> problems() {
        return Collections.unmodifiableList(problems);
    }
}
