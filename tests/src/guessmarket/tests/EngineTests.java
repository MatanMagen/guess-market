package guessmarket.tests;

import guessmarket.dto.AccountEntry;
import guessmarket.dto.CloseResult;
import guessmarket.dto.CommissionPolicy;
import guessmarket.dto.EntryKind;
import guessmarket.dto.EventLifecycle;
import guessmarket.dto.EventState;
import guessmarket.dto.FillKind;
import guessmarket.dto.LoadSummary;
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
import guessmarket.engine.exception.InvalidAmountException;
import guessmarket.engine.exception.InvalidEventException;
import guessmarket.engine.exception.InvalidFileException;
import guessmarket.engine.exception.InvalidPriceException;
import guessmarket.engine.exception.InvalidQuantityException;
import guessmarket.engine.exception.InvalidUserNameException;
import guessmarket.engine.exception.NoSuchUserException;
import guessmarket.engine.exception.NotMarketMakerException;
import guessmarket.engine.exception.UserAlreadyExistsException;
import guessmarket.engine.exception.UserBlockedException;
import guessmarket.engine.impl.GuessMarketEngine;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static guessmarket.tests.TestSupport.assertClose;
import static guessmarket.tests.TestSupport.assertEquals;
import static guessmarket.tests.TestSupport.assertFalse;
import static guessmarket.tests.TestSupport.assertKinds;
import static guessmarket.tests.TestSupport.assertThrows;
import static guessmarket.tests.TestSupport.assertTrue;

/**
 * The engine checks, as of exercise 3.
 * <p>
 * The LMSR figures come from the exercise's own appendix, and the order book figures from replaying
 * the lecturer's clob_simulation.html through its ledger, so a failure here means the engine
 * disagrees with the exercise rather than with itself.
 */
public final class EngineTests {

    /** The simulator scenario is the only event in its file, so it is always event 1. */
    private static final int SIMULATOR_EVENT = 1;

    // The standard market: Tikva uploads small.xml, then Avrum uploads multiple.xml.
    private static final int MUJTABA = 1;
    private static final int EARTH_QUAKE = 2;
    private static final int WORLD_CUP = 3;
    private static final int RAIN = 4;

    private static final int YES = 1;
    private static final int NO = 2;

    private final TestSupport support = new TestSupport();
    private final Path ex3;
    private final Path local;

    private EngineTests(Path testFiles) {
        this.ex3 = testFiles.resolve("ex3");
        this.local = testFiles.resolve("local");
    }

    public static void main(String[] args) {
        Path testFiles = Path.of(args.length > 0 ? args[0] : "test-files");
        System.exit(new EngineTests(testFiles).run());
    }

    private int run() {
        users();
        uploading();
        validation();
        marketMakerLifecycle();
        lmsrTrading();
        orderBookAgainstTheSimulator(false);
        orderBookAgainstTheSimulator(true);
        orderBookRules();
        accounts();
        moreThanTwoAnswers();
        creatingEvents();
        manyUsersAtOnce();
        return support.report();
    }

    // ---------- users ----------

    private void users() {
        support.group("logging in");

        support.check("a new user starts with an empty account", () -> {
            Engine engine = new GuessMarketEngine();
            engine.registerUser("Dana");
            assertClose("balance", 0.0d, engine.userDetails("Dana").balance());
            assertEquals("no lines", 0, engine.userDetails("Dana").ledger().size());
        });

        support.check("a name that is taken is refused", () -> {
            Engine engine = new GuessMarketEngine();
            engine.registerUser("Dana");
            assertThrows("again", UserAlreadyExistsException.class, () -> engine.registerUser("Dana"));
            assertThrows("with spaces", UserAlreadyExistsException.class, () -> engine.registerUser("  Dana "));
            assertEquals("still one", 1, engine.listUsers().size());
        });

        support.check("names are case sensitive", () -> {
            Engine engine = new GuessMarketEngine();
            engine.registerUser("Dana");
            engine.registerUser("dana");
            assertEquals("two users", 2, engine.listUsers().size());
        });

        support.check("a blank name is refused", () -> {
            Engine engine = new GuessMarketEngine();
            assertThrows("blank", InvalidUserNameException.class, () -> engine.registerUser("   "));
        });

        support.check("users are listed in the order they joined", () -> {
            Engine engine = market();
            assertEquals("first", "Tikva", engine.listUsers().get(0).name());
            assertEquals("third", "Menash", engine.listUsers().get(2).name());
        });
    }

