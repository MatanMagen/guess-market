package guessmarket.tests;

import guessmarket.dto.CloseResult;
import guessmarket.dto.CommissionPolicy;
import guessmarket.dto.EventLifecycle;
import guessmarket.dto.EventState;
import guessmarket.dto.EventSummary;
import guessmarket.dto.LoadSummary;
import guessmarket.dto.ProblemKind;
import guessmarket.dto.PurchaseResult;
import guessmarket.engine.api.Engine;
import guessmarket.engine.exception.EventClosedException;
import guessmarket.engine.exception.GuessMarketException;
import guessmarket.engine.exception.InvalidFileException;
import guessmarket.engine.exception.InvalidQuantityException;
import guessmarket.engine.exception.NoActiveEventsException;
import guessmarket.engine.exception.NoFileLoadedException;
import guessmarket.engine.exception.NoSuchEventException;
import guessmarket.engine.exception.NoSuchOutcomeException;
import guessmarket.engine.exception.StatePersistenceException;
import guessmarket.engine.impl.GuessMarketEngine;
import guessmarket.engine.model.CommissionType;
import guessmarket.engine.model.Event;
import guessmarket.engine.model.LmsrMarket;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static guessmarket.tests.TestSupport.assertClose;
import static guessmarket.tests.TestSupport.assertEquals;
import static guessmarket.tests.TestSupport.assertFalse;
import static guessmarket.tests.TestSupport.assertKinds;
import static guessmarket.tests.TestSupport.assertThrows;
import static guessmarket.tests.TestSupport.assertTrue;
import static guessmarket.tests.TestSupport.assertValue;

/**
 * The test suite for exercise 1.
 * <p>
 * Every expected figure below was worked out independently of the code under test, from the
 * formulas in appendix A of the exercise and from the LMSR simulator that came with it, so a
 * test failing means the implementation disagrees with the exercise rather than with itself.
 * <p>
 * Faults are checked by the {@link ProblemKind} or exception type the engine reports, never by
 * the wording of a message. The engine does not produce wording, and a test that asserted on
 * sentences would be pinning down the wrong layer.
 * <p>
 * Run with {@code ./test.sh}. The first argument is the folder holding the test files; it
 * defaults to {@code test-files}.
 */
public final class Ex1Tests {

    // Figures for b = 100 with two answers, computed from C(q) = b * ln(SUM e^(q_i/b)).
    private static final double B100_SUBSIDY = 69.3147180560;
    private static final double B100_BUY_100_COST = 62.0114506958;
    private static final double B100_BUY_100_COMMISSION_5 = 3.1005725348;
    private static final double B100_BUY_100_TOTAL_5 = 65.1120232306;
    private static final double B100_PRICE_AFTER_100 = 0.7310585786;
    private static final double B100_THEN_BUY_50_OTHER_COST = 16.0815296662;
    private static final double B100_BALANCE_AFTER_TWO_BUYS = 151.3123474361;

    // Figures for the other liquidity values that appear in the supplied files.
    private static final double B50_SUBSIDY = 34.6573590280;
    private static final double B50_BUY_100_COST = 71.6890415242;
    private static final double B50_PRICE_AFTER_100 = 0.8807970780;
    private static final double B400_SUBSIDY = 277.2588722240;
    private static final double B400_BUY_100_COST = 53.1168957276;
    private static final double B200_SUBSIDY = 138.6294361120;

    private static final double PRICE_TOLERANCE = 1e-9;

    private final TestSupport t = new TestSupport();
    private final Path officialFiles;
    private final Path localFiles;
    private final Path scratch;

    private Ex1Tests(Path testFilesRoot, Path scratch) {
        this.officialFiles = testFilesRoot.resolve("ex1");
        this.localFiles = testFilesRoot.resolve("local");
        this.scratch = scratch;
    }

    /**
     * @param args optionally the test files folder, then a scratch folder for saved states.
     */
    public static void main(String[] args) throws Exception {
        Path testFiles = Path.of(args.length > 0 ? args[0] : "test-files");
        Path scratch = Path.of(args.length > 1 ? args[1] : "build/test-scratch");
        Files.createDirectories(scratch);
        System.exit(new Ex1Tests(testFiles, scratch).runAll());
    }

    private int runAll() {
        lmsrMath();
        loadingValidFiles();
        rejectingBadFiles();
        stateIsNotDisturbedByABadFile();
        commandsNeedALoadedFile();
        transferObjectsAreSealedOff();
        buyingOnPurchaseCommission();
        buyingOnCloseCommission();
        tradeHistoryOrder();
        closingOnCloseCommission();
        closingOnPurchaseCommission();
        closedEventsAreOutOfPlay();
        rangeChecks();
        saveAndRestore();
        return t.report();
    }

    // ---------------------------------------------------------------- LMSR math

