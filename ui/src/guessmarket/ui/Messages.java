package guessmarket.ui;

import guessmarket.dto.CommissionPolicy;
import guessmarket.dto.EventLifecycle;
import guessmarket.dto.FileProblem;
import guessmarket.engine.exception.EventClosedException;
import guessmarket.engine.exception.GuessMarketException;
import guessmarket.engine.exception.InvalidQuantityException;
import guessmarket.engine.exception.NoActiveEventsException;
import guessmarket.engine.exception.NoFileLoadedException;
import guessmarket.engine.exception.NoSuchEventException;
import guessmarket.engine.exception.NoSuchOutcomeException;
import guessmarket.engine.exception.StatePersistenceException;

/**
 * Turns the facts the engine reports into English. The engine says which fault occurred and the
 * values involved; the wording is decided here, so the console's whole vocabulary sits in one file
 * and another front end can phrase the same facts differently.
 */
public final class Messages {

    private Messages() {
    }

    public static String describe(GuessMarketException failure) {
        return switch (failure) {
            case NoFileLoadedException ignored ->
                    "No system file is loaded yet. Use the 'Load system file' command first.";
            case NoActiveEventsException ignored ->
                    "Every event has already been closed, so there is nothing left to trade.";
            case NoSuchEventException e -> "There is no "
                    + (e.activeOnly() ? "active event" : "event") + " number " + e.requested()
                    + ". Choose a number between 1 and " + e.available() + ".";
            case NoSuchOutcomeException e -> "There is no answer number " + e.requested()
                    + " for event '" + e.eventName() + "'. Choose a number between 1 and "
                    + e.available() + ".";
            case InvalidQuantityException e -> "The number of shares must be at least "
                    + e.minimum() + ".";
            case EventClosedException e -> "Event '" + e.eventName()
                    + "' is closed, so it cannot be traded or closed again.";
            case StatePersistenceException e -> "The system state could not be "
                    + (e.direction() == StatePersistenceException.Direction.SAVING ? "saved" : "loaded")
                    + ": " + e.reason() + ".";
            default -> "The request could not be carried out.";
        };
    }

    /** Names the offending event where the fault belongs to one. */
    public static String describe(FileProblem problem) {
        String detail = detailOf(problem);
        if (!problem.belongsToAnEvent()) {
            return detail;
        }
        String name = problem.eventName() == null || problem.eventName().isEmpty()
                ? "unnamed"
                : problem.eventName();
        return "Event at position " + problem.eventPosition() + " ('" + name + "'): " + detail;
    }

    private static String detailOf(FileProblem problem) {
        return switch (problem.kind()) {
            case NO_PATH_GIVEN ->
                    "No file path was entered.";
            case FILE_NOT_FOUND ->
                    "There is no file at '" + problem.value(0) + "'. Check the path and try again.";
            case PATH_IS_A_FOLDER ->
                    "'" + problem.value(0) + "' is a folder, not a file.";
            case FILE_NOT_READABLE ->
                    "The file '" + problem.value(0) + "' cannot be read: permission was denied.";
            case WRONG_EXTENSION ->
                    "The file '" + problem.value(0) + "' is not an XML file: a Guess Market file must end with "
                            + problem.value(1) + ".";
            case UNREADABLE_XML ->
                    "The file '" + problem.value(0) + "' could not be read as a Guess Market XML file: "
                            + problem.value(1) + ".";
            case NO_EVENTS ->
                    "The file contains no events. A Guess Market file must describe at least one event.";
            case DUPLICATE_EVENT_ID ->
                    "event number " + problem.value(0) + " is already used by the event at position "
                            + problem.value(1) + ". Every event must have its own number.";
            case BLANK_EVENT_NAME ->
                    "the event name is empty.";
            case MISSING_COMMISSION ->
                    "the commission is missing.";
            case COMMISSION_OUT_OF_RANGE ->
                    "the commission is " + problem.value(0) + "%, which is outside the allowed range of "
                            + problem.value(1) + "% to " + problem.value(2) + "%.";
            case UNKNOWN_COMMISSION_TYPE ->
                    "the commission type '" + problem.value(0) + "' is not recognised. It must be either "
                            + label(CommissionPolicy.ON_PURCHASE) + " or " + label(CommissionPolicy.ON_CLOSE) + ".";
            case WRONG_ANSWER_COUNT ->
                    "the event offers " + problem.value(0) + " answer(s), but every event must offer exactly "
                            + problem.value(1) + ".";
            case BLANK_ANSWER ->
                    "answer number " + problem.value(0) + " is empty.";
            case DUPLICATE_ANSWER ->
                    "the answer '" + problem.value(0)
                            + "' appears more than once. The answers of an event must differ.";
            case MISSING_METHOD ->
                    "the trading method is missing.";
            case MISSING_LMSR ->
                    "the trading method is missing its LMSR details. LMSR is the only method supported "
                            + "in this exercise.";
            case LIQUIDITY_NOT_POSITIVE ->
                    "the liquidity b is " + problem.value(0) + ", but it must be a positive whole number.";
        };
    }

    public static String label(CommissionPolicy policy) {
        return switch (policy) {
            case ON_PURCHASE -> "on-purchase";
            case ON_CLOSE -> "on-close";
        };
    }

    public static String label(EventLifecycle lifecycle) {
        return switch (lifecycle) {
            case ACTIVE -> "Active";
            case CLOSED -> "Closed";
        };
    }
}