    // ---------- uploading ----------

    private void uploading() {
        support.group("uploading files");

        support.check("uploads accumulate instead of replacing each other", () -> {
            Engine engine = market();
            assertEquals("events", 4, engine.listEvents().size());
        });

        support.check("the uploader becomes market maker of every event in his file", () -> {
            Engine engine = market();
            assertEquals("small.xml", "Tikva", engine.listEvents().get(0).marketMakerName());
            for (int i = 1; i < 4; i++) {
                assertEquals("multiple.xml #" + i, "Avrum", engine.listEvents().get(i).marketMakerName());
            }
        });

        support.check("the summary names what was added", () -> {
            Engine engine = withUsers("Tikva:0");
            LoadSummary summary = upload(engine, ex3.resolve("multiple.xml"), "Tikva");
            assertEquals("events", 3, summary.eventNames().size());
            assertEquals("first", "Earth Quake on Dead Sea", summary.eventNames().get(0));
            assertEquals("maker", "Tikva", summary.marketMakerName());
        });

        support.check("both trading methods are recognised", () -> {
            Engine engine = market();
            assertTrue("mujtaba is lmsr", engine.eventState(MUJTABA).summary().method() == MarketMethod.LMSR);
            assertTrue("world cup is a book", engine.eventState(WORLD_CUP).summary().method() == MarketMethod.ORDER_BOOK);
        });

        support.check("every event starts not started with an empty account", () -> {
            Engine engine = market();
            for (var summary : engine.listEvents()) {
                assertTrue(summary.name() + " is not started", summary.status() == EventLifecycle.NOT_STARTED);
                assertClose("account of " + summary.name(), 0.0d, summary.accountBalance());
            }
        });

        support.check("uploading marks the uploader as a market maker", () -> {
            Engine engine = market();
            assertTrue("tikva", summaryOf(engine, "Tikva").marketMaker());
            assertFalse("menash", summaryOf(engine, "Menash").marketMaker());
        });

        support.check("surrounding spaces in a name are dropped", () -> {
            Engine engine = withUsers("Tikva:0");
            upload(engine, local.resolve("spaced-name.xml"), "Tikva");
            assertEquals("name", "Mujtaba   is   Dead", engine.listEvents().get(0).name());
        });

        support.check("an event name already in the system is refused", () -> {
            Engine engine = market();
            InvalidFileException thrown = assertThrows("again", InvalidFileException.class,
                    () -> upload(engine, ex3.resolve("small.xml"), "Menash"));
            assertKinds("problems", thrown.problems(), ProblemKind.DUPLICATE_EVENT_NAME);
            assertEquals("the name", "Mujtaba is Dead", thrown.problems().get(0).value(0));
        });

        support.check("two events of one file may not share a name", () -> {
            InvalidFileException thrown = rejected(local.resolve("bad-duplicate-name-in-file.xml"));
            assertKinds("problems", thrown.problems(), ProblemKind.DUPLICATE_EVENT_NAME_IN_FILE);
        });

        support.check("a rejected file adds nothing, not even its sound events", () -> {
            Engine engine = withUsers("Tikva:0");
            upload(engine, ex3.resolve("small.xml"), "Tikva");
            // Two of these three are new, but one repeats a name, so none of them may enter.
            String file = read(ex3.resolve("multiple.xml")).replace("World Cap Winner", "Mujtaba is Dead");
            assertThrows("rejected", InvalidFileException.class,
                    () -> engine.uploadEvents(stream(file), "multiple.xml", "Tikva"));
            assertEquals("still one", 1, engine.listEvents().size());
        });

        support.check("a file that is not xml is refused", () -> {
            InvalidFileException thrown = rejected(local.resolve("not-an-xml-file.txt"));
            assertKinds("problems", thrown.problems(), ProblemKind.WRONG_EXTENSION);
        });

        support.check("content that is not xml is refused, not thrown", () -> {
            Engine engine = withUsers("Tikva:0");
            InvalidFileException thrown = assertThrows("garbage", InvalidFileException.class,
                    () -> engine.uploadEvents(stream("this is not xml"), "garbage.xml", "Tikva"));
            assertKinds("problems", thrown.problems(), ProblemKind.UNREADABLE_XML);
        });

        support.check("only a logged in user can upload", () -> {
            Engine engine = new GuessMarketEngine();
            assertThrows("nobody", NoSuchUserException.class,
                    () -> upload(engine, ex3.resolve("small.xml"), "Nobody"));
        });
    }