    private void lmsrMath() {
        t.group("LMSR pricing");

        t.check("a fresh two answer market opens at b*ln(2)", () -> {
            assertClose("b=100", B100_SUBSIDY, new LmsrMarket(100, 2).openingSubsidy());
            assertClose("b=50", B50_SUBSIDY, new LmsrMarket(50, 2).openingSubsidy());
            assertClose("b=400", B400_SUBSIDY, new LmsrMarket(400, 2).openingSubsidy());
            assertClose("b=200", B200_SUBSIDY, new LmsrMarket(200, 2).openingSubsidy());
        });

        t.check("both answers start at 0.50", () -> {
            LmsrMarket market = new LmsrMarket(100, 2);
            assertClose("first answer", 0.5d, market.price(0), PRICE_TOLERANCE);
            assertClose("second answer", 0.5d, market.price(1), PRICE_TOLERANCE);
        });

        t.check("the worked example from appendix A: 100 shares at b=100 cost 62.01", () -> {
            LmsrMarket market = new LmsrMarket(100, 2);
            assertClose("cost", B100_BUY_100_COST, market.applyBuy(0, 100));
            assertClose("price after", B100_PRICE_AFTER_100, market.price(0), PRICE_TOLERANCE);
            assertClose("other answer is the complement", 1.0d - B100_PRICE_AFTER_100, market.price(1),
                    PRICE_TOLERANCE);
        });

        t.check("a quote does not move the market", () -> {
            LmsrMarket market = new LmsrMarket(100, 2);
            double first = market.quoteBuy(0, 100);
            double second = market.quoteBuy(0, 100);
            assertClose("repeated quotes agree", first, second);
            assertClose("price untouched", 0.5d, market.price(0), PRICE_TOLERANCE);
        });

        t.check("cost rises as liquidity falls", () -> {
            assertClose("b=50", B50_BUY_100_COST, new LmsrMarket(50, 2).applyBuy(0, 100));
            assertClose("b=400", B400_BUY_100_COST, new LmsrMarket(400, 2).applyBuy(0, 100));
            assertTrue("thin market costs more than deep market",
                    B50_BUY_100_COST > B100_BUY_100_COST && B100_BUY_100_COST > B400_BUY_100_COST);
        });

        t.check("prices always add up to 1", () -> {
            LmsrMarket market = new LmsrMarket(37, 2);
            market.applyBuy(0, 1234);
            market.applyBuy(1, 77);
            assertClose("sum of prices", 1.0d, market.price(0) + market.price(1), PRICE_TOLERANCE);
        });

        t.check("a huge position does not overflow to infinity", () -> {
            LmsrMarket market = new LmsrMarket(1, 2);
            double cost = market.applyBuy(0, 100_000L);
            assertTrue("cost is a finite number", Double.isFinite(cost));
            assertClose("cost", 100_000d - Math.log(2.0d), cost, 1e-6);
            assertClose("price saturates at 1", 1.0d, market.price(0), 1e-12);
            assertTrue("the losing answer is finite and non negative",
                    Double.isFinite(market.price(1)) && market.price(1) >= 0.0d);
        });

        t.check("C(q) is never below the largest position, so winners can always be paid", () -> {
            LmsrMarket market = new LmsrMarket(20, 2);
            market.applyBuy(0, 500);
            assertTrue("C(q) >= q_max", market.currentCost() >= 500 * LmsrMarket.PAYOUT_PER_WINNING_SHARE);
        });

        t.check("a market rejects nonsense construction", () -> {
            assertThrows("b = 0", IllegalArgumentException.class, () -> new LmsrMarket(0, 2));
            assertThrows("b < 0", IllegalArgumentException.class, () -> new LmsrMarket(-5, 2));
            assertThrows("one answer", IllegalArgumentException.class, () -> new LmsrMarket(100, 1));
        });

        t.check("a market rejects a non positive quantity", () -> {
            LmsrMarket market = new LmsrMarket(100, 2);
            assertThrows("zero shares", IllegalArgumentException.class, () -> market.quoteBuy(0, 0));
            assertThrows("negative shares", IllegalArgumentException.class, () -> market.quoteBuy(0, -1));
        });
    }

    // ------------------------------------------------------------- loading files

