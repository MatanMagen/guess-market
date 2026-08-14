package guessmarket.engine.impl;

import guessmarket.dto.CloseResult;
import guessmarket.dto.CommissionPolicy;
import guessmarket.dto.EventLifecycle;
import guessmarket.dto.EventState;
import guessmarket.dto.EventSummary;
import guessmarket.dto.LoadSummary;
import guessmarket.dto.OutcomeState;
import guessmarket.dto.PurchaseResult;
import guessmarket.dto.TradeRecord;
import guessmarket.engine.api.Engine;
import guessmarket.engine.exception.EventClosedException;
import guessmarket.engine.exception.InvalidQuantityException;
import guessmarket.engine.exception.NoActiveEventsException;
import guessmarket.engine.exception.NoFileLoadedException;
import guessmarket.engine.exception.NoSuchEventException;
import guessmarket.engine.exception.NoSuchOutcomeException;
import guessmarket.engine.exception.StatePersistenceException;
import guessmarket.engine.model.CommissionType;
import guessmarket.engine.model.Event;
import guessmarket.engine.model.EventStatus;
import guessmarket.engine.model.MarketState;
import guessmarket.engine.model.Outcome;
import guessmarket.engine.model.Trade;
import guessmarket.engine.persistence.StateStore;
import guessmarket.engine.xml.XmlEventLoader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The Guess Market engine.
 * <p>
 * It holds exactly one {@link MarketState} at a time and answers requests about it. It
 * translates the one based numbers the user sees into the zero based indexes the model uses,
 * and it converts its own objects into the transfer objects of the {@code guessmarket.dto}
 * module before answering, all the way down to individual trade lines, so nothing a caller
 * receives offers a route back into the state.
 */
public class GuessMarketEngine implements Engine {

    /** The smallest share quantity a purchase may ask for. */
    private static final long MINIMUM_QUANTITY = 1L;

    private final XmlEventLoader loader = new XmlEventLoader();
    private final StateStore stateStore = new StateStore();

    private MarketState state;

    @Override
    public LoadSummary loadFromFile(String path) {
        // The loader throws if anything is wrong, so reaching the next line means the file was
        // sound; only then is the previous state let go of.
        MarketState loaded = loader.load(path);
        state = loaded;
        return new LoadSummary(loaded.sourceDescription(), loaded.events().size());
    }

    @Override
    public boolean isLoaded() {
        return state != null;
    }

    @Override
    public String loadedSourceDescription() {
        return requireState().sourceDescription();
    }

    @Override
    public List<EventSummary> listEvents() {
        return summarize(requireState().events());
    }

    @Override
    public List<EventSummary> listActiveEvents() {
        return summarize(requireActiveEvents());
    }

    @Override
    public EventState eventState(int eventNumber) {
        List<Event> events = requireState().events();
        return describe(pick(events, eventNumber, false), eventNumber);
    }

    @Override
    public EventState activeEventState(int activeEventNumber) {
        List<Event> active = requireActiveEvents();
        return describe(pick(active, activeEventNumber, true), activeEventNumber);
    }

    @Override
    public PurchaseResult buyShares(int activeEventNumber, int outcomeNumber, long quantity) {
        if (quantity < MINIMUM_QUANTITY) {
            throw new InvalidQuantityException(quantity, MINIMUM_QUANTITY);
        }
        Event event = pick(requireActiveEvents(), activeEventNumber, true);
        int outcomeIndex = outcomeIndexOf(event, outcomeNumber);

        Trade trade;
        try {
            trade = event.buy(outcomeIndex, quantity);
        } catch (IllegalStateException e) {
            throw new EventClosedException(event.name());
        }

        return new PurchaseResult(
                trade.outcomeName(),
                trade.quantity(),
                trade.sharesCost(),
                trade.commissionPaid(),
                trade.totalPaid(),
                event.commissionType() == CommissionType.ON_PURCHASE,
                describe(event, positionOf(event)));
    }

    @Override
    public CloseResult closeEvent(int activeEventNumber, int winningOutcomeNumber) {
        Event event = pick(requireActiveEvents(), activeEventNumber, true);
        int winningIndex = outcomeIndexOf(event, winningOutcomeNumber);

        Event.Settlement settlement;
        try {
            settlement = event.close(winningIndex);
        } catch (IllegalStateException e) {
            throw new EventClosedException(event.name());
        }

        return new CloseResult(
                settlement.winningOutcomeName(),
                settlement.winningShares(),
                settlement.grossPayout(),
                settlement.commissionCharged(),
                settlement.netPaidToWinners(),
                event.commissionType() == CommissionType.ON_CLOSE,
                describe(event, positionOf(event)));
    }