    private void validation() {
        support.group("validation carried over from exercise 1");

        support.check("b must be positive", () -> assertKinds("zero",
                rejected(local.resolve("bad-b-zero.xml")).problems(), ProblemKind.LIQUIDITY_NOT_POSITIVE));

        support.check("b may not be negative", () -> assertKinds("negative",
                rejected(local.resolve("bad-b-negative.xml")).problems(), ProblemKind.LIQUIDITY_NOT_POSITIVE));

        support.check("an event needs at least two answers", () -> assertKinds("one",
                rejected(local.resolve("bad-one-answer.xml")).problems(), ProblemKind.WRONG_ANSWER_COUNT));

        support.check("the answers must differ", () -> assertKinds("same",
                rejected(local.resolve("bad-duplicate-answers.xml")).problems(), ProblemKind.DUPLICATE_ANSWER));

        support.check("commission may not be negative", () -> assertKinds("negative",
                rejected(local.resolve("bad-commission-negative.xml")).problems(),
                ProblemKind.COMMISSION_OUT_OF_RANGE));

        support.check("commission may not pass 90", () -> assertKinds("high",
                rejected(local.resolve("bad-commission-high.xml")).problems(), ProblemKind.COMMISSION_OUT_OF_RANGE));

        support.check("an event needs a name", () -> assertKinds("blank",
                rejected(local.resolve("bad-blank-name.xml")).problems(), ProblemKind.BLANK_EVENT_NAME));

        support.check("a file with no events is refused", () -> assertKinds("none",
                rejected(local.resolve("bad-no-events.xml")).problems(), ProblemKind.NO_EVENTS));

        support.check("d must be positive", () -> assertKinds("zero",
                rejected(local.resolve("bad-d-zero.xml")).problems(), ProblemKind.BASE_PRICE_NOT_POSITIVE));

        support.check("allow-mint must say true or false", () -> assertKinds("maybe",
                rejected(local.resolve("bad-mint-flag.xml")).problems(), ProblemKind.UNKNOWN_MINT_FLAG));
    }

    // ---------- opening and closing ----------

    private void marketMakerLifecycle() {
        support.group("only the market maker runs his event");

        support.check("somebody else cannot open it", () -> {
            Engine engine = market();
            assertThrows("not his", NotMarketMakerException.class, () -> engine.openEvent(MUJTABA, "Menash"));
        });

        support.check("the market maker can", () -> {
            Engine engine = market();
            EventState state = engine.openEvent(MUJTABA, "Tikva");
            assertTrue("active", state.summary().status() == EventLifecycle.ACTIVE);
        });

        support.check("opening an lmsr event costs its subsidy", () -> {
            Engine engine = market();
            engine.openEvent(MUJTABA, "Tikva");
            double subsidy = 100 * Math.log(2);
            assertClose("account", subsidy, engine.eventState(MUJTABA).summary().accountBalance());
            assertClose("balance", 10000 - subsidy, balanceOf(engine, "Tikva"));
        });

        support.check("opening a book buys the initial stock", () -> {
            Engine engine = market();
            engine.openEvent(WORLD_CUP, "Avrum");
            assertClose("account", 100.0d, engine.eventState(WORLD_CUP).summary().accountBalance());
            assertClose("balance", 900.0d, balanceOf(engine, "Avrum"));
            assertEquals("argentina held", 100L, holding(engine, WORLD_CUP, "Avrum", 0));
            assertEquals("spain held", 100L, holding(engine, WORLD_CUP, "Avrum", 1));
        });

        support.check("an event cannot be opened twice", () -> {
            Engine engine = market();
            engine.openEvent(MUJTABA, "Tikva");
            assertThrows("again", EventAlreadyStartedException.class, () -> engine.openEvent(MUJTABA, "Tikva"));
        });

        support.check("trading a not started event is refused", () -> {
            Engine engine = market();
            assertThrows("not open", EventNotActiveException.class,
                    () -> engine.buyLmsrShares(MUJTABA, "Menash", YES, 10));
        });

        support.check("a market maker who cannot fund his event cannot start it", () -> {
            Engine engine = withUsers("Pauper:10");
            upload(engine, local.resolve("poor-market-maker.xml"), "Pauper");
            InsufficientFundsException thrown = assertThrows("too poor", InsufficientFundsException.class,
                    () -> engine.openEvent(1, "Pauper"));
            assertClose("needed", 1000.0d, thrown.required());
            assertClose("held", 10.0d, thrown.available());
            assertTrue("still not started", engine.listEvents().get(0).status() == EventLifecycle.NOT_STARTED);
        });

        support.check("after loading funds he can", () -> {
            Engine engine = withUsers("Pauper:10");
            upload(engine, local.resolve("poor-market-maker.xml"), "Pauper");
            engine.depositFunds("Pauper", 990);
            engine.openEvent(1, "Pauper");
            assertClose("all of it went in", 0.0d, balanceOf(engine, "Pauper"));
        });
    }