    private void loadingValidFiles() {
        t.group("Loading the supplied valid files");

        t.check("single.xml loads one event with its details intact", () -> {
            Engine engine = new GuessMarketEngine();
            LoadSummary summary = engine.loadFromFile(official("single.xml"));
            assertEquals("events loaded", 1, summary.eventsLoaded());

            EventSummary event = engine.listEvents().get(0);
            assertEquals("display number", 1, event.displayNumber());
            assertEquals("id", 3, event.id());
            assertEquals("name", "Earth Quake on Dead Sea", event.name());
            assertEquals("commission", 50, event.commissionPercent());
            assertTrue("commission policy", event.commissionType() == CommissionPolicy.ON_PURCHASE);
            assertTrue("status", event.status() == EventLifecycle.ACTIVE);
            assertEquals("answers", 2, event.outcomeNames().size());
            assertEquals("first answer", "Yes", event.outcomeNames().get(0));
            assertEquals("second answer", "No", event.outcomeNames().get(1));
            assertEquals("liquidity", 100, engine.eventState(1).liquidity());
        });

        t.check("multiple.xml loads three events, numbered from 1 in file order", () -> {
            Engine engine = loadedMultiple();
            List<EventSummary> events = engine.listEvents();
            assertEquals("events loaded", 3, events.size());

            assertEquals("first display number", 1, events.get(0).displayNumber());
            assertEquals("second display number", 2, events.get(1).displayNumber());
            assertEquals("third display number", 3, events.get(2).displayNumber());
            assertEquals("first id", 1, events.get(0).id());
            assertEquals("second id", 2, events.get(1).id());
            assertEquals("third id", 3, events.get(2).id());
            assertEquals("second name", "World Cap Winner", events.get(1).name());
            assertEquals("second commission", 15, events.get(1).commissionPercent());
            assertTrue("second policy", events.get(1).commissionType() == CommissionPolicy.ON_CLOSE);
            assertTrue("third policy", events.get(2).commissionType() == CommissionPolicy.ON_PURCHASE);
            assertEquals("liquidity of event 2", 50, engine.eventState(2).liquidity());
            assertEquals("liquidity of event 3", 400, engine.eventState(3).liquidity());
        });

        t.check("a name written with spaces survives the xs:list attribute", () -> {
            Engine engine = loadedMultiple();
            assertEquals("name with two spaces in it", "Mujtaba is Dead", engine.listEvents().get(0).name());
        });

        t.check("runs of spaces in a name collapse to one and the name is trimmed", () -> {
            Engine engine = new GuessMarketEngine();
            engine.loadFromFile(local("spaced-name.xml"));
            assertEquals("collapsed name", "Multi Spaced Name", engine.listEvents().get(0).name());
        });

        t.check("a commission of 0 percent is allowed", () -> {
            Engine engine = new GuessMarketEngine();
            engine.loadFromFile(local("spaced-name.xml"));
            assertEquals("commission", 0, engine.listEvents().get(0).commissionPercent());
        });

        t.check("a fresh event is funded with its subsidy and has no trades", () -> {
            EventState state = loadedMultiple().eventState(1);
            assertClose("opening subsidy", B100_SUBSIDY, state.openingSubsidy());
            assertClose("balance equals the subsidy", B100_SUBSIDY, state.accountBalance());
            assertClose("market maker is level", 0.0d, state.marketMakerNetResult());
            assertClose("no commission yet", 0.0d, state.commissionCollected());
            assertEquals("no trades yet", 0, state.tradeHistory().size());
            assertFalse("not closed", state.closed());
            assertEquals("no shares on the first answer", 0, state.outcomes().get(0).sharesBought());
        });

        t.check("a path wrapped in quotation marks is accepted", () -> {
            Engine engine = new GuessMarketEngine();
            assertEquals("events loaded", 1,
                    engine.loadFromFile("\"" + official("single.xml") + "\"").eventsLoaded());
        });

        t.check("a path padded with spaces is accepted", () -> {
            Engine engine = new GuessMarketEngine();
            assertEquals("events loaded", 1,
                    engine.loadFromFile("   " + official("single.xml") + "   ").eventsLoaded());
        });

        t.check("loading a second valid file replaces the first completely", () -> {
            Engine engine = loadedMultiple();
            engine.buyShares(1, 1, 100);
            assertEquals("events after the second load", 1,
                    engine.loadFromFile(official("single.xml")).eventsLoaded());
            assertEquals("only the new events remain", 1, engine.listEvents().size());
            assertEquals("the old trade history is gone", 0, engine.eventState(1).tradeHistory().size());
            assertEquals("the new event is the one from single.xml", 3, engine.listEvents().get(0).id());
        });
    }

    // ------------------------------------------------------------ rejecting files

    private void rejectingBadFiles() {
        t.group("Rejecting unsound files");

        t.check("error-2.xml is rejected because two events share a number", () -> {
            InvalidFileException e = reject(official("error-2.xml"));
            assertKinds("faults", e.problems(), ProblemKind.DUPLICATE_EVENT_ID);
            assertEquals("the offending position", 3, e.problems().get(0).eventPosition());
            assertValue("the repeated number", e.problems().get(0), 0, "1");
            assertValue("the earlier position", e.problems().get(0), 1, "1");
        });

        t.check("error-3.xml is rejected because a commission is above 90", () -> {
            InvalidFileException e = reject(official("error-3.xml"));
            assertKinds("faults", e.problems(), ProblemKind.COMMISSION_OUT_OF_RANGE);
            assertEquals("the offending position", 2, e.problems().get(0).eventPosition());
            assertEquals("the offending event", "World Cap Winner", e.problems().get(0).eventName());
            assertValue("the value found", e.problems().get(0), 0, "115");
            assertValue("the lowest allowed", e.problems().get(0), 1, "0");
            assertValue("the highest allowed", e.problems().get(0), 2, "90");
        });

        t.check("a negative commission is rejected", () -> {
            InvalidFileException e = reject(local("bad-commission-negative.xml"));
            assertKinds("faults", e.problems(), ProblemKind.COMMISSION_OUT_OF_RANGE);
            assertValue("the value found", e.problems().get(0), 0, "-5");
        });

        t.check("a liquidity of zero is rejected rather than dividing by it", () -> {
            InvalidFileException e = reject(local("bad-b-zero.xml"));
            assertKinds("faults", e.problems(), ProblemKind.LIQUIDITY_NOT_POSITIVE);
            assertValue("the value found", e.problems().get(0), 0, "0");
        });

        t.check("a negative liquidity is rejected", () -> {
            InvalidFileException e = reject(local("bad-b-negative.xml"));
            assertKinds("faults", e.problems(), ProblemKind.LIQUIDITY_NOT_POSITIVE);
            assertValue("the value found", e.problems().get(0), 0, "-5");
        });

        t.check("an event with only one answer is rejected", () -> {
            InvalidFileException e = reject(local("bad-one-answer.xml"));
            assertKinds("faults", e.problems(), ProblemKind.WRONG_ANSWER_COUNT);
            assertValue("the count found", e.problems().get(0), 0, "1");
            assertValue("the count required", e.problems().get(0), 1, "2");
        });

        t.check("an event whose two answers are the same is rejected", () -> {
            InvalidFileException e = reject(local("bad-duplicate-answers.xml"));
            assertKinds("faults", e.problems(), ProblemKind.DUPLICATE_ANSWER);
            assertValue("the repeated answer", e.problems().get(0), 0, "yes");
        });

        t.check("an event with a blank name is rejected", () -> {
            InvalidFileException e = reject(local("bad-blank-name.xml"));
            assertKinds("faults", e.problems(), ProblemKind.BLANK_EVENT_NAME);
        });

        t.check("a file holding no events is rejected", () -> {
            InvalidFileException e = reject(local("bad-no-events.xml"));
            assertKinds("faults", e.problems(), ProblemKind.NO_EVENTS);
            assertFalse("the fault belongs to the file, not an event",
                    e.problems().get(0).belongsToAnEvent());
        });

        t.check("a file that is not XML is rejected on its extension", () -> {
            InvalidFileException e = reject(local("not-an-xml-file.txt"));
            assertKinds("faults", e.problems(), ProblemKind.WRONG_EXTENSION);
            assertValue("the required extension", e.problems().get(0), 1, ".xml");
        });

        t.check("a file that is not there is reported as missing", () -> {
            InvalidFileException e = reject(officialFiles.resolve("no-such-file.xml").toString());
            assertKinds("faults", e.problems(), ProblemKind.FILE_NOT_FOUND);
        });

        t.check("a name that is neither xml nor present reports both faults at once", () -> {
            InvalidFileException e = reject(officialFiles.resolve("no-such-file.txt").toString());
            assertKinds("both faults", e.problems(),
                    ProblemKind.WRONG_EXTENSION, ProblemKind.FILE_NOT_FOUND);
        });

        t.check("a folder given instead of a file is reported", () -> {
            InvalidFileException e = reject(officialFiles.toString() + "/");
            assertTrue("some fault was reported", !e.problems().isEmpty());
        });

        t.check("an empty path is reported", () -> {
            InvalidFileException e = reject("   ");
            assertKinds("faults", e.problems(), ProblemKind.NO_PATH_GIVEN);
        });

        t.check("a rejection always carries at least one fault", () -> {
            assertThrows("an empty problem list is refused", IllegalArgumentException.class,
                    () -> new InvalidFileException("somewhere.xml", List.of()));
        });

        t.check("the faults of a rejection cannot be tampered with", () -> {
            InvalidFileException e = reject(official("error-3.xml"));
            assertThrows("adding a fault", UnsupportedOperationException.class,
                    () -> e.problems().add(null));
            assertThrows("changing a fault's values", UnsupportedOperationException.class,
                    () -> e.problems().get(0).values().add("tampered"));
        });
    }

