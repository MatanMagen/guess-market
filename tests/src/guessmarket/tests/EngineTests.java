package guessmarket.tests;

import guessmarket.dto.CloseResult;
import guessmarket.dto.EventLifecycle;
import guessmarket.dto.EventState;
import guessmarket.dto.FillKind;
import guessmarket.dto.CommissionPolicy;
import guessmarket.dto.MarketMethod;
import guessmarket.dto.NewEvent;
import guessmarket.dto.OrderResult;
import guessmarket.dto.OrderSide;
import guessmarket.dto.OutcomeBook;
import guessmarket.dto.ProblemKind;
import guessmarket.dto.UserSummary;
import guessmarket.engine.api.Engine;
import guessmarket.engine.exception.EventAlreadyStartedException;
import guessmarket.engine.exception.EventNotActiveException;
import guessmarket.engine.exception.InsufficientFundsException;
import guessmarket.engine.exception.InsufficientSharesException;
import guessmarket.engine.exception.InvalidEventException;
import guessmarket.engine.exception.InvalidFileException;
import guessmarket.engine.exception.InvalidPriceException;
import guessmarket.engine.exception.InvalidQuantityException;
import guessmarket.engine.exception.NoSuchUserException;
import guessmarket.engine.exception.NotMarketMakerException;
import guessmarket.engine.exception.UserBlockedException;
import guessmarket.engine.impl.GuessMarketEngine;

import java.nio.file.Path;
import java.util.List;

import static guessmarket.tests.TestSupport.assertClose;
import static guessmarket.tests.TestSupport.assertEquals;
import static guessmarket.tests.TestSupport.assertFalse;
import static guessmarket.tests.TestSupport.assertKinds;
import static guessmarket.tests.TestSupport.assertThrows;
import static guessmarket.tests.TestSupport.assertTrue;

/**
 * The exercise 2 engine checks.
 * <p>
 * The order book figures are not taken from this implementation. They come from replaying the
 * lecturer's own clob_simulation.html through its ledger, so a failure here means the engine
 * disagrees with the simulator rather than with itself.
 */
public final class Ex2Tests {

    /** The commission rate the simulator uses. */
    private static final int SIMULATOR_EVENT = 1;

    private static final int YES = 1;
    private static final int NO = 2;

    private final TestSupport support = new TestSupport();
    private final Path ex2;
    private final Path local;

    private Ex2Tests(Path testFiles) {
        this.ex2 = testFiles.resolve("ex2");
        this.local = testFiles.resolve("local");
    }

    public static void main(String[] args) {
        Path testFiles = Path.of(args.length > 0 ? args[0] : "test-files");
        System.exit(new Ex2Tests(testFiles).run());
    }

    private int run() {
        loading();
        newValidations();
        marketMakerLifecycle();
        lmsrTrading();
        orderBookAgainstTheSimulator(false);
        orderBookAgainstTheSimulator(true);
        orderBookRules();
        userRules();
        creatingEvents();
        return support.report();
    }

    // ---------- loading ----------