    // ---------- lmsr ----------

    private void lmsrTrading() {
        support.group("lmsr trading");

        support.check("a hundred shares at b=100 cost 62.01", () -> {
            Engine engine = market();
            engine.openEvent(MUJTABA, "Tikva");
            assertClose("quote", 62.01145069582775d, engine.quoteLmsrPurchase(MUJTABA, YES, 100), 1e-9);
        });

        support.check("that purchase moves the price from 0.50 to 0.73", () -> {
            Engine engine = market();
            engine.openEvent(MUJTABA, "Tikva");
            assertClose("before", 0.5d, engine.eventState(MUJTABA).lmsr().outcomes().get(0).price());
            engine.buyLmsrShares(MUJTABA, "Menash", YES, 100);
            assertClose("after", 0.7310585786, engine.eventState(MUJTABA).lmsr().outcomes().get(0).price(), 1e-9);
        });

        support.check("an on-purchase commission goes to the market maker", () -> {
            Engine engine = market();
            engine.openEvent(MUJTABA, "Tikva");
            double subsidy = 100 * Math.log(2);
            var result = engine.buyLmsrShares(MUJTABA, "Menash", YES, 10);
            assertClose("commission", result.sharesCost() * 0.05d, result.commissionPaid());
            assertClose("buyer paid", 100 - result.totalPaid(), balanceOf(engine, "Menash"));
            assertClose("maker earned", 10000 - subsidy + result.commissionPaid(), balanceOf(engine, "Tikva"));
            assertClose("account holds only the shares cost",
                    subsidy + result.sharesCost(), engine.eventState(MUJTABA).summary().accountBalance());
        });

        support.check("closing pays the winners and hands the leftover back", () -> {
            Engine engine = market();
            engine.openEvent(MUJTABA, "Tikva");
            var purchase = engine.buyLmsrShares(MUJTABA, "Menash", YES, 10);
            double makerBefore = balanceOf(engine, "Tikva");
            double accountBefore = engine.eventState(MUJTABA).summary().accountBalance();

            CloseResult close = engine.closeEvent(MUJTABA, "Tikva", YES);
            assertClose("winner paid", 10.0d, close.netPaidToWinners());
            assertClose("menash", 100 - purchase.totalPaid() + 10.0d, balanceOf(engine, "Menash"));
            assertClose("leftover returned", accountBefore - 10.0d, close.subsidyReturned());
            assertClose("maker", makerBefore + accountBefore - 10.0d, balanceOf(engine, "Tikva"));
            assertClose("account emptied", 0.0d, engine.eventState(MUJTABA).summary().accountBalance());
        });

        support.check("an on-close commission comes off the winnings", () -> {
            Engine engine = market();
            engine.openEvent(RAIN, "Avrum");
            engine.buyLmsrShares(RAIN, "Menash", YES, 20);
            double before = balanceOf(engine, "Menash");
            CloseResult close = engine.closeEvent(RAIN, "Avrum", YES);
            assertClose("10% of 20", 2.0d, close.commissionCharged());
            assertClose("menash", before + 18.0d, balanceOf(engine, "Menash"));
        });

        support.check("a closed event refuses further trade", () -> {
            Engine engine = market();
            engine.openEvent(MUJTABA, "Tikva");
            engine.closeEvent(MUJTABA, "Tikva", YES);
            assertThrows("closed", EventNotActiveException.class,
                    () -> engine.buyLmsrShares(MUJTABA, "Menash", YES, 1));
        });

        support.check("a quantity below one is refused", () -> {
            Engine engine = market();
            engine.openEvent(MUJTABA, "Tikva");
            assertThrows("zero", InvalidQuantityException.class,
                    () -> engine.buyLmsrShares(MUJTABA, "Menash", YES, 0));
        });
    }