    private void stateIsNotDisturbedByABadFile() {
        t.group("A rejected file leaves the loaded state alone");

        t.check("events, money and trade history all survive a rejected load", () -> {
            Engine engine = loadedMultiple();
            engine.buyShares(1, 1, 100);
            double balanceBefore = engine.eventState(1).accountBalance();
            String sourceBefore = engine.loadedSourceDescription();

            assertThrows("the bad file is rejected", InvalidFileException.class,
                    () -> engine.loadFromFile(official("error-2.xml")));

            assertEquals("still three events", 3, engine.listEvents().size());
            assertEquals("the trade is still there", 1, engine.eventState(1).tradeHistory().size());
            assertClose("the balance is untouched", balanceBefore, engine.eventState(1).accountBalance());
            assertEquals("the loaded file is still the old one", sourceBefore, engine.loadedSourceDescription());
        });

        t.check("nothing becomes loaded when the very first file is rejected", () -> {
            Engine engine = new GuessMarketEngine();
            assertThrows("rejected", InvalidFileException.class,
                    () -> engine.loadFromFile(official("error-3.xml")));
            assertFalse("still nothing loaded", engine.isLoaded());
            assertThrows("listing still refuses", NoFileLoadedException.class, engine::listEvents);
        });

        t.check("the load command works again after a rejection", () -> {
            Engine engine = new GuessMarketEngine();
            assertThrows("rejected", InvalidFileException.class,
                    () -> engine.loadFromFile(official("error-2.xml")));
            assertEquals("a good file loads afterwards", 3,
                    engine.loadFromFile(official("multiple.xml")).eventsLoaded());
        });
    }

    // ------------------------------------------------- commands before any load

    private void commandsNeedALoadedFile() {
        t.group("Commands that need a loaded file say so");

        t.check("a new engine has nothing loaded", () -> {
            assertFalse("not loaded", new GuessMarketEngine().isLoaded());
        });

        t.check("every command that needs a file throws NoFileLoadedException", () -> {
            Engine engine = new GuessMarketEngine();
            for (TestSupport.ThrowingRunnable command : List.<TestSupport.ThrowingRunnable>of(
                    engine::listEvents,
                    engine::listActiveEvents,
                    () -> engine.eventState(1),
                    () -> engine.activeEventState(1),
                    () -> engine.buyShares(1, 1, 10),
                    () -> engine.closeEvent(1, 1),
                    () -> engine.saveState("ignored"),
                    engine::loadedSourceDescription)) {
                assertThrows("command without a file", NoFileLoadedException.class, command);
            }
        });

        t.check("every engine fault is a GuessMarketException and is unchecked", () -> {
            // Unchecked matters: it is why one catch site in the console covers every command.
            for (Class<?> type : List.of(
                    NoFileLoadedException.class, NoActiveEventsException.class,
                    NoSuchEventException.class, NoSuchOutcomeException.class,
                    InvalidQuantityException.class, EventClosedException.class,
                    InvalidFileException.class, StatePersistenceException.class)) {
                assertTrue(type.getSimpleName() + " extends GuessMarketException",
                        GuessMarketException.class.isAssignableFrom(type));
                assertTrue(type.getSimpleName() + " is unchecked",
                        RuntimeException.class.isAssignableFrom(type));
            }
        });
    }