    private void loading() {
        support.group("loading");

        support.check("a valid four event file loads", () -> {
            Engine engine = new GuessMarketEngine();
            assertEquals("events", 4, engine.loadFromFile(ex2.resolve("multiple.xml").toString()).eventsLoaded());
            assertEquals("users", 3, engine.listUsers().size());
        });

        support.check("both trading methods are recognised", () -> {
            Engine engine = loaded("multiple.xml");
            assertTrue("event 1 is lmsr", engine.listEvents().get(0).method() == MarketMethod.LMSR);
            assertTrue("event 2 is a book", engine.listEvents().get(1).method() == MarketMethod.ORDER_BOOK);
        });

        support.check("every event starts not started", () -> {
            Engine engine = loaded("multiple.xml");
            for (var summary : engine.listEvents()) {
                assertTrue(summary.name() + " is not started", summary.status() == EventLifecycle.NOT_STARTED);
                assertClose("account of " + summary.name(), 0.0d, summary.accountBalance());
            }
        });

        support.check("market makers are matched to their events", () -> {
            Engine engine = loaded("multiple.xml");
            assertEquals("event 1", "Tikva", engine.listEvents().get(0).marketMakerName());
            assertEquals("event 2", "Avrum", engine.listEvents().get(1).marketMakerName());
        });

        support.check("the name attribute arrives whole", () -> {
            Engine engine = loaded("multiple.xml");
            assertEquals("name", "Mujtaba is Dead", engine.listEvents().get(0).name());
        });

        support.check("surrounding spaces in a name are dropped", () -> {
            Engine engine = new GuessMarketEngine();
            engine.loadFromFile(local.resolve("spaced-name.xml").toString());
            assertEquals("name", "Mujtaba   is   Dead", engine.listEvents().get(0).name());
        });

        support.check("a second file replaces the first", () -> {
            Engine engine = loaded("multiple.xml");
            engine.loadFromFile(ex2.resolve("small.xml").toString());
            assertEquals("events", 2, engine.listEvents().size());
        });

        support.check("a rejected file leaves the loaded one alone", () -> {
            Engine engine = loaded("multiple.xml");
            assertThrows("bad file", InvalidFileException.class,
                    () -> engine.loadFromFile(ex2.resolve("error-2.xml").toString()));
            assertEquals("still loaded", 4, engine.listEvents().size());
        });

        support.check("a missing file names itself", () -> {
            Engine engine = new GuessMarketEngine();
            InvalidFileException thrown = assertThrows("missing", InvalidFileException.class,
                    () -> engine.loadFromFile(ex2.resolve("nope.xml").toString()));
            assertKinds("problems", thrown.problems(), ProblemKind.FILE_NOT_FOUND);
        });

        support.check("a file that is not xml is refused", () -> {
            Engine engine = new GuessMarketEngine();
            InvalidFileException thrown = assertThrows("txt", InvalidFileException.class,
                    () -> engine.loadFromFile(local.resolve("not-an-xml-file.txt").toString()));
            assertKinds("problems", thrown.problems(), ProblemKind.WRONG_EXTENSION);
        });
    }

    // ---------- the four checks exercise 2 adds ----------

    private void newValidations() {
        support.group("validation added in exercise 2");

        support.check("error-2.xml is refused for a user opening with nothing", () -> {
            InvalidFileException thrown = rejected(ex2.resolve("error-2.xml").toString());
            assertKinds("problems", thrown.problems(), ProblemKind.INITIAL_CASH_NOT_POSITIVE);
            assertEquals("the user", "Avrum", thrown.problems().get(0).value(0));
        });

        support.check("error-3.xml is refused for a market maker of a missing event", () -> {
            InvalidFileException thrown = rejected(ex2.resolve("error-3.xml").toString());
            assertKinds("problems", thrown.problems(),
                    ProblemKind.MARKET_MAKER_OF_UNKNOWN_EVENT, ProblemKind.EVENT_WITHOUT_MARKET_MAKER);
            assertEquals("the user", "Avrum", thrown.problems().get(0).value(0));
            assertEquals("the event", "12", thrown.problems().get(0).value(1));
        });

        support.check("two users may not share a name", () -> {
            InvalidFileException thrown = rejected(local.resolve("bad-duplicate-user.xml").toString());
            assertKinds("problems", thrown.problems(), ProblemKind.DUPLICATE_USER_NAME);
        });

        support.check("an event may not have two market makers", () -> {
            InvalidFileException thrown = rejected(local.resolve("bad-two-market-makers.xml").toString());
            assertKinds("problems", thrown.problems(), ProblemKind.EVENT_WITH_SEVERAL_MARKET_MAKERS);
        });

        support.check("an event must have one", () -> {
            InvalidFileException thrown = rejected(local.resolve("bad-no-market-maker.xml").toString());
            assertKinds("problems", thrown.problems(), ProblemKind.EVENT_WITHOUT_MARKET_MAKER);
        });

        support.check("initial cash of zero is refused", () -> {
            InvalidFileException thrown = rejected(local.resolve("bad-initial-cash-zero.xml").toString());
            assertKinds("problems", thrown.problems(), ProblemKind.INITIAL_CASH_NOT_POSITIVE);
        });

        support.group("validation carried over from exercise 1");

        support.check("b must be positive", () -> assertKinds("zero",
                rejected(local.resolve("bad-b-zero.xml").toString()).problems(),
                ProblemKind.LIQUIDITY_NOT_POSITIVE));

        support.check("b may not be negative", () -> assertKinds("negative",
                rejected(local.resolve("bad-b-negative.xml").toString()).problems(),
                ProblemKind.LIQUIDITY_NOT_POSITIVE));

        support.check("an event needs exactly two answers", () -> assertKinds("one",
                rejected(local.resolve("bad-one-answer.xml").toString()).problems(),
                ProblemKind.WRONG_ANSWER_COUNT));

        support.check("the two answers must differ", () -> assertKinds("same",
                rejected(local.resolve("bad-duplicate-answers.xml").toString()).problems(),
                ProblemKind.DUPLICATE_ANSWER));

        support.check("commission may not be negative", () -> assertKinds("negative",
                rejected(local.resolve("bad-commission-negative.xml").toString()).problems(),
                ProblemKind.COMMISSION_OUT_OF_RANGE));

        support.check("commission may not pass 90", () -> assertKinds("high",
                rejected(local.resolve("bad-commission-high.xml").toString()).problems(),
                ProblemKind.COMMISSION_OUT_OF_RANGE));

        support.check("an event needs a name", () -> assertKinds("blank",
                rejected(local.resolve("bad-blank-name.xml").toString()).problems(),
                ProblemKind.BLANK_EVENT_NAME));

        support.check("a file with no events is refused", () -> assertKinds("none",
                rejected(local.resolve("bad-no-events.xml").toString()).problems(),
                ProblemKind.NO_EVENTS));

        support.group("validation added for the order book");

        support.check("d must be positive", () -> assertKinds("zero",
                rejected(local.resolve("bad-d-zero.xml").toString()).problems(),
                ProblemKind.BASE_PRICE_NOT_POSITIVE));

        support.check("allow-mint must say true or false", () -> assertKinds("maybe",
                rejected(local.resolve("bad-mint-flag.xml").toString()).problems(),
                ProblemKind.UNKNOWN_MINT_FLAG));
    }

