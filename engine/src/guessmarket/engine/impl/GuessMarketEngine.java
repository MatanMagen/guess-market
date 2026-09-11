package guessmarket.engine.impl;

import guessmarket.dto.CloseResult;
import guessmarket.dto.CommissionPolicy;
import guessmarket.dto.EventLifecycle;
import guessmarket.dto.EventState;
import guessmarket.dto.EventSummary;
import guessmarket.dto.FillRecord;
import guessmarket.dto.HoldingState;
import guessmarket.dto.LmsrDetails;
import guessmarket.dto.LoadSummary;
import guessmarket.dto.MarketMethod;
import guessmarket.dto.OrderBookDetails;
import guessmarket.dto.OrderResult;
import guessmarket.dto.OrderSide;
import guessmarket.dto.OrderView;
import guessmarket.dto.OutcomeBook;
import guessmarket.dto.OutcomeState;
import guessmarket.dto.ParticipantState;
import guessmarket.dto.PurchaseResult;
import guessmarket.dto.TradeRecord;
import guessmarket.dto.UserDetails;
import guessmarket.dto.UserParticipation;
import guessmarket.dto.UserSummary;
import guessmarket.engine.api.Engine;
import guessmarket.engine.exception.EventAlreadyStartedException;
import guessmarket.engine.exception.EventNotActiveException;
import guessmarket.engine.exception.InsufficientFundsException;
import guessmarket.engine.exception.InsufficientSharesException;
import guessmarket.engine.exception.InvalidPriceException;
import guessmarket.engine.exception.InvalidQuantityException;
import guessmarket.engine.exception.NoFileLoadedException;
import guessmarket.engine.exception.NoSuchEventException;
import guessmarket.engine.exception.NoSuchOutcomeException;
import guessmarket.engine.exception.NoSuchUserException;
import guessmarket.engine.exception.NotMarketMakerException;
import guessmarket.engine.exception.UserBlockedException;
import guessmarket.engine.exception.WrongMarketMethodException;
import guessmarket.engine.model.CommissionType;
import guessmarket.engine.model.Event;
import guessmarket.engine.model.EventStatus;
import guessmarket.engine.model.Fill;
import guessmarket.engine.model.LmsrEvent;
import guessmarket.engine.model.MarketState;
import guessmarket.engine.model.Order;
import guessmarket.engine.model.OrderBook;
import guessmarket.engine.model.OrderBookEvent;
import guessmarket.engine.model.OrderExecution;
import guessmarket.engine.model.Outcome;
import guessmarket.engine.model.Payment;
import guessmarket.engine.model.Settlement;
import guessmarket.engine.model.Trade;
import guessmarket.engine.model.User;
import guessmarket.engine.xml.XmlEventLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The engine. Holds at most one loaded file at a time, guards every request, and answers only in
 * dto records.
 * <p>
 * Nothing here formats anything for a human: a refused request comes back as an exception carrying
 * the numbers involved, and the front end decides what to say about it.
 */
public class GuessMarketEngine implements Engine {

    private static final long MINIMUM_QUANTITY = 1L;

    private final XmlEventLoader loader = new XmlEventLoader();
    private MarketState state;

    @Override
    public LoadSummary loadFromFile(String path) {
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
        requireLoaded();
        return state.sourceDescription();
    }

    @Override
    public List<UserSummary> listUsers() {
        requireLoaded();
        List<UserSummary> summaries = new ArrayList<>();
        List<User> users = state.users();
        for (int i = 0; i < users.size(); i++) {
            User user = users.get(i);
            summaries.add(new UserSummary(i + 1, user.name(), user.balance(), user.isBlocked(),
                    countParticipations(user.name())));
        }
        return summaries;
    }

    @Override
    public UserDetails userDetails(String userName) {
        requireLoaded();
        User user = requireUser(userName);
        List<UserParticipation> participations = new ArrayList<>();
        for (Event event : state.events()) {
            if (event.isParticipant(user.name())) {
                participations.add(toParticipation(event, user.name()));
            }
        }
        return new UserDetails(user.name(), user.balance(), user.isBlocked(), participations);
    }

    @Override
    public List<EventSummary> listEvents() {
        requireLoaded();
        List<EventSummary> summaries = new ArrayList<>();
        List<Event> events = state.events();
        for (int i = 0; i < events.size(); i++) {
            summaries.add(toSummary(events.get(i), i + 1));
        }
        return summaries;
    }

