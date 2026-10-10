package guessmarket.engine.exception;

import guessmarket.dto.FileProblem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A Guess Market file could not be accepted.
 * <p>
 * Carries every fault found at once, not one per attempt, so the user can fix the file in a
 * single pass. A file that raises this has added nothing: none of its events, not even the sound
 * ones, enter the system.
 */
public class InvalidFileException extends GuessMarketException {

    private static final long serialVersionUID = 1L;

    private final String fileName;
    /** ArrayList rather than List so the field is provably serializable. */
    private final ArrayList<FileProblem> problems;

    public InvalidFileException(String fileName, List<FileProblem> problems) {
        super("file '" + fileName + "' rejected with " + problems.size() + " problem(s)");
        if (problems.isEmpty()) {
            throw new IllegalArgumentException("a rejected file must carry at least one problem");
        }
        this.fileName = fileName;
        this.problems = new ArrayList<>(problems);
    }

    public String fileName() {
        return fileName;
    }

    public List<FileProblem> problems() {
        return Collections.unmodifiableList(problems);
    }
}
