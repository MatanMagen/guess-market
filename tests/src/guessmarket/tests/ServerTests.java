package guessmarket.tests;

import guessmarket.client.Server;
import guessmarket.client.ServerException;
import guessmarket.dto.AccountEntry;
import guessmarket.dto.EntryKind;
import guessmarket.dto.EventLifecycle;
import guessmarket.dto.EventSummary;
import guessmarket.dto.LoadSummary;
import guessmarket.dto.OrderResult;
import guessmarket.dto.OrderSide;
import guessmarket.dto.UserDetails;
import guessmarket.dto.UserSummary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static guessmarket.tests.TestSupport.assertClose;
import static guessmarket.tests.TestSupport.assertContains;
import static guessmarket.tests.TestSupport.assertEquals;
import static guessmarket.tests.TestSupport.assertThrows;
import static guessmarket.tests.TestSupport.assertTrue;

/**
 * Drives a running server over HTTP through the client's own {@link Server} class, the way two
 * JavaFX clients would, and checks what each of them sees.
 * <p>
 * Needs guess-market.war deployed and freshly started, since names and event names are unique for
 * the life of the server: {@code java ... ServerTests http://localhost:8080/guess-market test-files}.
 */
public final class ServerTests {

    private final TestSupport support = new TestSupport();
    private final String url;
    private final Path ex3;
    private final Path local;

    private ServerTests(String url, Path testFiles) {
        this.url = url;
        this.ex3 = testFiles.resolve("ex3");
        this.local = testFiles.resolve("local");
    }

    public static void main(String[] args) {
        String url = args.length > 0 ? args[0] : Server.DEFAULT_URL;
        Path testFiles = Path.of(args.length > 1 ? args[1] : "test-files");
        System.exit(new ServerTests(url, testFiles).run());
    }