    private void transferObjectsAreSealedOff() {
        t.group("The engine answers only with sealed off transfer objects");

        t.check("the lists inside an answer cannot be modified", () -> {
            EventState state = loadedMultiple().eventState(1);
            assertThrows("answer list", UnsupportedOperationException.class,
                    () -> state.outcomes().add(null));
            assertThrows("trade history", UnsupportedOperationException.class,
                    () -> state.tradeHistory().add(null));
            assertThrows("answer names", UnsupportedOperationException.class,
                    () -> state.summary().outcomeNames().add("tampered"));
        });

        t.check("an answer is a snapshot and does not follow later trading", () -> {
            Engine engine = loadedMultiple();
            EventState before = engine.eventState(1);
            engine.buyShares(1, 1, 100);

            assertEquals("the old snapshot still shows no trades", 0, before.tradeHistory().size());
            assertClose("and still shows the opening balance", B100_SUBSIDY, before.accountBalance());
            assertEquals("while a fresh one shows the trade", 1,
                    engine.eventState(1).tradeHistory().size());
        });

        t.check("nothing an interface receives exposes an engine object", () -> {
            EventState state = loadedMultiple().eventState(1);
            for (Object value : List.of(state, state.summary(), state.outcomes().get(0),
                    state.summary().commissionType(), state.summary().status())) {
                String packageName = value.getClass().getPackageName();
                assertEquals("the type handed out lives in the dto module",
                        "guessmarket.dto", packageName);
            }
        });
    }

    // -------------------------------------------------------------- buying shares

    private void buyingOnPurchaseCommission() {
        t.group("Buying in an event that charges commission on purchase");

        t.check("the buyer pays the LMSR cost plus the commission", () -> {
            PurchaseResult result = loadedMultiple().buyShares(1, 1, 100);

            assertEquals("answer bought", "Hell Yea !", result.outcomeName());
            assertEquals("quantity", 100, result.quantity());
            assertClose("shares cost", B100_BUY_100_COST, result.sharesCost());
            assertClose("commission at 5%", B100_BUY_100_COMMISSION_5, result.commissionPaid());
            assertClose("total paid", B100_BUY_100_TOTAL_5, result.totalPaid());
            assertTrue("commission was charged", result.commissionCharged());
        });

        t.check("the money lands in the event account and is counted as commission", () -> {
            EventState state = loadedMultiple().buyShares(1, 1, 100).stateAfterPurchase();

            assertClose("balance is subsidy plus what was paid",
                    B100_SUBSIDY + B100_BUY_100_TOTAL_5, state.accountBalance());
            assertClose("commission collected", B100_BUY_100_COMMISSION_5, state.commissionCollected());
            assertClose("market maker net result", B100_BUY_100_TOTAL_5, state.marketMakerNetResult());
        });

        t.check("prices and share counts move with the purchase", () -> {
            EventState state = loadedMultiple().buyShares(1, 1, 100).stateAfterPurchase();

            assertClose("bought answer", B100_PRICE_AFTER_100, state.outcomes().get(0).price(), PRICE_TOLERANCE);
            assertClose("other answer", 1.0d - B100_PRICE_AFTER_100, state.outcomes().get(1).price(),
                    PRICE_TOLERANCE);
            assertEquals("shares on the bought answer", 100, state.outcomes().get(0).sharesBought());
            assertEquals("shares on the other answer", 0, state.outcomes().get(1).sharesBought());
        });

        t.check("a second purchase is priced from the moved market", () -> {
            Engine engine = loadedMultiple();
            engine.buyShares(1, 1, 100);
            PurchaseResult second = engine.buyShares(1, 2, 50);

            assertClose("cost of the second purchase", B100_THEN_BUY_50_OTHER_COST, second.sharesCost());
            assertClose("balance after both purchases",
                    B100_BALANCE_AFTER_TWO_BUYS, second.stateAfterPurchase().accountBalance());
            assertEquals("two trades recorded", 2, second.stateAfterPurchase().tradeHistory().size());
        });

        t.check("a 50 percent commission on a deep event is charged in full", () -> {
            PurchaseResult result = loadedMultiple().buyShares(3, 1, 100);
            assertClose("shares cost at b=400", B400_BUY_100_COST, result.sharesCost());
            assertClose("commission at 50%", B400_BUY_100_COST * 0.5d, result.commissionPaid());
            assertClose("total paid", B400_BUY_100_COST * 1.5d, result.totalPaid());
        });

        t.check("buying one share at a time costs the same as buying them together", () -> {
            Engine one = loadedMultiple();
            Engine many = loadedMultiple();
            one.buyShares(1, 1, 10);
            double togetherCost = one.eventState(1).tradeHistory().get(0).sharesCost();
            double pieceByPiece = 0.0d;
            for (int i = 0; i < 10; i++) {
                pieceByPiece += many.buyShares(1, 1, 1).sharesCost();
            }
            assertClose("the cost function is path independent", togetherCost, pieceByPiece, 1e-9);
        });
    }