    // ---------- opening and closing ----------

    private void marketMakerLifecycle() {
        support.group("only the market maker runs his event");

        support.check("somebody else cannot open it", () -> {
            Engine engine = loaded("small.xml");
            assertThrows("not his", NotMarketMakerException.class, () -> engine.openEvent(1, "Menash"));
        });

        support.check("the market maker can", () -> {
            Engine engine = loaded("small.xml");
            EventState state = engine.openEvent(1, "Tikva");
            assertTrue("active", state.summary().status() == EventLifecycle.ACTIVE);
        });

        support.check("opening an lmsr event costs its subsidy", () -> {
            Engine engine = loaded("small.xml");
            engine.openEvent(1, "Tikva");
            double subsidy = 100 * Math.log(2);
            assertClose("account", subsidy, engine.eventState(1).summary().accountBalance());
            assertClose("balance", 10000 - subsidy, balanceOf(engine, "Tikva"));
        });

        support.check("opening a book buys the initial stock", () -> {
            Engine engine = loaded("small.xml");
            engine.openEvent(2, "Avrum");
            assertClose("account", 100.0d, engine.eventState(2).summary().accountBalance());
            assertClose("balance", 900.0d, balanceOf(engine, "Avrum"));
            assertEquals("yes held", 100L, holding(engine, 2, "Avrum", 0));
            assertEquals("no held", 100L, holding(engine, 2, "Avrum", 1));
        });

        support.check("an event cannot be opened twice", () -> {
            Engine engine = loaded("small.xml");
            engine.openEvent(1, "Tikva");
            assertThrows("again", EventAlreadyStartedException.class, () -> engine.openEvent(1, "Tikva"));
        });

        support.check("trading a not started event is refused", () -> {
            Engine engine = loaded("small.xml");
            assertThrows("not open", EventNotActiveException.class,
                    () -> engine.buyLmsrShares(1, "Menash", YES, 10));
        });

        support.check("a market maker who cannot fund his event cannot start it", () -> {
            Engine engine = new GuessMarketEngine();
            engine.loadFromFile(local.resolve("poor-market-maker.xml").toString());
            InsufficientFundsException thrown = assertThrows("too poor", InsufficientFundsException.class,
                    () -> engine.openEvent(1, "Pauper"));
            assertClose("needed", 1000.0d, thrown.required());
            assertClose("held", 10.0d, thrown.available());
            assertTrue("still not started",
                    engine.listEvents().get(0).status() == EventLifecycle.NOT_STARTED);
        });
    }