    private int run() {
        Server alice = new Server(url);
        Server bob = new Server(url);

        support.group("logging in over HTTP");

        support.check("two users log in under their own names", () -> {
            assertEquals("alice", "Alice", alice.login("Alice").name());
            assertEquals("bob", "Bob", bob.login("Bob").name());
        });

        support.check("a third client cannot take a name already in use", () -> {
            ServerException thrown = assertThrows("taken", ServerException.class, () -> new Server(url).login("Alice"));
            assertEquals("conflict", 409, thrown.status());
            assertContains("message", "already taken", thrown.getMessage());
        });

        support.check("a client that has not logged in is refused", () -> {
            ServerException thrown = assertThrows("anonymous", ServerException.class, () -> new Server(url).me());
            assertEquals("unauthorised", 401, thrown.status());
        });

        support.check("names are not limited to English", () ->
                assertEquals("hebrew", "מתן", new Server(url).login("מתן").name()));

        support.group("money");

        support.check("each user loads money into his own account", () -> {
            assertClose("alice", 1000.0d, alice.deposit(1000).balance());
            assertClose("bob", 500.0d, bob.deposit(500).balance());
            assertClose("bob sees his own", 500.0d, bob.me().balance());
        });

        support.check("a deposit that is not a number is refused in words", () -> {
            ServerException thrown = assertThrows("bad", ServerException.class, () -> alice.deposit(-3));
            assertContains("message", "positive", thrown.getMessage());
        });

        support.group("uploading");

        support.check("an upload adds its events and makes the uploader their maker", () -> {
            LoadSummary summary = upload(alice, ex3.resolve("multiple.xml"));
            assertEquals("three", 3, summary.eventNames().size());
            List<EventSummary> seenByBob = bob.events();
            assertEquals("bob sees them", 3, seenByBob.size());
            assertEquals("maker", "Alice", seenByBob.get(0).marketMakerName());
            assertTrue("alice is a maker", userOf(bob, "Alice").marketMaker());
            assertTrue("bob is not", !userOf(bob, "Bob").marketMaker());
        });

        support.check("a second upload accumulates", () -> {
            upload(bob, ex3.resolve("small.xml"));
            assertEquals("four", 4, alice.events().size());
        });

        support.check("an event name already in the system is refused", () -> {
            ServerException thrown = assertThrows("again", ServerException.class,
                    () -> upload(alice, ex3.resolve("small.xml")));
            assertContains("message", "already an event called 'Mujtaba is Dead'", thrown.getMessage());
            assertEquals("still four", 4, alice.events().size());
        });

        support.check("a faulty file is refused with its reason", () -> {
            ServerException thrown = assertThrows("high", ServerException.class,
                    () -> upload(alice, local.resolve("bad-commission-high.xml")));
            assertContains("message", "commission of 95", thrown.getMessage());
        });

        support.check("a file that is not xml is refused", () -> {
            ServerException thrown = assertThrows("txt", ServerException.class,
                    () -> upload(alice, local.resolve("not-an-xml-file.txt")));
            assertContains("message", ".xml", thrown.getMessage());
        });

        support.group("trading between two clients");

        int earthQuake = idOf(alice, "Earth Quake on Dead Sea");
        int worldCup = idOf(alice, "World Cap Winner");
        int rain = idOf(alice, "Will it rain tomorrow ?");

        support.check("only the market maker can start his event", () -> {
            ServerException thrown = assertThrows("bob", ServerException.class, () -> bob.open(worldCup));
            assertContains("message", "Only 'Alice'", thrown.getMessage());
        });

        support.check("starting a book buys its stock out of the maker's account", () -> {
            assertTrue("active", alice.open(worldCup).summary().status() == EventLifecycle.ACTIVE);
            assertClose("alice", 900.0d, alice.me().balance());
        });

        support.check("a maker without the money cannot start an event", () -> {
            ServerException thrown = assertThrows("poor", ServerException.class, () -> alice.open(earthQuake));
            assertContains("message", "needs 1,000.00", thrown.getMessage());
        });

        support.check("an order from one client fills against another's", () -> {
            bob.order(worldCup, 1, OrderSide.BUY, 10, 0.60);
            OrderResult sale = alice.order(worldCup, 1, OrderSide.SELL, 10, 0.55);
            assertEquals("filled", 10L, sale.quantityFilled());
            assertClose("at bob's price", 0.60d, sale.fills().get(0).price());
            assertClose("bob paid", 494.0d, bob.me().balance());
            assertClose("alice received", 906.0d, alice.me().balance());
        });

        support.check("an lmsr quote matches what the purchase then costs", () -> {
            alice.open(rain);
            double quote = bob.quote(rain, 1, 20);
            assertClose("cost", quote, bob.buy(rain, 1, 20).sharesCost());
        });

        support.check("closing pays the winner, and his account says so", () -> {
            double before = bob.me().balance();
            alice.close(rain, 1);
            UserDetails after = bob.me();
            assertClose("20 shares less 10%", before + 18.0d, after.balance());
            AccountEntry last = after.ledger().get(after.ledger().size() - 1);
            assertTrue("commission is the last line", last.kind() == EntryKind.COMMISSION_PAID);
        });

        support.check("each user sees only his own account", () -> {
            assertEquals("bob's name", "Bob", bob.me().name());
            assertEquals("alice's name", "Alice", alice.me().name());
        });

        support.group("many requests at once");

        support.check("simultaneous purchases from four clients are all counted", () -> {
            int stock = idOf(alice, "Mujtaba is Dead");
            bob.open(stock);
            List<Thread> threads = new ArrayList<>();
            for (int t = 0; t < 4; t++) {
                Server trader = new Server(url);
                trader.login("Trader" + t);
                trader.deposit(100);
                threads.add(Thread.ofPlatform().start(() -> {
                    for (int i = 0; i < 25; i++) {
                        trader.buy(stock, 1, 1);
                    }
                }));
            }
            for (Thread thread : threads) {
                thread.join();
            }
            assertEquals("100 shares", 100L, alice.event(stock).lmsr().outcomes().get(0).sharesBought());
        });

        support.group("bonus 1: chat");

        support.check("a line one user writes reaches the other", () -> {
            int before = bob.chat(0).version();
            alice.say("hello Bob");
            var lines = bob.chat(before);
            assertEquals("one new line", 1, lines.lines().size());
            assertEquals("from", "Alice", lines.lines().get(0).userName());
            assertEquals("text", "hello Bob", lines.lines().get(0).text());
        });

        return support.report();
    }

    private LoadSummary upload(Server server, Path file) throws IOException {
        return server.upload(file.getFileName().toString(), Files.readAllBytes(file));
    }

    private static int idOf(Server server, String eventName) {
        for (EventSummary event : server.events()) {
            if (event.name().equals(eventName)) {
                return event.id();
            }
        }
        return -1;
    }

    private static UserSummary userOf(Server server, String name) {
        for (UserSummary user : server.users()) {
            if (user.name().equals(name)) {
                return user;
            }
        }
        throw new AssertionError("no user " + name);
    }
}