    private void buyingOnCloseCommission() {
        t.group("Buying in an event that charges commission on close");

        t.check("no commission is taken at the time of the purchase", () -> {
            PurchaseResult result = loadedMultiple().buyShares(2, 1, 100);

            assertClose("shares cost at b=50", B50_BUY_100_COST, result.sharesCost());
            assertClose("no commission", 0.0d, result.commissionPaid());
            assertClose("total is just the shares", B50_BUY_100_COST, result.totalPaid());
            assertFalse("commission not charged now", result.commissionCharged());
            assertClose("nothing collected yet", 0.0d, result.stateAfterPurchase().commissionCollected());
        });

        t.check("the account still receives the full purchase price", () -> {
            EventState state = loadedMultiple().buyShares(2, 1, 100).stateAfterPurchase();
            assertClose("balance", B50_SUBSIDY + B50_BUY_100_COST, state.accountBalance());
            assertClose("price after", B50_PRICE_AFTER_100, state.outcomes().get(0).price(), PRICE_TOLERANCE);
        });
    }

    private void tradeHistoryOrder() {
        t.group("Trade history");

        t.check("the newest trade is listed first", () -> {
            Engine engine = loadedMultiple();
            engine.buyShares(1, 1, 10);
            engine.buyShares(1, 2, 20);
            EventState state = engine.buyShares(1, 1, 30).stateAfterPurchase();

            assertEquals("three trades", 3, state.tradeHistory().size());
            assertEquals("newest first", 30, state.tradeHistory().get(0).quantity());
            assertEquals("then the middle one", 20, state.tradeHistory().get(1).quantity());
            assertEquals("oldest last", 10, state.tradeHistory().get(2).quantity());
            assertEquals("the answer is recorded", "No way !", state.tradeHistory().get(1).outcomeName());
        });

        t.check("each line adds up", () -> {
            EventState state = loadedMultiple().buyShares(1, 1, 100).stateAfterPurchase();
            var line = state.tradeHistory().get(0);
            assertClose("total is shares plus commission",
                    line.sharesCost() + line.commissionPaid(), line.totalPaid());
        });
    }

    // -------------------------------------------------------------- closing events

    private void closingOnCloseCommission() {
        t.group("Closing an event that charges commission on close");

        t.check("the commission comes off the winning position and the rest is paid out", () -> {
            Engine engine = loadedMultiple();
            engine.buyShares(2, 1, 100);
            CloseResult result = engine.closeEvent(2, 1);

            assertEquals("winning answer", "Argentina", result.winningOutcomeName());
            assertEquals("winning shares", 100, result.winningShares());
            assertClose("gross payout is one per winning share",
                    100 * LmsrMarket.PAYOUT_PER_WINNING_SHARE, result.grossPayout());
            assertClose("commission at 15%", 15.0d, result.commissionCharged());
            assertClose("winners receive the remainder", 85.0d, result.netPaidToWinners());
            assertTrue("commission applied on close", result.commissionAppliedOnClose());
        });

        t.check("the commission stays in the account and is counted", () -> {
            Engine engine = loadedMultiple();
            engine.buyShares(2, 1, 100);
            EventState state = engine.closeEvent(2, 1).stateAfterClose();

            assertClose("balance", B50_SUBSIDY + B50_BUY_100_COST - 85.0d, state.accountBalance());
            assertClose("commission collected", 15.0d, state.commissionCollected());
            assertTrue("closed", state.closed());
            assertEquals("winner recorded", "Argentina", state.winningOutcomeName());
            assertTrue("status", state.summary().status() == EventLifecycle.CLOSED);
        });

        t.check("the market maker can end up out of pocket", () -> {
            Engine engine = loadedMultiple();
            engine.buyShares(2, 1, 100);
            EventState state = engine.closeEvent(2, 1).stateAfterClose();
            double expected = B50_BUY_100_COST - 85.0d;
            assertClose("net result", expected, state.marketMakerNetResult());
            assertTrue("and that result is negative here", state.marketMakerNetResult() < 0.0d);
        });

        t.check("closing an event nobody traded pays nothing out", () -> {
            CloseResult result = loadedMultiple().closeEvent(2, 1);
            assertEquals("no winning shares", 0, result.winningShares());
            assertClose("nothing paid out", 0.0d, result.netPaidToWinners());
            assertClose("no commission", 0.0d, result.commissionCharged());
            assertClose("the subsidy is all that is left",
                    B50_SUBSIDY, result.stateAfterClose().accountBalance());
        });

        t.check("closing on the answer nobody bought pays nothing out", () -> {
            Engine engine = loadedMultiple();
            engine.buyShares(2, 1, 100);
            CloseResult result = engine.closeEvent(2, 2);
            assertEquals("winner is the untraded answer", "Spain", result.winningOutcomeName());
            assertEquals("no winning shares", 0, result.winningShares());
            assertClose("nothing paid out", 0.0d, result.netPaidToWinners());
            assertClose("the account keeps everything",
                    B50_SUBSIDY + B50_BUY_100_COST, result.stateAfterClose().accountBalance());
        });
    }