    @Override
    public EventState eventState(int eventId) {
        requireLoaded();
        return toState(requireEvent(eventId));
    }

    @Override
    public EventState openEvent(int eventId, String userName) {
        requireLoaded();
        Event event = requireEvent(eventId);
        User user = requireActingUser(userName);
        requireMarketMaker(event, user);
        if (event.status() != EventStatus.NOT_STARTED) {
            throw new EventAlreadyStartedException(event.name());
        }
        double cost = event.openingCost();
        if (!user.canAfford(cost)) {
            throw new InsufficientFundsException(user.name(), cost, user.balance());
        }
        event.open(user);
        return toState(event);
    }

    @Override
    public CloseResult closeEvent(int eventId, String userName, int outcomeNumber) {
        requireLoaded();
        Event event = requireEvent(eventId);
        User user = requireActingUser(userName);
        requireMarketMaker(event, user);
        requireActive(event);
        int outcomeIndex = requireOutcome(event, outcomeNumber);

        Settlement settlement = event instanceof LmsrEvent lmsr
                ? lmsr.close(outcomeIndex, state.usersByName())
                : ((OrderBookEvent) event).close(outcomeIndex, state.usersByName());

        List<CloseResult.Payout> payouts = new ArrayList<>();
        for (Payment payment : settlement.payments()) {
            payouts.add(new CloseResult.Payout(payment.userName(), payment.winningShares(),
                    payment.gross(), payment.commission(), payment.net()));
        }
        return new CloseResult(settlement.winningOutcomeName(),
                settlement.winningShares(),
                settlement.grossPayout(),
                settlement.commissionCharged(),
                settlement.netPaidToWinners(),
                settlement.subsidyReturned(),
                event.commissionType() == CommissionType.ON_CLOSE,
                payouts,
                toState(event));
    }

    @Override
    public double quoteLmsrPurchase(int eventId, int outcomeNumber, long quantity) {
        requireLoaded();
        LmsrEvent event = requireLmsr(requireEvent(eventId));
        int outcomeIndex = requireOutcome(event, outcomeNumber);
        requireQuantity(quantity);
        return event.quoteBuy(outcomeIndex, quantity);
    }

    @Override
    public PurchaseResult buyLmsrShares(int eventId, String userName, int outcomeNumber, long quantity) {
        requireLoaded();
        LmsrEvent event = requireLmsr(requireEvent(eventId));
        User user = requireActingUser(userName);
        requireActive(event);
        int outcomeIndex = requireOutcome(event, outcomeNumber);
        requireQuantity(quantity);

        Trade trade = event.buy(user, outcomeIndex, quantity, state.user(event.marketMakerName()));
        return new PurchaseResult(user.name(),
                trade.outcomeName(),
                trade.quantity(),
                trade.sharesCost(),
                trade.commissionPaid(),
                trade.totalPaid(),
                event.commissionType() == CommissionType.ON_PURCHASE,
                toState(event));
    }

    @Override
    public OrderResult submitOrder(int eventId, String userName, int outcomeNumber,
                                   OrderSide side, long quantity, double price) {
        requireLoaded();
        OrderBookEvent event = requireOrderBook(requireEvent(eventId));
        User user = requireActingUser(userName);
        requireActive(event);
        int outcomeIndex = requireOutcome(event, outcomeNumber);
        requireQuantity(quantity);
        if (!event.isPriceAllowed(price)) {
            throw new InvalidPriceException(price, event.minimumPrice(), event.maximumPrice());
        }
        if (side == OrderSide.SELL) {
            long sellable = event.sellableQuantity(user.name(), outcomeIndex);
            if (quantity > sellable) {
                throw new InsufficientSharesException(user.name(),
                        event.outcomes().get(outcomeIndex).name(), quantity, Math.max(0L, sellable));
            }
        }

        OrderExecution execution = event.submit(user, outcomeIndex, side, quantity, price, state.usersByName());
        List<FillRecord> fills = new ArrayList<>();
        for (Fill fill : execution.fills()) {
            fills.add(new FillRecord(fill.kind(), fill.outcomeName(), fill.buyerName(), fill.sellerName(),
                    fill.quantity(), fill.price(), fill.buyerCommission()));
        }
        return new OrderResult(execution.orderId(),
                event.outcomes().get(outcomeIndex).name(),
                side,
                quantity,
                execution.quantityFilled(),
                execution.quantityResting(),
                fills,
                execution.cashSpent(),
                execution.cashReceived(),
                execution.commissionPaid(),
                toState(event));
    }

