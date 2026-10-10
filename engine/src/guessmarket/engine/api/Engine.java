package guessmarket.engine.api;

import guessmarket.dto.CloseResult;
import guessmarket.dto.EventState;
import guessmarket.dto.EventSummary;
import guessmarket.dto.LoadSummary;
import guessmarket.dto.NewEvent;
import guessmarket.dto.OrderResult;
import guessmarket.dto.OrderSide;
import guessmarket.dto.PurchaseResult;
import guessmarket.dto.UserDetails;
import guessmarket.dto.UserSummary;
import guessmarket.engine.exception.InvalidFileException;

import java.io.InputStream;
import java.util.List;

/**
 * Everything the engine can do, and the only thing a front end talks to. Since exercise 3 that
 * front end is the server, which calls it on behalf of many users at once.
 * <p>
 * The engine is passive: it does not know who is calling it and never prints. It answers with the
 * immutable records of the dto module, never with its own objects, and reports faults as the
 * unchecked exceptions of {@code guessmarket.engine.exception}, which carry the facts rather than
 * a finished sentence.
 * <p>
 * Events are addressed by the number the engine gave them when they were uploaded, so a front end
 * that filters or sorts its view cannot address the wrong one. Users are addressed by name, which
 * registration keeps unique.
 */
public interface Engine {

    /**
     * Brings a new user into the system with an empty account.
     *
     * @throws guessmarket.engine.exception.UserAlreadyExistsException if the name is taken.
     * @throws guessmarket.engine.exception.InvalidUserNameException if the name is blank.
     */
    void registerUser(String userName);

    /** Loads money into a user's account, which is also how a blocked user gets unblocked. */
    UserDetails depositFunds(String userName, double amount);

    /**
     * Reads an uploaded file and, if it is sound, adds every event in it to the system, with the
     * uploader as their market maker. A file that fails any check adds nothing.
     *
     * @param fileName what the file was called on the uploader's machine.
     * @throws InvalidFileException carrying every fault found in the file.
     */
    LoadSummary uploadEvents(InputStream xml, String fileName, String uploaderName);

    /** Every user, in the order they joined. */
    List<UserSummary> listUsers();

    /** One user with his account lines and every event he runs or has taken part in. */
    UserDetails userDetails(String userName);

    /** Every event, in the order it was uploaded. A front end filters this list itself. */
    List<EventSummary> listEvents();

    EventState eventState(int eventId);

    /**
     * Starts an event, moving the market maker's stake into its account: the subsidy for LMSR, the
     * initial stock of shares for an order book.
     *
     * @throws guessmarket.engine.exception.NotMarketMakerException if somebody else asked.
     * @throws guessmarket.engine.exception.InsufficientFundsException if he cannot fund it.
     */
    EventState openEvent(int eventId, String userName);

    /**
     * Resolves an event on one of its answers and pays the winners.
     *
     * @param outcomeNumber a position in that event's answer listing, counted from 1.
     */
    CloseResult closeEvent(int eventId, String userName, int outcomeNumber);

    /** What an LMSR purchase would cost right now, without making it. */
    double quoteLmsrPurchase(int eventId, int outcomeNumber, long quantity);

    /**
     * Buys shares of an LMSR event for a user.
     *
     * @param outcomeNumber a position in that event's answer listing, counted from 1.
     */
    PurchaseResult buyLmsrShares(int eventId, String userName, int outcomeNumber, long quantity);

    /**
     * Brings a new event into being, with the user who asked for it as its market maker. It is
     * checked against the same rules an uploaded file has to satisfy, and once accepted it behaves
     * like any other event: not started, with an empty account, waiting for its maker to open it.
     *
     * @throws guessmarket.engine.exception.InvalidEventException carrying every fault found.
     */
    EventState createEvent(NewEvent request);

    /**
     * Puts an order into one answer's book, matching it against whatever is already resting and
     * leaving the remainder behind.
     *
     * @param outcomeNumber a position in that event's answer listing, counted from 1.
     * @param price per share, between 0.01 and d minus 0.01.
     */
    OrderResult submitOrder(int eventId, String userName, int outcomeNumber,
                            OrderSide side, long quantity, double price);
}