    // ---------- lmsr ----------

    private void lmsrTrading() {
        support.group("lmsr trading");

        support.check("a hundred shares at b=100 cost 62.01", () -> {
            Engine engine = loaded("small.xml");
            engine.openEvent(1, "Tikva");
            assertClose("quote", 62.01145069582775d, engine.quoteLmsrPurchase(1, YES, 100), 1e-9);
        });

        support.check("that purchase moves the price from 0.50 to 0.73", () -> {
            Engine engine = loaded("small.xml");
            engine.openEvent(1, "Tikva");
            assertClose("before", 0.5d, engine.eventState(1).lmsr().outcomes().get(0).price());
            engine.buyLmsrShares(1, "Menash", YES, 100);
            assertClose("after", 0.7310585786, engine.eventState(1).lmsr().outcomes().get(0).price(), 1e-9);
        });

        support.check("an on-purchase commission goes to the market maker", () -> {
            Engine engine = loaded("small.xml");
            engine.openEvent(1, "Tikva");
            double subsidy = 100 * Math.log(2);
            var result = engine.buyLmsrShares(1, "Menash", YES, 10);
            assertClose("commission", result.sharesCost() * 0.05d, result.commissionPaid());
            assertClose("buyer paid", 100 - result.totalPaid(), balanceOf(engine, "Menash"));
            assertClose("maker earned", 10000 - subsidy + result.commissionPaid(), balanceOf(engine, "Tikva"));
            assertClose("account holds only the shares cost",
                    subsidy + result.sharesCost(), engine.eventState(1).summary().accountBalance());
        });

        support.check("closing pays the winners and hands the leftover back", () -> {
            Engine engine = loaded("small.xml");
            engine.openEvent(1, "Tikva");
            var purchase = engine.buyLmsrShares(1, "Menash", YES, 10);
            double makerBefore = balanceOf(engine, "Tikva");
            double accountBefore = engine.eventState(1).summary().accountBalance();

            CloseResult close = engine.closeEvent(1, "Tikva", YES);
            assertClose("winner paid", 10.0d, close.netPaidToWinners());
            assertClose("menash", 100 - purchase.totalPaid() + 10.0d, balanceOf(engine, "Menash"));
            assertClose("leftover returned", accountBefore - 10.0d, close.subsidyReturned());
            assertClose("maker", makerBefore + accountBefore - 10.0d, balanceOf(engine, "Tikva"));
            assertClose("account emptied", 0.0d, engine.eventState(1).summary().accountBalance());
        });

        support.check("a closed event refuses further trade", () -> {
            Engine engine = loaded("small.xml");
            engine.openEvent(1, "Tikva");
            engine.closeEvent(1, "Tikva", YES);
            assertThrows("closed", EventNotActiveException.class,
                    () -> engine.buyLmsrShares(1, "Menash", YES, 1));
        });

        support.check("a quantity below one is refused", () -> {
            Engine engine = loaded("small.xml");
            engine.openEvent(1, "Tikva");
            assertThrows("zero", InvalidQuantityException.class,
                    () -> engine.buyLmsrShares(1, "Menash", YES, 0));
        });
    }

    // ---------- the simulator, replayed ----------

