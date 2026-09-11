package guessmarket.ui;

import guessmarket.dto.FileProblem;
import guessmarket.engine.exception.EventAlreadyStartedException;
import guessmarket.engine.exception.EventNotActiveException;
import guessmarket.engine.exception.GuessMarketException;
import guessmarket.engine.exception.InsufficientFundsException;
import guessmarket.engine.exception.InsufficientSharesException;
import guessmarket.engine.exception.InvalidFileException;
import guessmarket.engine.exception.InvalidPriceException;
import guessmarket.engine.exception.InvalidQuantityException;
import guessmarket.engine.exception.NoActiveEventsException;
import guessmarket.engine.exception.NoFileLoadedException;
import guessmarket.engine.exception.NoSuchEventException;
import guessmarket.engine.exception.NoSuchOutcomeException;
import guessmarket.engine.exception.NoSuchUserException;
import guessmarket.engine.exception.NotMarketMakerException;
import guessmarket.engine.exception.UserBlockedException;
import guessmarket.engine.exception.WrongMarketMethodException;

/**
 * Turns the facts the engine reports into English. The engine says which fault occurred and the
 * values involved; the wording is decided here, so the application's whole vocabulary sits in one
 * file and another front end can phrase the same facts differently.
 */
public final class Messages {

    private Messages() {
    }

    public static String describe(GuessMarketException failure) {
        return switch (failure) {
            case NoFileLoadedException ignored ->
                    "No system file is loaded yet. Use Load file first.";
            case NoActiveEventsException ignored ->
                    "Every event has already been closed, so there is nothing left to trade.";
            case NoSuchEventException e ->
                    "There is no event numbered " + e.eventId() + " in the loaded file.";
            case NoSuchOutcomeException e -> "There is no answer number " + e.requested()
                    + " for event '" + e.eventName() + "'. Choose between 1 and " + e.available() + ".";
            case NoSuchUserException e -> "There is no user named '" + e.userName() + "'.";
            case UserBlockedException e -> "'" + e.userName() + "' went into overdraft and is blocked. "
                    + "His balance is " + Format.money(e.balance())
                    + ", and an account cannot be topped up.";
            case NotMarketMakerException e -> "Only '" + e.marketMakerName()
                    + "' can open or close '" + e.eventName() + "'. '" + e.userName() + "' cannot.";
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
                    + Format.money(e.required()) + " to start this event but holds only "
                    + Format.money(e.available()) + ".";
            case InsufficientSharesException e -> "'" + e.userName() + "' has "
                    + Format.quantity(e.available()) + " share(s) of '" + e.outcomeName()
                    + "' left to offer, not " + Format.quantity(e.requested())
                    + ". Shares already on offer cannot be offered twice.";
            case InvalidPriceException e -> "A price must be between "
                    + Format.price(e.minimum()) + " and " + Format.price(e.maximum())
                    + ". A share can never be worth more than it pays out.";
            case InvalidQuantityException e ->
                    "The number of shares must be at least " + e.minimum() + ".";
            case WrongMarketMethodException e -> "Event '" + e.eventName() + "' is traded by "
                    + Format.method(e.actual()) + ", so that action does not apply to it.";
            case InvalidFileException e -> describeFile(e);
            default -> failure.getMessage();
        };
    }

    private static String describeFile(InvalidFileException failure) {
        StringBuilder text = new StringBuilder("The file could not be loaded:\n");
        for (FileProblem problem : failure.problems()) {
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
            case NO_PATH_GIVEN -> "no file was chosen.";
            case FILE_NOT_FOUND -> "there is no file at " + problem.value(0) + ".";
            case PATH_IS_A_FOLDER -> problem.value(0) + " is a folder, not a file.";
            case FILE_NOT_READABLE -> problem.value(0) + " cannot be read.";
            case WRONG_EXTENSION -> "a system file must end in " + problem.value(1) + ".";
            case UNREADABLE_XML -> "the XML could not be read (" + problem.value(1) + ").";
            case NO_EVENTS -> "the file defines no events.";
            case NO_USERS -> "the file defines no users.";
            case DUPLICATE_EVENT_ID -> "event number " + problem.value(0)
                    + " is already used by event " + problem.value(1) + ".";
            case BLANK_EVENT_NAME -> "the event has no name.";
            case MISSING_COMMISSION -> "the event has no commission.";
            case COMMISSION_OUT_OF_RANGE -> "a commission of " + problem.value(0)
                    + " is outside " + problem.value(1) + " to " + problem.value(2) + ".";
            case UNKNOWN_COMMISSION_TYPE -> "'" + problem.value(0)
                    + "' is not a commission type. Use on-purchase or on-close.";
            case WRONG_ANSWER_COUNT -> "an event needs exactly " + problem.value(1)
                    + " answers, and this one has " + problem.value(0) + ".";
            case BLANK_ANSWER -> "answer " + problem.value(0) + " is empty.";
            case DUPLICATE_ANSWER -> "'" + problem.value(0) + "' is given twice as an answer.";
            case MISSING_METHOD -> "the event names no trading method.";
            case MISSING_LMSR -> "the LMSR settings are missing.";
            case LIQUIDITY_NOT_POSITIVE -> "b must be greater than 0, and it is " + problem.value(0) + ".";
            case BLANK_USER_NAME -> "user " + problem.value(0) + " has no name.";
            case DUPLICATE_USER_NAME -> "the name '" + problem.value(0)
                    + "' is already used by user " + problem.value(1) + ".";
            case INITIAL_CASH_NOT_POSITIVE -> "user '" + problem.value(0)
                    + "' opens with " + problem.value(1) + ", and a balance must start above 0.";
            case MARKET_MAKER_OF_UNKNOWN_EVENT -> "'" + problem.value(0)
                    + "' is market maker of event " + problem.value(1) + ", which the file does not define.";
            case MARKET_MAKER_TWICE_OF_SAME_EVENT -> "'" + problem.value(0)
                    + "' claims event " + problem.value(1) + " twice.";
            case EVENT_WITHOUT_MARKET_MAKER -> "no user is its market maker.";
            case EVENT_WITH_SEVERAL_MARKET_MAKERS -> "it has more than one market maker ("
                    + problem.value(0) + ").";
            case MISSING_ORDER_BOOK -> "the order book settings are missing.";
            case BASE_PRICE_NOT_POSITIVE -> "d must be greater than 0, and it is " + problem.value(0) + ".";
            case INITIAL_INVESTMENT_NEGATIVE -> "the initial investment cannot be negative, and it is "
                    + problem.value(0) + ".";
            case UNKNOWN_MINT_FLAG -> "'" + problem.value(0) + "' is not a yes or no for allow-mint.";
        };
    }
}