    // ---------- guards ----------

    private void requireLoaded() {
        if (state == null) {
            throw new NoFileLoadedException();
        }
    }

    private User requireUser(String userName) {
        User user = userName == null ? null : state.user(userName.trim());
        if (user == null) {
            throw new NoSuchUserException(String.valueOf(userName));
        }
        return user;
    }

    /** Anybody may be looked at; only an unblocked user may act. */
    private User requireActingUser(String userName) {
        User user = requireUser(userName);
        if (user.isBlocked()) {
            throw new UserBlockedException(user.name(), user.balance());
        }
        return user;
    }

    private Event requireEvent(int eventId) {
        Event event = state.eventById(eventId);
        if (event == null) {
            throw new NoSuchEventException(eventId);
        }
        return event;
    }

    private void requireMarketMaker(Event event, User user) {
        if (!event.isMarketMaker(user.name())) {
            throw new NotMarketMakerException(event.name(), user.name(), event.marketMakerName());
        }
    }

    private void requireActive(Event event) {
        if (event.status() != EventStatus.ACTIVE) {
            throw new EventNotActiveException(event.name(), lifecycleOf(event.status()));
        }
    }

    private int requireOutcome(Event event, int outcomeNumber) {
        int index = outcomeNumber - 1;
        if (!event.hasOutcomeIndex(index)) {
            throw new NoSuchOutcomeException(event.name(), outcomeNumber, event.outcomes().size());
        }
        return index;
    }

    private void requireQuantity(long quantity) {
        if (quantity < MINIMUM_QUANTITY) {
            throw new InvalidQuantityException(quantity, MINIMUM_QUANTITY);
        }
    }

    private LmsrEvent requireLmsr(Event event) {
        if (!(event instanceof LmsrEvent lmsr)) {
            throw new WrongMarketMethodException(event.name(), MarketMethod.LMSR, event.method());
        }
        return lmsr;
    }

    private OrderBookEvent requireOrderBook(Event event) {
        if (!(event instanceof OrderBookEvent orderBook)) {
            throw new WrongMarketMethodException(event.name(), MarketMethod.ORDER_BOOK, event.method());
        }
        return orderBook;
    }

    // ---------- model to dto ----------

    private int countParticipations(String userName) {
        int count = 0;
        for (Event event : state.events()) {
            if (event.isParticipant(userName)) {
                count++;
            }
        }
        return count;
    }

    private EventSummary toSummary(Event event, int displayNumber) {
        List<String> outcomeNames = new ArrayList<>();
        for (Outcome outcome : event.outcomes()) {
            outcomeNames.add(outcome.name());
        }
        return new EventSummary(displayNumber,
                event.id(),
                event.name(),
                event.description(),
                event.commissionPercent(),
                policyOf(event.commissionType()),
                outcomeNames,
                lifecycleOf(event.status()),
                event.method(),
                event.marketMakerName(),
                event.accountBalance());
    }

    private EventState toState(Event event) {
        int displayNumber = state.events().indexOf(event) + 1;
        Outcome winner = event.winningOutcome();
        return new EventState(toSummary(event, displayNumber),
                event instanceof LmsrEvent lmsr ? toLmsrDetails(lmsr) : null,
                event instanceof OrderBookEvent book ? toOrderBookDetails(book) : null,
                toParticipants(event),
                winner == null ? null : winner.name(),
                event.status() == EventStatus.CLOSED);
    }

    private LmsrDetails toLmsrDetails(LmsrEvent event) {
        List<OutcomeState> outcomes = new ArrayList<>();
        for (int i = 0; i < event.outcomes().size(); i++) {
            Outcome outcome = event.outcomes().get(i);
            outcomes.add(new OutcomeState(i + 1, outcome.name(), event.price(i), outcome.sharesBought()));
        }
        List<TradeRecord> history = new ArrayList<>();
        for (Trade trade : event.trades()) {
            history.add(toRecord(trade));
        }
        Collections.reverse(history);
        return new LmsrDetails(event.liquidity(), outcomes, event.openingSubsidy(),
                event.marketMakerNetResult(), event.commissionCollected(), history);
    }