    private void orderBookAgainstTheSimulator(boolean onClose) {
        String mode = onClose ? "on close" : "on purchase";
        support.group("clob_simulation.html replayed, commission " + mode);

        Engine engine = new GuessMarketEngine();
        engine.loadFromFile(local.resolve(onClose ? "clob-on-close.xml" : "clob-on-purchase.xml").toString());
        engine.openEvent(SIMULATOR_EVENT, "Zoe");

        support.check("the maker's first mint creates a hundred pairs", () -> {
            assertClose("zoe", 400.0d, balanceOf(engine, "Zoe"));
            assertClose("pool", 100.0d, engine.eventState(SIMULATOR_EVENT).summary().accountBalance());
            assertEquals("yes", 100L, holding(engine, SIMULATOR_EVENT, "Zoe", 0));
            assertEquals("no", 100L, holding(engine, SIMULATOR_EVENT, "Zoe", 1));
        });

        order(engine, "Bob", YES, OrderSide.BUY, 20, 0.50);
        order(engine, "Carol", YES, OrderSide.BUY, 15, 0.48);
        order(engine, "Zoe", YES, OrderSide.SELL, 25, 0.58);
        order(engine, "Zoe", YES, OrderSide.SELL, 15, 0.65);

        support.check("a two sided book quotes a spread of 0.08 and a mid of 0.54", () -> {
            OutcomeBook book = book(engine, 0);
            assertClose("best bid", 0.50d, book.bestBid());
            assertClose("best ask", 0.58d, book.bestAsk());
            assertClose("spread", 0.08d, book.spread());
            assertClose("mid", 0.54d, book.mid());
            assertTrue("nothing has traded", book.lastTradePrice() == null);
        });

        OrderResult aliceBuys = order(engine, "Alice", YES, OrderSide.BUY, 25, 0.58);

        support.check("alice's order is filled as a resale, not a mint", () -> {
            assertEquals("fills", 1, aliceBuys.fills().size());
            assertTrue("a trade", aliceBuys.fills().get(0).kind() == FillKind.TRADE);
            assertEquals("seller", "Zoe", aliceBuys.fills().get(0).sellerName());
            assertEquals("filled", 25L, aliceBuys.quantityFilled());
            assertClose("pool untouched", 100.0d, engine.eventState(SIMULATOR_EVENT).summary().accountBalance());
        });

        support.check("using up the cheap level widens the spread to 0.15", () -> {
            OutcomeBook book = book(engine, 0);
            assertClose("best ask", 0.65d, book.bestAsk());
            assertClose("spread", 0.15d, book.spread());
            assertClose("last", 0.58d, book.lastTradePrice());
        });

        order(engine, "Zoe", NO, OrderSide.SELL, 50, 0.45);
        order(engine, "Bob", NO, OrderSide.BUY, 25, 0.45);

        support.check("a partly filled ask keeps its remainder", () -> {
            OutcomeBook book = book(engine, 1);
            assertEquals("asks", 1, book.asks().size());
            assertEquals("left", 25L, book.asks().get(0).quantity());
            assertClose("last", 0.45d, book.lastTradePrice());
        });

        OrderResult zoeSells = order(engine, "Zoe", YES, OrderSide.SELL, 30, 0.45);

        support.check("a sell walks the bids best first and takes their prices", () -> {
            assertEquals("fills", 2, zoeSells.fills().size());
            assertClose("first at the top bid", 0.50d, zoeSells.fills().get(0).price());
            assertEquals("twenty from bob", 20L, zoeSells.fills().get(0).quantity());
            assertClose("then the next level", 0.48d, zoeSells.fills().get(1).price());
            assertEquals("ten from carol", 10L, zoeSells.fills().get(1).quantity());
            assertClose("she collects more than her floor", 14.80d, zoeSells.cashReceived());
            assertClose("last is the final fill", 0.48d, book(engine, 0).lastTradePrice());
        });

        order(engine, "Carol", NO, OrderSide.BUY, 35, 0.42);

        support.check("a bid that crosses nothing and cannot mint just rests", () -> {
            assertEquals("no bids", 1, book(engine, 1).bids().size());
            assertClose("pool untouched", 100.0d, engine.eventState(SIMULATOR_EVENT).summary().accountBalance());
        });

        OrderResult aliceMints = order(engine, "Alice", YES, OrderSide.BUY, 40, 0.62);

        support.check("two opposite bids worth a dollar together mint new pairs", () -> {
            assertEquals("two legs", 2, aliceMints.fills().size());
            assertTrue("a mint", aliceMints.fills().get(0).kind() == FillKind.MINT);
            assertEquals("the smaller size", 35L, aliceMints.fills().get(0).quantity());
            assertTrue("nobody sold", aliceMints.fills().get(0).sellerName() == null);
        });

        support.check("the resting order keeps its price and the arriving one pays the rest", () -> {
            assertClose("alice pays the complement", 0.58d, aliceMints.fills().get(0).price());
            assertClose("carol keeps her own price", 0.42d, aliceMints.fills().get(1).price());
            assertClose("the pair cost a dollar", 100.0d + 35.0d,
                    engine.eventState(SIMULATOR_EVENT).summary().accountBalance());
        });

        support.check("the unmatched remainder rests at the price it was given", () -> {
            assertEquals("resting", 5L, aliceMints.quantityResting());
            assertClose("still 0.62", 0.62d, book(engine, 0).bids().get(0).price());
        });

        support.check("a mint stamps a last price on both books", () -> {
            assertClose("yes", 0.58d, book(engine, 0).lastTradePrice());
            assertClose("no", 0.42d, book(engine, 1).lastTradePrice());
        });

        support.check("a price at or above d is refused outright", () ->
                assertThrows("1.05", InvalidPriceException.class,
                        () -> engine.submitOrder(SIMULATOR_EVENT, "Bob", YES, OrderSide.BUY, 10, 1.05)));

        order(engine, "Bob", NO, OrderSide.SELL, 25, 0.15);

        support.check("an ask nobody bids against simply sits there", () -> {
            assertEquals("asks", 2, book(engine, 1).asks().size());
            assertClose("cheapest", 0.15d, book(engine, 1).bestAsk());
        });

        CloseResult close = engine.closeEvent(SIMULATOR_EVENT, "Zoe", YES);

        support.check("the pot exactly covers the winners", () -> {
            assertEquals("winning shares", 135L, close.winningShares());
            assertClose("gross", 135.0d, close.grossPayout());
            assertClose("account emptied", 0.0d, engine.eventState(SIMULATOR_EVENT).summary().accountBalance());
            assertClose("nothing left over", 0.0d, close.subsidyReturned());
        });

        support.check("every resting order is cancelled", () -> {
            EventState state = engine.eventState(SIMULATOR_EVENT);
            for (OutcomeBook book : state.orderBook().books()) {
                assertEquals("bids on " + book.outcomeName(), 0, book.bids().size());
                assertEquals("asks on " + book.outcomeName(), 0, book.asks().size());
            }
        });

        support.check("the final balances match the simulator", () -> {
            assertClose("Zoe", onClose ? 486.45d : 486.3055d, balanceOf(engine, "Zoe"), 1e-9);
            assertClose("Alice", onClose ? 224.60d : 224.852d, balanceOf(engine, "Alice"), 1e-9);
            assertClose("Bob", onClose ? 198.55d : 198.5375d, balanceOf(engine, "Bob"), 1e-9);
            assertClose("Carol", onClose ? 190.40d : 190.305d, balanceOf(engine, "Carol"), 1e-9);
        });

        support.check("the commission the maker took matches the simulator", () ->
                assertClose("fees", onClose ? 1.35d : 0.7555d,
                        engine.eventState(SIMULATOR_EVENT).orderBook().commissionCollected(), 1e-9));

        support.check("no money was created or destroyed", () ->
                assertClose("total", 1100.0d,
                        balanceOf(engine, "Zoe") + balanceOf(engine, "Alice")
                                + balanceOf(engine, "Bob") + balanceOf(engine, "Carol"), 1e-6));
    }

