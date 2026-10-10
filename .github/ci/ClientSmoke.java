import guessmarket.client.AppContext;
import guessmarket.client.LoginView;
import guessmarket.client.MainView;
import guessmarket.client.Server;
import guessmarket.dto.EventSummary;
import guessmarket.dto.OrderSide;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

/**
 * Brings up the real client window against a running server, with a second user trading behind
 * it, walks through its screens and saves a picture of each. Not part of the submission: it is how
 * the screens were checked without clicking through them by hand.
 *
 *   java ... ClientSmoke http://localhost:8080/guess-market test-files out-dir
 */
public class ClientSmoke extends Application {

    private final List<Runnable> steps = new ArrayList<>();
    private Stage stage;
    private Path out;

    @Override
    public void start(Stage stage) throws IOException {
        this.stage = stage;
        List<String> args = getParameters().getRaw();
        String url = args.get(0);
        Path files = Path.of(args.get(1));
        out = Path.of(args.get(2));
        Files.createDirectories(out);

        String suffix = String.valueOf(System.currentTimeMillis() % 100000);
        Server maker = new Server(url);
        maker.login("Avi" + suffix);
        maker.deposit(5000);
        String uploaded = maker.upload("multiple.xml", Files.readAllBytes(files.resolve("ex3/multiple.xml")))
                .eventNames().get(0);
        int earth = idOf(maker, uploaded);
        int cup = earth + 1;
        int rain = earth + 2;
        maker.open(cup);
        maker.open(rain);

        Server me = new Server(url);
        String name = "Dana" + suffix;
        me.login(name);
        me.deposit(300);
        me.buy(rain, 1, 25);
        me.order(cup, 1, OrderSide.BUY, 10, 0.55);
        maker.order(cup, 1, OrderSide.SELL, 4, 0.55);
        maker.order(cup, 2, OrderSide.SELL, 20, 0.47);
        maker.say("Welcome to Guess Market, " + name);

        // The login screen first, refusing a name that is already taken.
        stage.setScene(new Scene(new LoginView(new Server(url), ignored -> { }), 1240, 800));
        stage.show();
        steps.add(() -> {
            ((TextField) stage.getScene().lookup(".text-field")).setText(name);
            ((Button) stage.getScene().lookup(".button")).fire();
        });
        steps.add(() -> snap("0-login-name-taken"));
        steps.add(() -> {
            stage.setTitle("Guess Market — " + name);
            stage.getScene().setRoot(new MainView(new AppContext(me, name)));
        });

        steps.add(() -> select(0, cup));
        steps.add(() -> snap("1-events-order-book"));
        steps.add(() -> scrollDetailToBottom());
        steps.add(() -> snap("1b-events-order-controls"));
        steps.add(() -> select(0, rain));
        steps.add(() -> snap("2-events-lmsr"));
        steps.add(() -> scrollDetailToBottom());
        steps.add(() -> snap("2b-events-lmsr-controls"));
        steps.add(() -> tab(1));
        steps.add(() -> selectRow(2, 0));
        steps.add(() -> snap("3-account"));
        steps.add(() -> tab(2));
        steps.add(() -> snap("4-chat"));
        steps.add(() -> {
            stage.setWidth(760);
            stage.setHeight(520);
            tab(0);
        });
        steps.add(() -> snap("5-small-window"));
        steps.add(() -> {
            System.out.println("SMOKE OK");
            Platform.exit();
        });
        next();
    }

    private void next() {
        if (steps.isEmpty()) {
            return;
        }
        PauseTransition pause = new PauseTransition(Duration.millis(1500));
        pause.setOnFinished(e -> {
            steps.remove(0).run();
            next();
        });
        pause.play();
    }

    private void scrollDetailToBottom() {
        stage.getScene().getRoot().lookupAll(".scroll-pane").forEach(node -> {
            if (node instanceof ScrollPane pane && isShowing(pane)) {
                pane.setVvalue(1.0);
            }
        });
    }

    private void tab(int index) {
        ((TabPane) stage.getScene().lookup(".tab-pane")).getSelectionModel().select(index);
    }

    /** Selects the row of an event in the n-th table currently in the scene graph. */
    @SuppressWarnings("unchecked")
    private void select(int table, int eventId) {
        TableView<EventSummary> view = (TableView<EventSummary>) tables().get(table);
        for (EventSummary row : view.getItems()) {
            if (row.id() == eventId) {
                view.getSelectionModel().select(row);
            }
        }
    }

    private void selectRow(int table, int row) {
        tables().get(table).getSelectionModel().select(row);
    }

    private List<TableView<?>> tables() {
        List<TableView<?>> found = new ArrayList<>();
        stage.getScene().getRoot().lookupAll(".table-view").forEach(node -> {
            if (node instanceof TableView<?> view && view.isVisible() && view.getScene() != null
                    && view.localToScene(view.getBoundsInLocal()).getWidth() > 0 && isShowing(view)) {
                found.add(view);
            }
        });
        return found;
    }

    private static boolean isShowing(javafx.scene.Node node) {
        for (javafx.scene.Node n = node; n != null; n = n.getParent()) {
            if (!n.isVisible()) {
                return false;
            }
        }
        return node.getParent() != null && node.getParent().getScene() != null
                && node.getLayoutBounds().getWidth() > 0;
    }

    private void snap(String name) {
        try {
            WritableImage image = stage.getScene().snapshot(null);
            Files.write(out.resolve(name + ".png"), png(image));
            System.out.println("saved " + name + ".png");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static int idOf(Server server, String eventName) {
        for (EventSummary event : server.events()) {
            if (event.name().equals(eventName)) {
                return event.id();
            }
        }
        throw new IllegalStateException("no event " + eventName);
    }

    // javafx.swing is trimmed out of the vendored SDK, so the picture is encoded here.
    private static byte[] png(WritableImage image) throws IOException {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        PixelReader pixels = image.getPixelReader();
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflate = new DeflaterOutputStream(raw)) {
            for (int y = 0; y < height; y++) {
                deflate.write(0);
                for (int x = 0; x < width; x++) {
                    int argb = pixels.getArgb(x, y);
                    deflate.write(argb >> 16);
                    deflate.write(argb >> 8);
                    deflate.write(argb);
                }
            }
        }
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(file);
        data.write(new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10});
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        DataOutputStream h = new DataOutputStream(header);
        h.writeInt(width);
        h.writeInt(height);
        h.write(new byte[] {8, 2, 0, 0, 0});
        chunk(data, "IHDR", header.toByteArray());
        chunk(data, "IDAT", raw.toByteArray());
        chunk(data, "IEND", new byte[0]);
        return file.toByteArray();
    }

    private static void chunk(DataOutputStream data, String type, byte[] body) throws IOException {
        data.writeInt(body.length);
        byte[] name = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        data.write(name);
        data.write(body);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(body);
        data.writeInt((int) crc.getValue());
    }

    public static void main(String[] args) {
        launch(args);
    }
}
