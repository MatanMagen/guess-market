package guessmarket.server;

import guessmarket.dto.FileProblem;
import guessmarket.dto.MarketMethod;
import guessmarket.engine.exception.EventAlreadyStartedException;
import guessmarket.engine.exception.EventNotActiveException;
import guessmarket.engine.exception.GuessMarketException;
import guessmarket.engine.exception.InsufficientFundsException;
import guessmarket.engine.exception.InsufficientSharesException;
import guessmarket.engine.exception.InvalidAmountException;
import guessmarket.engine.exception.InvalidEventException;
import guessmarket.engine.exception.InvalidFileException;
import guessmarket.engine.exception.InvalidPriceException;
import guessmarket.engine.exception.InvalidQuantityException;
import guessmarket.engine.exception.InvalidUserNameException;
import guessmarket.engine.exception.NoSuchEventException;
import guessmarket.engine.exception.NoSuchOutcomeException;
import guessmarket.engine.exception.NoSuchUserException;
import guessmarket.engine.exception.NotMarketMakerException;
import guessmarket.engine.exception.UserAlreadyExistsException;
import guessmarket.engine.exception.UserBlockedException;
import guessmarket.engine.exception.WrongMarketMethodException;

import java.util.List;
import java.util.Locale;

/**
 * Turns the facts the engine reports into English. The engine says which fault occurred and the
 * values involved; the wording is decided here, so the whole vocabulary of the system sits in one
 * file, and every client — the JavaFX one, or a web page — shows the same sentence.
 */
public final class Messages {

    private Messages() {
    }

    public static String describe(GuessMarketException failure) {
        return switch (failure) {
            case UserAlreadyExistsException e -> "The name '" + e.userName()
                    + "' is already taken. Please choose a different name.";
            case InvalidUserNameException ignored -> "Please enter a name to log in with.";
            case InvalidAmountException e -> "The amount to load must be a positive number, not "
                    + e.requested() + ".";
            case NoSuchEventException e -> "There is no event numbered " + e.eventId() + ".";
            case NoSuchOutcomeException e -> "There is no answer number " + e.requested()
                    + " for event '" + e.eventName() + "'. Choose between 1 and " + e.available() + ".";
            case NoSuchUserException e -> "There is no user named '" + e.userName() + "'.";
            case UserBlockedException e -> "'" + e.userName() + "' went into overdraft and is blocked. "
                    + "The balance is " + money(e.balance())
                    + ". Load money into the account to bring it back to zero or above.";
            case NotMarketMakerException e -> "Only '" + e.marketMakerName()
                    + "' can start or close '" + e.eventName() + "'. '" + e.userName() + "' cannot.";
            case EventAlreadyStartedException e ->
                    "Event '" + e.eventName() + "' has already been started and cannot be started again.";
            case EventNotActiveException e -> switch (e.status()) {
                case NOT_STARTED -> "Event '" + e.eventName()
                        + "' has not been started yet, so it cannot be traded.";
                case CLOSED -> "Event '" + e.eventName()
                        + "' is closed, so it cannot be traded or closed again.";
                case ACTIVE -> "Event '" + e.eventName() + "' is not available.";
            };
            case InsufficientFundsException e -> "'" + e.userName() + "' needs "
                    + money(e.required()) + " to start this event but holds only "
                    + money(e.available()) + ". Load money into the account first.";
            case InsufficientSharesException e -> "'" + e.userName() + "' has "
                    + quantity(e.available()) + " share(s) of '" + e.outcomeName()
                    + "' left to offer, not " + quantity(e.requested())
                    + ". Shares already on offer cannot be offered twice.";
            case InvalidPriceException e -> "A price must be between "
                    + price(e.minimum()) + " and " + price(e.maximum())
                    + ". A share can never be worth more than it pays out.";
            case InvalidQuantityException e ->
                    "The number of shares must be at least " + e.minimum() + ".";
            case WrongMarketMethodException e -> "Event '" + e.eventName() + "' is traded by "
                    + method(e.actual()) + ", so that action does not apply to it.";
            case InvalidFileException e -> describeProblems(
                    "The file '" + e.fileName() + "' was not loaded, and none of its events were added:",
                    e.problems());
            case InvalidEventException e -> describeProblems("The event could not be created:", e.problems());
            default -> failure.getMessage();
        };
    }

    private static String describeProblems(String heading, List<FileProblem> problems) {
        StringBuilder text = new StringBuilder(heading).append("\n");
        for (FileProblem problem : problems) {
            text.append("\n  • ").append(describeProblem(problem));
        }
        return text.toString();
    }

    private static String describeProblem(FileProblem problem) {
        String where = problem.belongsToAnEvent()
                ? "Event " + problem.eventPosition()
                + (problem.eventName() == null || problem.eventName().isEmpty()
                        ? "" : " ('" + problem.eventName() + "')") + ": "
                : "";
        return where + switch (problem.kind()) {
            case WRONG_EXTENSION -> "a Guess Market file must end in " + problem.value(1)
                    + ", and '" + problem.value(0) + "' does not.";
            case UNREADABLE_XML -> "the XML could not be read (" + problem.value(1) + ").";
            case NO_EVENTS -> "the file defines no events.";
            case BLANK_EVENT_NAME -> "the event has no name.";
            case DUPLICATE_EVENT_NAME_IN_FILE -> "the name '" + problem.value(0)
                    + "' is already used by event " + problem.value(1) + " of this file.";
            case DUPLICATE_EVENT_NAME -> "there is already an event called '" + problem.value(0)
                    + "' in the system.";
            case MISSING_COMMISSION -> "the event has no commission.";
            case COMMISSION_OUT_OF_RANGE -> "a commission of " + problem.value(0)
                    + " is outside " + problem.value(1) + " to " + problem.value(2) + ".";
            case UNKNOWN_COMMISSION_TYPE -> "'" + problem.value(0)
                    + "' is not a commission type. Use on-purchase or on-close.";
            case WRONG_ANSWER_COUNT -> "an event needs at least " + problem.value(1)
                    + " answers, and this one has " + problem.value(0) + ".";
            case BLANK_ANSWER -> "answer " + problem.value(0) + " is empty.";
            case DUPLICATE_ANSWER -> "'" + problem.value(0) + "' is given twice as an answer.";
            case MISSING_METHOD -> "the event names no trading method.";
            case LIQUIDITY_NOT_POSITIVE -> "b must be greater than 0, and it is " + problem.value(0) + ".";
            case BASE_PRICE_NOT_POSITIVE -> "d must be greater than 0, and it is " + problem.value(0) + ".";
            case INITIAL_INVESTMENT_NEGATIVE -> "the initial investment cannot be negative, and it is "
                    + problem.value(0) + ".";
            case UNKNOWN_MINT_FLAG -> "'" + problem.value(0) + "' is not true or false for allow-mint.";
        };
    }

    private static String money(double amount) {
        return String.format(Locale.US, "%,.2f", amount);
    }

    private static String price(double price) {
        return String.format(Locale.US, "%.2f", price);
    }

    private static String quantity(long quantity) {
        return String.format(Locale.US, "%,d", quantity);
    }

    private static String method(MarketMethod method) {
        return method == MarketMethod.LMSR ? "LMSR" : "order book";
    }
}