    private OrderBookDetails toOrderBookDetails(OrderBookEvent event) {
        List<OutcomeBook> books = new ArrayList<>();
        for (int i = 0; i < event.books().size(); i++) {
            OrderBook book = event.books().get(i);
            books.add(new OutcomeBook(i + 1,
                    book.outcomeName(),
                    toOrderViews(book.bids()),
                    toOrderViews(book.asks()),
                    book.lastTradePrice(),
                    book.bestBid() == null ? null : book.bestBid().price(),
                    book.bestAsk() == null ? null : book.bestAsk().price(),
                    book.mid(),
                    book.spread(),
                    event.outcomes().get(i).sharesBought()));
        }
        return new OrderBookDetails(event.basePrice(), event.isMintAllowed(), event.initialInvestment(),
                event.commissionCollected(), books);
    }

    private List<OrderView> toOrderViews(List<Order> orders) {
        List<OrderView> views = new ArrayList<>();
        for (Order order : orders) {
            views.add(new OrderView(order.id(), order.userName(), order.remaining(), order.price()));
        }
        return views;
    }

    private List<ParticipantState> toParticipants(Event event) {
        List<ParticipantState> participants = new ArrayList<>();
        for (String name : event.participants()) {
            participants.add(new ParticipantState(name, holdingsOf(event, name), countRestingOrders(event, name)));
        }
        return participants;
    }

    private List<HoldingState> holdingsOf(Event event, String userName) {
        List<HoldingState> holdings = new ArrayList<>();
        for (int i = 0; i < event.outcomes().size(); i++) {
            holdings.add(new HoldingState(event.outcomes().get(i).name(),
                    event.holdingOf(userName, i), event.amountPaidBy(userName, i)));
        }
        return holdings;
    }

    private long countRestingOrders(Event event, String userName) {
        if (!(event instanceof OrderBookEvent book)) {
            return 0L;
        }
        long count = 0L;
        for (OrderBook outcomeBook : book.books()) {
            count += outcomeBook.ordersOf(userName).size();
        }
        return count;
    }

    private UserParticipation toParticipation(Event event, String userName) {
        List<TradeRecord> trades = new ArrayList<>();
        List<OrderView> resting = new ArrayList<>();
        if (event instanceof LmsrEvent lmsr) {
            for (Trade trade : lmsr.tradesOf(userName)) {
                trades.add(toRecord(trade));
            }
            Collections.reverse(trades);
        } else if (event instanceof OrderBookEvent book) {
            for (OrderBook outcomeBook : book.books()) {
                resting.addAll(toOrderViews(outcomeBook.ordersOf(userName)));
            }
        }
        return new UserParticipation(event.id(),
                event.name(),
                event.method(),
                lifecycleOf(event.status()),
                event.isMarketMaker(userName),
                trades,
                holdingsOf(event, userName),
                resting,
                event.commissionPaidBy(userName),
                profitOrLossOf(event, userName),
                event.winningOutcome() == null ? null : event.winningOutcome().name());
    }

    /**
     * What the user is up on this event: everything he has taken out of it less everything he put
     * in. Only complete once the event has closed and the winnings have been paid.
     */
    private double profitOrLossOf(Event event, String userName) {
        double net = 0.0d;
        for (int i = 0; i < event.outcomes().size(); i++) {
            net -= event.amountPaidBy(userName, i);
        }
        net -= event.commissionPaidBy(userName);
        if (event.status() == EventStatus.CLOSED && event.winningOutcome() != null) {
            int winningIndex = event.outcomes().indexOf(event.winningOutcome());
            net += payoutPerShare(event) * event.holdingOf(userName, winningIndex);
        }
        return net;
    }

    private double payoutPerShare(Event event) {
        return event instanceof OrderBookEvent book ? book.basePrice() : 1.0d;
    }

    private TradeRecord toRecord(Trade trade) {
        return new TradeRecord(trade.userName(), trade.outcomeName(), trade.quantity(),
                trade.sharesCost(), trade.commissionPaid(), trade.totalPaid());
    }

    private CommissionPolicy policyOf(CommissionType type) {
        return type == CommissionType.ON_PURCHASE ? CommissionPolicy.ON_PURCHASE : CommissionPolicy.ON_CLOSE;
    }

    private EventLifecycle lifecycleOf(EventStatus status) {
        return switch (status) {
            case NOT_STARTED -> EventLifecycle.NOT_STARTED;
            case ACTIVE -> EventLifecycle.ACTIVE;
            case CLOSED -> EventLifecycle.CLOSED;
        };
    }
}
