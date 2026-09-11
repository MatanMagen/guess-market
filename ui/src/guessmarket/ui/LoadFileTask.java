package guessmarket.ui;

import guessmarket.dto.LoadSummary;
import guessmarket.engine.api.Engine;
import javafx.concurrent.Task;

/**
 * Loads a file off the JavaFX thread so the window keeps drawing.
 * <p>
 * Reading one of these files takes a few milliseconds, which would make a progress bar flash past
 * before anyone saw it, so the work is spread over a short pause. The exercise asks for exactly
 * this.
 */
public class LoadFileTask extends Task<LoadSummary> {

    private static final int STEPS = 40;
    private static final long PAUSE_PER_STEP_MILLIS = 35L;

    private final Engine engine;
    private final String path;

    public LoadFileTask(Engine engine, String path) {
        this.engine = engine;
        this.path = path;
    }

    @Override
    protected LoadSummary call() throws InterruptedException {
        updateMessage("Reading " + path);
        for (int step = 0; step < STEPS / 2; step++) {
            Thread.sleep(PAUSE_PER_STEP_MILLIS);
            updateProgress(step, STEPS);
        }

        updateMessage("Checking the file");
        LoadSummary summary = engine.loadFromFile(path);

        for (int step = STEPS / 2; step <= STEPS; step++) {
            Thread.sleep(PAUSE_PER_STEP_MILLIS);
            updateProgress(step, STEPS);
        }
        updateMessage("Loaded " + summary.eventsLoaded() + " event(s)");
        return summary;
    }
}