    private void closingOnPurchaseCommission() {
        t.group("Closing an event that already charged its commission");

        t.check("winners are paid in full because the commission was taken earlier", () -> {
            Engine engine = loadedMultiple();
            engine.buyShares(1, 1, 100);
            CloseResult result = engine.closeEvent(1, 1);

            assertClose("gross payout", 100.0d, result.grossPayout());
            assertClose("no commission at close", 0.0d, result.commissionCharged());
            assertClose("winners get the whole payout", 100.0d, result.netPaidToWinners());
            assertFalse("commission not applied on close", result.commissionAppliedOnClose());
            assertClose("commission collected is still the purchase commission",
                    B100_BUY_100_COMMISSION_5, result.stateAfterClose().commissionCollected());
        });

        t.check("the residual balance is left in the account, not reset", () -> {
            Engine engine = loadedMultiple();
            engine.buyShares(1, 1, 100);
            EventState state = engine.closeEvent(1, 1).stateAfterClose();
            assertClose("balance", B100_SUBSIDY + B100_BUY_100_TOTAL_5 - 100.0d, state.accountBalance());
            assertClose("net result", B100_BUY_100_TOTAL_5 - 100.0d, state.marketMakerNetResult());
            assertTrue("the market maker subsidised this one", state.marketMakerNetResult() < 0.0d);
        });
    }

    private void closedEventsAreOutOfPlay() {
        t.group("A closed event is out of play");

        t.check("it disappears from the list of active events but stays in the full list", () -> {
            Engine engine = loadedMultiple();
            engine.closeEvent(1, 1);

            assertEquals("full list unchanged", 3, engine.listEvents().size());
            assertEquals("active list is shorter", 2, engine.listActiveEvents().size());
            assertTrue("the closed one is still shown",
                    engine.listEvents().get(0).status() == EventLifecycle.CLOSED);
            assertEquals("the first active event is now event 2", 2, engine.listActiveEvents().get(0).id());
        });

        t.check("it can no longer be reached through the active list", () -> {
            Engine engine = loadedMultiple();
            engine.closeEvent(2, 1);

            // Closing removes the event from the active list, so there is no number the user can
            // type that reaches it again: the position it used to occupy now belongs to the next
            // event along.
            List<EventSummary> active = engine.listActiveEvents();
            assertEquals("two events left active", 2, active.size());
            for (EventSummary event : active) {
                assertFalse("the closed event is gone from the active list", event.id() == 2);
            }
            assertEquals("position 2 now addresses event 3", 3, active.get(1).id());

            NoSuchEventException e = assertThrows("buying past the end of the active list",
                    NoSuchEventException.class, () -> engine.buyShares(3, 1, 10));
            assertTrue("the fault knows the listing was of active events only", e.activeOnly());
            assertEquals("and how many there were", 2, e.available());
        });

        t.check("the event itself refuses a second close and any later trade", () -> {
            // Guarding at the model level as well as through the listing means the rule holds even
            // if a future front end addresses events some other way.
            Event event = new Event(1, "Direct", "closed twice on purpose", 10,
                    CommissionType.ON_PURCHASE, List.of("Yes", "No"), 100);
            event.buy(0, 10);
            event.close(0);

            assertThrows("closing twice", IllegalStateException.class, () -> event.close(1));
            assertThrows("buying after the close", IllegalStateException.class, () -> event.buy(0, 1));
            assertEquals("the first close still stands", "Yes", event.winningOutcome().name());
        });

        t.check("once every event is closed the trading commands say so", () -> {
            Engine engine = loadedMultiple();
            engine.closeEvent(1, 1);
            engine.closeEvent(1, 1);
            engine.closeEvent(1, 1);

            assertEquals("all three still listed", 3, engine.listEvents().size());
            assertThrows("listing active events", NoActiveEventsException.class, engine::listActiveEvents);
            assertThrows("buying", NoActiveEventsException.class, () -> engine.buyShares(1, 1, 1));
            assertThrows("closing", NoActiveEventsException.class, () -> engine.closeEvent(1, 1));
        });

        t.check("its trading state can still be inspected", () -> {
            Engine engine = loadedMultiple();
            engine.buyShares(1, 1, 100);
            engine.closeEvent(1, 1);
            EventState state = engine.eventState(1);
            assertTrue("closed", state.closed());
            assertEquals("history kept", 1, state.tradeHistory().size());
            assertEquals("winner kept", "Hell Yea !", state.winningOutcomeName());
        });
    }

    // ----------------------------------------------------------------- range checks

    private void rangeChecks() {
        t.group("Numbers outside the shown ranges are refused");

        t.check("event numbers are one based and bounded", () -> {
            Engine engine = loadedMultiple();
            assertThrows("zero", NoSuchEventException.class, () -> engine.eventState(0));
            assertThrows("negative", NoSuchEventException.class, () -> engine.eventState(-1));
            assertThrows("past the end", NoSuchEventException.class, () -> engine.eventState(4));

            NoSuchEventException e = assertThrows("far past the end", NoSuchEventException.class,
                    () -> engine.eventState(99));
            assertEquals("the fault knows what was asked for", 99, e.requested());
            assertEquals("and the highest legal number", 3, e.available());
            assertFalse("this listing was of every event", e.activeOnly());
        });

        t.check("answer numbers are one based and bounded", () -> {
            Engine engine = loadedMultiple();
            assertThrows("zero", NoSuchOutcomeException.class, () -> engine.buyShares(1, 0, 10));
            assertThrows("past the end", NoSuchOutcomeException.class, () -> engine.buyShares(1, 3, 10));

            NoSuchOutcomeException e = assertThrows("closing on a bad answer",
                    NoSuchOutcomeException.class, () -> engine.closeEvent(1, 5));
            assertEquals("the fault names the event", "Mujtaba is Dead", e.eventName());
            assertEquals("what was asked for", 5, e.requested());
            assertEquals("and how many there are", 2, e.available());
        });

        t.check("a quantity must be at least one", () -> {
            Engine engine = loadedMultiple();
            InvalidQuantityException e = assertThrows("zero shares", InvalidQuantityException.class,
                    () -> engine.buyShares(1, 1, 0));
            assertEquals("the fault knows the minimum", 1, e.minimum());
            assertEquals("and what was asked for", 0, e.requested());
            assertThrows("negative shares", InvalidQuantityException.class, () -> engine.buyShares(1, 1, -5));
        });

        t.check("a refused purchase changes nothing", () -> {
            Engine engine = loadedMultiple();
            double before = engine.eventState(1).accountBalance();
            assertThrows("bad quantity", InvalidQuantityException.class, () -> engine.buyShares(1, 1, 0));
            assertThrows("bad answer", NoSuchOutcomeException.class, () -> engine.buyShares(1, 9, 10));
            assertClose("balance untouched", before, engine.eventState(1).accountBalance());
            assertEquals("no trade recorded", 0, engine.eventState(1).tradeHistory().size());
        });
    }

