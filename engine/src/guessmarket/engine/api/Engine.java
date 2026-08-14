package guessmarket.engine.api;

import guessmarket.dto.CloseResult;
import guessmarket.dto.EventState;
import guessmarket.dto.EventSummary;
import guessmarket.dto.LoadSummary;
import guessmarket.dto.PurchaseResult;
import guessmarket.engine.exception.InvalidFileException;

import java.util.List;

/**
 * Everything the engine can do, and the only thing a user interface talks to.
 * <p>
 * The engine is passive: it does not know who is calling it and never prints. It answers with the
 * immutable records of the dto module, never with its own objects, and reports faults as the
 * unchecked exceptions of {@code guessmarket.engine.exception}, which carry the facts rather than
 * a finished sentence.
 * <p>
 * Every index here is <b>one based</b>, matching what the user sees on screen.
 */
public interface Engine {

    /**
     * Reads a file and, if it is sound, replaces whatever is loaded. A file that fails any check
     * changes nothing.
     *
     * @throws InvalidFileException carrying every fault found in the file.
     */
    LoadSummary loadFromFile(String path);

    boolean isLoaded();

    String loadedSourceDescription();

    /** Every loaded event, numbered from 1 in file order. */
    List<EventSummary> listEvents();

    /** Only the events that still accept trades, numbered from 1. */
    List<EventSummary> listActiveEvents();

    /** @param eventNumber a position in {@link #listEvents()}. */
    EventState eventState(int eventNumber);

    /** @param activeEventNumber a position in {@link #listActiveEvents()}. */
    EventState activeEventState(int activeEventNumber);

    /**
     * @param activeEventNumber a position in {@link #listActiveEvents()}.
     * @param outcomeNumber a position in that event's answer listing.
     */
    PurchaseResult buyShares(int activeEventNumber, int outcomeNumber, long quantity);

    /**
     * Resolves an event on one of its answers and pays the winners.
     *
     * @param activeEventNumber a position in {@link #listActiveEvents()}.
     * @param winningOutcomeNumber a position in that event's answer listing.
     */
    CloseResult closeEvent(int activeEventNumber, int winningOutcomeNumber);

    /**
     * Writes everything loaded, trade history included, for {@link #restoreState} to pick up later.
     *
     * @param pathWithoutExtension full path and file name, no extension.
     * @return the full path actually written.
     */
    String saveState(String pathWithoutExtension);

    /** Replaces whatever is loaded. A failed restore changes nothing. */
    LoadSummary restoreState(String pathWithoutExtension);
}