    // ---------- order book rules ----------

    private void orderBookRules() {
        support.group("order book rules");

        support.check("an event that forbids minting lets the orders rest instead", () -> {
            Engine engine = loaded("multiple.xml");
            engine.openEvent(3, "Tikva");
            engine.submitOrder(3, "Avrum", YES, OrderSide.BUY, 10, 0.60);
            OrderResult result = engine.submitOrder(3, "Menash", NO, OrderSide.BUY, 10, 0.60);
            assertEquals("no fills", 0, result.fills().size());
            assertEquals("rests", 10L, result.quantityResting());
            assertClose("pool untouched", 1000.0d, engine.eventState(3).summary().accountBalance());
        });

        support.check("nobody can sell shares he does not hold", () -> {
            Engine engine = loaded("small.xml");
            engine.openEvent(2, "Avrum");
            assertThrows("naked", InsufficientSharesException.class,
                    () -> engine.submitOrder(2, "Menash", YES, OrderSide.SELL, 1, 0.50));
        });

        support.check("shares already on offer cannot be offered twice", () -> {
            Engine engine = loaded("small.xml");
            engine.openEvent(2, "Avrum");
            engine.submitOrder(2, "Avrum", YES, OrderSide.SELL, 100, 0.90);
            assertThrows("again", InsufficientSharesException.class,
                    () -> engine.submitOrder(2, "Avrum", YES, OrderSide.SELL, 1, 0.90));
        });

        support.check("a price below one step is refused", () -> {
            Engine engine = loaded("small.xml");
            engine.openEvent(2, "Avrum");
            assertThrows("zero", InvalidPriceException.class,
                    () -> engine.submitOrder(2, "Menash", YES, OrderSide.BUY, 1, 0.0));
        });

        support.check("the top of the range is d minus one step", () -> {
            Engine engine = loaded("small.xml");
            engine.openEvent(2, "Avrum");
            OrderResult result = engine.submitOrder(2, "Menash", YES, OrderSide.BUY, 1, 0.99);
            assertEquals("rests", 1L, result.quantityResting());
        });

        support.check("a participant appears from his first order", () -> {
            Engine engine = loaded("small.xml");
            engine.openEvent(2, "Avrum");
            assertEquals("just the maker", 1, engine.eventState(2).participants().size());
            engine.submitOrder(2, "Menash", YES, OrderSide.BUY, 1, 0.10);
            assertEquals("and the bidder", 2, engine.eventState(2).participants().size());
        });
    }