    // ------------------------------------------------------------- save and restore

    private void saveAndRestore() {
        t.group("Saving and restoring the system state (bonus)");

        t.check("a saved state comes back with its events, money and history", () -> {
            Engine engine = loadedMultiple();
            engine.buyShares(1, 1, 100);
            engine.buyShares(2, 2, 40);
            engine.closeEvent(3, 1);
            double balance1 = engine.eventState(1).accountBalance();
            double balance2 = engine.eventState(2).accountBalance();

            String saved = engine.saveState(scratch.resolve("round-trip").toString());
            assertTrue("the file was written", new File(saved).isFile());
            assertTrue("the extension was added", saved.endsWith(".gmstate"));

            Engine other = new GuessMarketEngine();
            assertEquals("three events restored", 3,
                    other.restoreState(scratch.resolve("round-trip").toString()).eventsLoaded());
            assertEquals("events present", 3, other.listEvents().size());
            assertEquals("history restored", 1, other.eventState(1).tradeHistory().size());
            assertClose("balance restored on event 1", balance1, other.eventState(1).accountBalance());
            assertClose("balance restored on event 2", balance2, other.eventState(2).accountBalance());
            assertTrue("the closed event is still closed", other.eventState(3).closed());
            assertEquals("its winner is still recorded", "Yes", other.eventState(3).winningOutcomeName());
        });

        t.check("a restored state can be traded on straight away", () -> {
            Engine engine = loadedMultiple();
            engine.buyShares(1, 1, 100);
            engine.saveState(scratch.resolve("continue").toString());

            Engine other = new GuessMarketEngine();
            other.restoreState(scratch.resolve("continue").toString());
            PurchaseResult result = other.buyShares(1, 2, 50);
            assertClose("priced from the restored market", B100_THEN_BUY_50_OTHER_COST, result.sharesCost());
            assertEquals("two trades now", 2, result.stateAfterPurchase().tradeHistory().size());
        });

        t.check("a restore replaces whatever was loaded", () -> {
            Engine engine = loadedMultiple();
            engine.saveState(scratch.resolve("three-events").toString());
            engine.loadFromFile(official("single.xml"));
            assertEquals("one event after the xml load", 1, engine.listEvents().size());

            engine.restoreState(scratch.resolve("three-events").toString());
            assertEquals("three events after the restore", 3, engine.listEvents().size());
        });

        t.check("a missing saved state is reported without losing what is loaded", () -> {
            Engine engine = loadedMultiple();
            StatePersistenceException e = assertThrows("restoring nothing",
                    StatePersistenceException.class,
                    () -> engine.restoreState(scratch.resolve("never-written").toString()));
            assertTrue("the fault knows it was loading",
                    e.direction() == StatePersistenceException.Direction.LOADING);
            assertEquals("the loaded events are untouched", 3, engine.listEvents().size());
        });

        t.check("a file that is not a saved state is refused", () -> {
            Engine engine = loadedMultiple();
            assertThrows("restoring an xml file", StatePersistenceException.class,
                    () -> engine.restoreState(official("multiple.xml")));
            assertEquals("the loaded events are untouched", 3, engine.listEvents().size());
        });

        t.check("saving to a folder that does not exist is reported", () -> {
            Engine engine = loadedMultiple();
            StatePersistenceException e = assertThrows("saving into thin air",
                    StatePersistenceException.class,
                    () -> engine.saveState(scratch.resolve("no/such/folder/state").toString()));
            assertTrue("the fault knows it was saving",
                    e.direction() == StatePersistenceException.Direction.SAVING);
        });

        t.check("an empty path is refused for both saving and restoring", () -> {
            Engine engine = loadedMultiple();
            assertThrows("empty save path", StatePersistenceException.class, () -> engine.saveState("  "));
            assertThrows("empty restore path", StatePersistenceException.class,
                    () -> engine.restoreState("  "));
        });
    }

    // ------------------------------------------------------------------- helpers

    private Engine loadedMultiple() {
        Engine engine = new GuessMarketEngine();
        engine.loadFromFile(official("multiple.xml"));
        return engine;
    }

    /**
     * Loads a file that is expected to be refused.
     *
     * @param path the file to try.
     * @return the rejection, so the caller can inspect the faults it carries.
     */
    private InvalidFileException reject(String path) {
        return assertThrows("loading '" + path + "'", InvalidFileException.class,
                () -> new GuessMarketEngine().loadFromFile(path));
    }

    private String official(String fileName) {
        return officialFiles.resolve(fileName).toString();
    }

    private String local(String fileName) {
        return localFiles.resolve(fileName).toString();
    }
}