    // ---------- the simulator, replayed ----------

    private void orderBookAgainstTheSimulator(boolean onClose) {
        String mode = onClose ? "on close" : "on purchase";
        support.group("clob_simulation.html replayed, commission " + mode);

        Engine engine = withUsers("Zoe:500", "Alice:200", "Bob:200", "Carol:200");
        upload(engine, local.resolve(onClose ? "clob-on-close.xml" : "clob-on-purchase.xml"), "Zoe");
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

        support.check("every user's account lines add up to his balance", () -> {
            for (String name : List.of("Zoe", "Alice", "Bob", "Carol")) {
                double sum = 0.0d;
                for (AccountEntry entry : engine.userDetails(name).ledger()) {
                    sum += entry.amount();
                }
                assertClose(name, balanceOf(engine, name), sum, 1e-9);
            }
        });
    }

    // ---------- order book rules ----------

    private void orderBookRules() {
        support.group("order book rules");

        support.check("an event that forbids minting lets the orders rest instead", () -> {
            Engine engine = market();
            engine.openEvent(EARTH_QUAKE, "Avrum");
            engine.submitOrder(EARTH_QUAKE, "Tikva", YES, OrderSide.BUY, 10, 0.60);
            OrderResult result = engine.submitOrder(EARTH_QUAKE, "Menash", NO, OrderSide.BUY, 10, 0.60);
            assertEquals("no fills", 0, result.fills().size());
            assertEquals("rests", 10L, result.quantityResting());
            assertClose("pool untouched", 1000.0d, engine.eventState(EARTH_QUAKE).summary().accountBalance());
        });

        support.check("nobody can sell shares he does not hold", () -> {
            Engine engine = market();
            engine.openEvent(WORLD_CUP, "Avrum");
            assertThrows("naked", InsufficientSharesException.class,
                    () -> engine.submitOrder(WORLD_CUP, "Menash", YES, OrderSide.SELL, 1, 0.50));
        });

        support.check("shares already on offer cannot be offered twice", () -> {
            Engine engine = market();
            engine.openEvent(WORLD_CUP, "Avrum");
            engine.submitOrder(WORLD_CUP, "Avrum", YES, OrderSide.SELL, 100, 0.90);
            assertThrows("again", InsufficientSharesException.class,
                    () -> engine.submitOrder(WORLD_CUP, "Avrum", YES, OrderSide.SELL, 1, 0.90));
        });

        support.check("a price below one step is refused", () -> {
            Engine engine = market();
            engine.openEvent(WORLD_CUP, "Avrum");
            assertThrows("zero", InvalidPriceException.class,
                    () -> engine.submitOrder(WORLD_CUP, "Menash", YES, OrderSide.BUY, 1, 0.0));
        });

        support.check("the top of the range is d minus one step", () -> {
            Engine engine = market();
            engine.openEvent(WORLD_CUP, "Avrum");
            OrderResult result = engine.submitOrder(WORLD_CUP, "Menash", YES, OrderSide.BUY, 1, 0.99);
            assertEquals("rests", 1L, result.quantityResting());
        });

        support.check("a participant appears from his first order", () -> {
            Engine engine = market();
            engine.openEvent(WORLD_CUP, "Avrum");
            assertEquals("just the maker", 1, engine.eventState(WORLD_CUP).participants().size());
            engine.submitOrder(WORLD_CUP, "Menash", YES, OrderSide.BUY, 1, 0.10);
            assertEquals("and the bidder", 2, engine.eventState(WORLD_CUP).participants().size());
        });
    }