    // ---------- users ----------

    private void userRules() {
        support.group("users and their money");

        support.check("an unknown user is refused", () -> {
            Engine engine = loaded("small.xml");
            assertThrows("nobody", NoSuchUserException.class, () -> engine.openEvent(1, "Nobody"));
        });

        support.check("overspending is allowed once and blocks the user", () -> {
            Engine engine = loaded("small.xml");
            engine.openEvent(1, "Tikva");
            // Menash holds 100, and a thousand shares of a b=100 event cost far more than that.
            engine.buyLmsrShares(1, "Menash", YES, 1000);
            assertTrue("now negative", balanceOf(engine, "Menash") < 0);
            assertTrue("and blocked", blocked(engine, "Menash"));
        });

        support.check("a blocked user is refused from then on", () -> {
            Engine engine = loaded("small.xml");
            engine.openEvent(1, "Tikva");
            engine.buyLmsrShares(1, "Menash", YES, 1000);
            assertThrows("blocked", UserBlockedException.class,
                    () -> engine.buyLmsrShares(1, "Menash", YES, 1));
        });

        support.check("blocking one user leaves the others alone", () -> {
            Engine engine = loaded("small.xml");
            engine.openEvent(1, "Tikva");
            engine.buyLmsrShares(1, "Menash", YES, 1000);
            assertFalse("tikva is fine", blocked(engine, "Tikva"));
            engine.buyLmsrShares(1, "Avrum", NO, 1);
        });

        support.check("a user's events list follows what he has done", () -> {
            Engine engine = loaded("small.xml");
            engine.openEvent(1, "Tikva");
            assertEquals("nothing yet", 0, engine.userDetails("Menash").participations().size());
            engine.buyLmsrShares(1, "Menash", YES, 5);
            assertEquals("one event", 1, engine.userDetails("Menash").participations().size());
            assertEquals("named", "Mujtaba is Dead",
                    engine.userDetails("Menash").participations().get(0).eventName());
        });

        support.check("his trade history reads newest first", () -> {
            Engine engine = loaded("small.xml");
            engine.openEvent(1, "Tikva");
            engine.buyLmsrShares(1, "Menash", YES, 5);
            engine.buyLmsrShares(1, "Menash", NO, 7);
            var trades = engine.userDetails("Menash").participations().get(0).lmsrTrades();
            assertEquals("two", 2, trades.size());
            assertEquals("newest first", 7L, trades.get(0).quantity());
        });
    }

    // ---------- bonus: creating an event ----------