    @Override
    public String saveState(String pathWithoutExtension) {
        MarketState current = requireState();
        if (pathWithoutExtension == null || pathWithoutExtension.isBlank()) {
            throw new StatePersistenceException(StatePersistenceException.Direction.SAVING,
                    "no file path was entered", null);
        }
        try {
            return stateStore.save(pathWithoutExtension, current);
        } catch (IOException e) {
            throw new StatePersistenceException(StatePersistenceException.Direction.SAVING,
                    message(e), e);
        }
    }

    @Override
    public LoadSummary restoreState(String pathWithoutExtension) {
        if (pathWithoutExtension == null || pathWithoutExtension.isBlank()) {
            throw new StatePersistenceException(StatePersistenceException.Direction.LOADING,
                    "no file path was entered", null);
        }
        MarketState restored;
        try {
            restored = stateStore.load(pathWithoutExtension);
        } catch (IOException e) {
            throw new StatePersistenceException(StatePersistenceException.Direction.LOADING,
                    message(e), e);
        } catch (ClassNotFoundException e) {
            throw new StatePersistenceException(StatePersistenceException.Direction.LOADING,
                    "the file was written by a different version of this application", e);
        }
        // Only now, once the file has been read in full, is the previous state let go of.
        state = restored;
        return new LoadSummary(state.sourceDescription(), state.events().size());
    }

    private MarketState requireState() {
        if (state == null) {
            throw new NoFileLoadedException();
        }
        return state;
    }

    private List<Event> requireActiveEvents() {
        List<Event> active = requireState().activeEvents();
        if (active.isEmpty()) {
            throw new NoActiveEventsException();
        }
        return active;
    }

    private Event pick(List<Event> events, int oneBasedNumber, boolean activeOnly) {
        if (oneBasedNumber < 1 || oneBasedNumber > events.size()) {
            throw new NoSuchEventException(oneBasedNumber, events.size(), activeOnly);
        }
        return events.get(oneBasedNumber - 1);
    }

    private int outcomeIndexOf(Event event, int oneBasedNumber) {
        int index = oneBasedNumber - 1;
        if (!event.hasOutcomeIndex(index)) {
            throw new NoSuchOutcomeException(event.name(), oneBasedNumber, event.outcomes().size());
        }
        return index;
    }

    private int positionOf(Event event) {
        return requireState().events().indexOf(event) + 1;
    }

    private List<EventSummary> summarize(List<Event> events) {
        List<EventSummary> summaries = new ArrayList<>();
        for (int i = 0; i < events.size(); i++) {
            summaries.add(summarize(events.get(i), i + 1));
        }
        return summaries;
    }

    private EventSummary summarize(Event event, int displayNumber) {
        List<String> outcomeNames = new ArrayList<>();
        for (Outcome outcome : event.outcomes()) {
            outcomeNames.add(outcome.name());
        }
        return new EventSummary(
                displayNumber,
                event.id(),
                event.name(),
                event.description(),
                event.commissionPercent(),
                toPolicy(event.commissionType()),
                outcomeNames,
                toLifecycle(event.status()));
    }

    private EventState describe(Event event, int displayNumber) {
        List<OutcomeState> outcomes = new ArrayList<>();
        List<Outcome> eventOutcomes = event.outcomes();
        for (int i = 0; i < eventOutcomes.size(); i++) {
            Outcome outcome = eventOutcomes.get(i);
            outcomes.add(new OutcomeState(i + 1, outcome.name(), event.price(i), outcome.sharesBought()));
        }

        // The exercise asks for the newest trade first, while the model keeps them in the order
        // they happened.
        List<Trade> trades = event.trades();
        List<TradeRecord> history = new ArrayList<>();
        for (int i = trades.size() - 1; i >= 0; i--) {
            Trade trade = trades.get(i);
            history.add(new TradeRecord(trade.outcomeName(), trade.quantity(), trade.sharesCost(),
                    trade.commissionPaid(), trade.totalPaid()));
        }

        Outcome winner = event.winningOutcome();
        return new EventState(
                summarize(event, displayNumber),
                event.liquidity(),
                outcomes,
                event.accountBalance(),
                event.openingSubsidy(),
                event.marketMakerNetResult(),
                event.commissionCollected(),
                history,
                winner == null ? null : winner.name(),
                winner != null);
    }

    private CommissionPolicy toPolicy(CommissionType type) {
        return type == CommissionType.ON_PURCHASE ? CommissionPolicy.ON_PURCHASE : CommissionPolicy.ON_CLOSE;
    }

    private EventLifecycle toLifecycle(EventStatus status) {
        return status == EventStatus.ACTIVE ? EventLifecycle.ACTIVE : EventLifecycle.CLOSED;
    }

    private String message(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message.trim();
    }
}