    // ---------- accounts ----------

    private void accounts() {
        support.group("accounts and their lines");

        support.check("loading money adds a deposit line", () -> {
            Engine engine = withUsers("Dana:0");
            var details = engine.depositFunds("Dana", 250);
            assertClose("balance", 250.0d, details.balance());
            assertEquals("one line", 1, details.ledger().size());
            assertTrue("a deposit", details.ledger().get(0).kind() == EntryKind.DEPOSIT);
            assertClose("after", 250.0d, details.ledger().get(0).balanceAfter());
        });

        support.check("a deposit must be positive", () -> {
            Engine engine = withUsers("Dana:0");
            assertThrows("zero", InvalidAmountException.class, () -> engine.depositFunds("Dana", 0));
            assertThrows("negative", InvalidAmountException.class, () -> engine.depositFunds("Dana", -5));
            assertThrows("not a number", InvalidAmountException.class, () -> engine.depositFunds("Dana", Double.NaN));
        });

        support.check("a purchase writes its cost and its commission on separate lines", () -> {
            Engine engine = market();
            engine.openEvent(MUJTABA, "Tikva");
            var result = engine.buyLmsrShares(MUJTABA, "Menash", YES, 10);
            List<AccountEntry> lines = engine.userDetails("Menash").ledger();
            AccountEntry shares = lines.get(lines.size() - 2);
            AccountEntry commission = lines.get(lines.size() - 1);
            assertTrue("shares", shares.kind() == EntryKind.SHARES_BOUGHT);
            assertClose("cost", -result.sharesCost(), shares.amount());
            assertEquals("event", "Mujtaba is Dead", shares.eventName());
            assertTrue("commission", commission.kind() == EntryKind.COMMISSION_PAID);
            assertClose("fee", -result.commissionPaid(), commission.amount());
        });

        support.check("the market maker sees the commission arrive", () -> {
            Engine engine = market();
            engine.openEvent(MUJTABA, "Tikva");
            var result = engine.buyLmsrShares(MUJTABA, "Menash", YES, 10);
            List<AccountEntry> lines = engine.userDetails("Tikva").ledger();
            AccountEntry last = lines.get(lines.size() - 1);
            assertTrue("received", last.kind() == EntryKind.COMMISSION_RECEIVED);
            assertClose("fee", result.commissionPaid(), last.amount());
        });

        support.check("opening an event is a line of its own", () -> {
            Engine engine = market();
            engine.openEvent(WORLD_CUP, "Avrum");
            List<AccountEntry> lines = engine.userDetails("Avrum").ledger();
            assertTrue("initial stock", lines.get(lines.size() - 1).kind() == EntryKind.INITIAL_STOCK);
            assertClose("cost", -100.0d, lines.get(lines.size() - 1).amount());
        });

        support.check("overspending is allowed once and blocks the user", () -> {
            Engine engine = market();
            engine.openEvent(MUJTABA, "Tikva");
            // Menash holds 100, and a thousand shares of a b=100 event cost far more than that.
            engine.buyLmsrShares(MUJTABA, "Menash", YES, 1000);
            assertTrue("now negative", balanceOf(engine, "Menash") < 0);
            assertTrue("and blocked", summaryOf(engine, "Menash").blocked());
            assertThrows("refused", UserBlockedException.class,
                    () -> engine.buyLmsrShares(MUJTABA, "Menash", YES, 1));
        });

        support.check("loading enough money unblocks him", () -> {
            Engine engine = market();
            engine.openEvent(MUJTABA, "Tikva");
            engine.buyLmsrShares(MUJTABA, "Menash", YES, 1000);
            double debt = -balanceOf(engine, "Menash");
            engine.depositFunds("Menash", debt / 2);
            assertTrue("still blocked", summaryOf(engine, "Menash").blocked());
            engine.depositFunds("Menash", debt);
            assertFalse("free again", summaryOf(engine, "Menash").blocked());
            engine.buyLmsrShares(MUJTABA, "Menash", YES, 1);
        });

        support.check("a user's events are the ones he runs and the ones he trades", () -> {
            Engine engine = market();
            assertEquals("menash, nothing yet", 0, engine.userDetails("Menash").participations().size());
            assertEquals("tikva runs one", 1, engine.userDetails("Tikva").participations().size());
            engine.openEvent(MUJTABA, "Tikva");
            engine.buyLmsrShares(MUJTABA, "Menash", YES, 5);
            assertEquals("menash, one", 1, engine.userDetails("Menash").participations().size());
            assertEquals("named", "Mujtaba is Dead", engine.userDetails("Menash").participations().get(0).eventName());
        });

        support.check("his trade history reads newest first", () -> {
            Engine engine = market();
            engine.openEvent(MUJTABA, "Tikva");
            engine.buyLmsrShares(MUJTABA, "Menash", YES, 5);
            engine.buyLmsrShares(MUJTABA, "Menash", NO, 7);
            var trades = engine.userDetails("Menash").participations().get(0).lmsrTrades();
            assertEquals("two", 2, trades.size());
            assertEquals("newest first", 7L, trades.get(0).quantity());
        });
    }