    private void creatingEvents() {
        support.group("bonus: a user creates an event of his own");

        support.check("the creator becomes its market maker", () -> {
            Engine engine = loaded("small.xml");
            var state = engine.createEvent(NewEvent.lmsr("Menash", "Will it snow ?", "In Tel Aviv",
                    7, CommissionPolicy.ON_CLOSE, List.of("Yes", "No"), 50));
            assertEquals("maker", "Menash", state.summary().marketMakerName());
            assertTrue("not started", state.summary().status() == EventLifecycle.NOT_STARTED);
            assertEquals("empty account", 0L, (long) state.summary().accountBalance());
        });

        support.check("it takes a number no loaded event is using", () -> {
            Engine engine = loaded("multiple.xml");
            var state = engine.createEvent(NewEvent.lmsr("Menash", "Brand new", "",
                    0, CommissionPolicy.ON_CLOSE, List.of("Yes", "No"), 50));
            assertEquals("id", 5, state.summary().id());
            assertEquals("events", 5, engine.listEvents().size());
        });

        support.check("it then behaves like any other event", () -> {
            Engine engine = loaded("small.xml");
            int id = engine.createEvent(NewEvent.orderBook("Avrum", "Home made book", "",
                    10, CommissionPolicy.ON_PURCHASE, List.of("Yes", "No"), 1, true, 40)).summary().id();
            engine.openEvent(id, "Avrum");
            assertClose("initial stock", 40.0d, engine.eventState(id).summary().accountBalance());
            engine.submitOrder(id, "Menash", 1, OrderSide.BUY, 5, 0.30);
            assertEquals("a bid rests", 1,
                    engine.eventState(id).orderBook().books().get(0).bids().size());
        });

        support.check("somebody else cannot open it", () -> {
            Engine engine = loaded("small.xml");
            int id = engine.createEvent(NewEvent.lmsr("Menash", "His own", "",
                    5, CommissionPolicy.ON_CLOSE, List.of("Yes", "No"), 20)).summary().id();
            assertThrows("not his", NotMarketMakerException.class, () -> engine.openEvent(id, "Avrum"));
        });

        support.check("the same rules a file has to satisfy still apply", () -> {
            Engine engine = loaded("small.xml");
            InvalidEventException thrown = assertThrows("bad", InvalidEventException.class,
                    () -> engine.createEvent(NewEvent.lmsr("Menash", "  ", "",
                            95, CommissionPolicy.ON_CLOSE, List.of("Yes", "Yes"), 0)));
            assertKinds("problems", thrown.problems(),
                    ProblemKind.BLANK_EVENT_NAME, ProblemKind.COMMISSION_OUT_OF_RANGE,
                    ProblemKind.DUPLICATE_ANSWER, ProblemKind.LIQUIDITY_NOT_POSITIVE);
        });

        support.check("a name already in use is refused", () -> {
            Engine engine = loaded("small.xml");
            InvalidEventException thrown = assertThrows("taken", InvalidEventException.class,
                    () -> engine.createEvent(NewEvent.lmsr("Menash", "Mujtaba is Dead", "",
                            5, CommissionPolicy.ON_CLOSE, List.of("Yes", "No"), 20)));
            assertKinds("problems", thrown.problems(), ProblemKind.DUPLICATE_EVENT_NAME);
        });

        support.check("a blocked user cannot create one", () -> {
            Engine engine = loaded("small.xml");
            engine.openEvent(1, "Tikva");
            engine.buyLmsrShares(1, "Menash", YES, 1000);
            assertThrows("blocked", UserBlockedException.class,
                    () -> engine.createEvent(NewEvent.lmsr("Menash", "Too late", "",
                            5, CommissionPolicy.ON_CLOSE, List.of("Yes", "No"), 20)));
        });
    }

    // ---------- helpers ----------

    private Engine loaded(String fileName) {
        Engine engine = new GuessMarketEngine();
        engine.loadFromFile(ex2.resolve(fileName).toString());
        return engine;
    }

    private InvalidFileException rejected(String path) {
        Engine engine = new GuessMarketEngine();
        return assertThrows("rejected", InvalidFileException.class, () -> engine.loadFromFile(path));
    }

    private OrderResult order(Engine engine, String user, int outcome, OrderSide side, long quantity, double price) {
        return engine.submitOrder(SIMULATOR_EVENT, user, outcome, side, quantity, price);
    }

    private OutcomeBook book(Engine engine, int index) {
        return engine.eventState(SIMULATOR_EVENT).orderBook().books().get(index);
    }

    private long holding(Engine engine, int eventId, String userName, int outcomeIndex) {
        for (var participant : engine.eventState(eventId).participants()) {
            if (participant.userName().equals(userName)) {
                return participant.holdings().get(outcomeIndex).quantity();
            }
        }
        return 0L;
    }

    private double balanceOf(Engine engine, String name) {
        return engine.userDetails(name).balance();
    }

    private boolean blocked(Engine engine, String name) {
        for (UserSummary summary : engine.listUsers()) {
            if (summary.name().equals(name)) {
                return summary.blocked();
            }
        }
        throw new AssertionError("no user " + name);
    }
}