    // ---------- more than two answers ----------

    private void moreThanTwoAnswers() {
        support.group("events with more than two answers");

        support.check("an lmsr event opens at an even price on every answer", () -> {
            Engine engine = withUsers("Maker:1000", "Dana:100");
            upload(engine, local.resolve("three-answers.xml"), "Maker");
            engine.openEvent(1, "Maker");
            for (var outcome : engine.eventState(1).lmsr().outcomes()) {
                assertClose(outcome.name(), 1.0d / 3.0d, outcome.price());
            }
            assertClose("subsidy is b ln 3", 100 * Math.log(3), engine.eventState(1).summary().accountBalance());
        });

        support.check("buying one answer lowers the other two equally", () -> {
            Engine engine = withUsers("Maker:1000", "Dana:100");
            upload(engine, local.resolve("three-answers.xml"), "Maker");
            engine.openEvent(1, "Maker");
            engine.buyLmsrShares(1, "Dana", 1, 50);
            var outcomes = engine.eventState(1).lmsr().outcomes();
            assertClose("e^0.5 / (e^0.5 + 2)", Math.exp(0.5) / (Math.exp(0.5) + 2), outcomes.get(0).price());
            assertClose("even", outcomes.get(1).price(), outcomes.get(2).price());
            assertClose("sums to one", 1.0d,
                    outcomes.get(0).price() + outcomes.get(1).price() + outcomes.get(2).price());
        });

        support.check("a book mints a set once every answer has a buyer", () -> {
            Engine engine = withUsers("Maker:0", "A:10", "B:10", "C:10");
            upload(engine, local.resolve("three-answers.xml"), "Maker");
            engine.openEvent(2, "Maker");
            engine.submitOrder(2, "A", 1, OrderSide.BUY, 5, 0.30);
            OrderResult second = engine.submitOrder(2, "B", 2, OrderSide.BUY, 5, 0.30);
            assertEquals("two answers are not enough", 0, second.fills().size());
            OrderResult third = engine.submitOrder(2, "C", 3, OrderSide.BUY, 5, 0.45);
            assertEquals("three legs", 3, third.fills().size());
            assertClose("the arriving order pays what is left of d", 0.40d, third.fills().get(0).price());
            assertClose("five whole sets", 5.0d, engine.eventState(2).summary().accountBalance());
            assertClose("c paid 2.00", 8.0d, balanceOf(engine, "C"));
        });
    }

    // ---------- the exercise 2 bonus, still in the engine ----------

    private void creatingEvents() {
        support.group("a user creates an event of his own");

        support.check("the creator becomes its market maker", () -> {
            Engine engine = market();
            var state = engine.createEvent(NewEvent.lmsr("Menash", "Will it snow ?", "In Tel Aviv",
                    7, CommissionPolicy.ON_CLOSE, List.of("Yes", "No"), 50));
            assertEquals("maker", "Menash", state.summary().marketMakerName());
            assertEquals("next number", 5, state.summary().id());
            assertTrue("not started", state.summary().status() == EventLifecycle.NOT_STARTED);
        });

        support.check("the same rules a file has to satisfy still apply", () -> {
            Engine engine = market();
            InvalidEventException thrown = assertThrows("bad", InvalidEventException.class,
                    () -> engine.createEvent(NewEvent.lmsr("Menash", "  ", "",
                            95, CommissionPolicy.ON_CLOSE, List.of("Yes", "Yes"), 0)));
            assertKinds("problems", thrown.problems(),
                    ProblemKind.BLANK_EVENT_NAME, ProblemKind.COMMISSION_OUT_OF_RANGE,
                    ProblemKind.DUPLICATE_ANSWER, ProblemKind.LIQUIDITY_NOT_POSITIVE);
        });

        support.check("a name already in use is refused", () -> {
            Engine engine = market();
            InvalidEventException thrown = assertThrows("taken", InvalidEventException.class,
                    () -> engine.createEvent(NewEvent.lmsr("Menash", "Mujtaba is Dead", "",
                            5, CommissionPolicy.ON_CLOSE, List.of("Yes", "No"), 20)));
            assertKinds("problems", thrown.problems(), ProblemKind.DUPLICATE_EVENT_NAME);
        });
    }

    // ---------- concurrency ----------

    private void manyUsersAtOnce() {
        support.group("many users at once");

        support.check("simultaneous purchases neither lose nor invent shares or money", () -> {
            Engine engine = withUsers("Maker:1000");
            upload(engine, ex3.resolve("small.xml"), "Maker");
            engine.openEvent(1, "Maker");
            int threads = 8;
            int buysEach = 50;
            for (int t = 0; t < threads; t++) {
                engine.registerUser("T" + t);
                engine.depositFunds("T" + t, 1000);
            }
            List<Thread> workers = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                String name = "T" + t;
                int outcome = t % 2 + 1;
                workers.add(Thread.ofPlatform().start(() -> {
                    for (int i = 0; i < buysEach; i++) {
                        engine.buyLmsrShares(1, name, outcome, 1);
                    }
                }));
            }
            for (Thread worker : workers) {
                worker.join();
            }
            var outcomes = engine.eventState(1).lmsr().outcomes();
            assertEquals("every share counted", (long) threads * buysEach,
                    outcomes.get(0).sharesBought() + outcomes.get(1).sharesBought());
            double total = 0.0d;
            for (UserSummary user : engine.listUsers()) {
                total += user.balance();
            }
            total += engine.eventState(1).summary().accountBalance();
            assertClose("money is conserved", 1000.0d + threads * 1000.0d, total, 1e-6);
        });
    }

    // ---------- helpers ----------

    /** Tikva, Avrum and Menash with Tikva's small.xml and Avrum's multiple.xml uploaded, in that order. */
    private Engine market() {
        Engine engine = withUsers("Tikva:10000", "Avrum:1000", "Menash:100");
        upload(engine, ex3.resolve("small.xml"), "Tikva");
        upload(engine, ex3.resolve("multiple.xml"), "Avrum");
        return engine;
    }

    /** Each argument is name:cash. */
    private Engine withUsers(String... users) {
        Engine engine = new GuessMarketEngine();
        for (String user : users) {
            String[] parts = user.split(":");
            engine.registerUser(parts[0]);
            double cash = Double.parseDouble(parts[1]);
            if (cash > 0) {
                engine.depositFunds(parts[0], cash);
            }
        }
        return engine;
    }

    private LoadSummary upload(Engine engine, Path file, String uploader) {
        try (InputStream in = Files.newInputStream(file)) {
            return engine.uploadEvents(in, file.getFileName().toString(), uploader);
        } catch (IOException e) {
            throw new AssertionError("cannot read " + file + ": " + e);
        }
    }

    private InvalidFileException rejected(Path file) {
        Engine engine = withUsers("Uploader:0");
        return assertThrows("rejected", InvalidFileException.class, () -> upload(engine, file, "Uploader"));
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    private static InputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
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

    private UserSummary summaryOf(Engine engine, String name) {
        for (UserSummary summary : engine.listUsers()) {
            if (summary.name().equals(name)) {
                return summary;
            }
        }
        throw new AssertionError("no user " + name);
    }
}
