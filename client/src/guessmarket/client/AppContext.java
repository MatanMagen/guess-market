package guessmarket.client;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.stage.Window;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * What every screen needs: the server, who is logged in, and the two ways of talking to the
 * server without freezing the window.
 * <p>
 * <b>Pull.</b> Screens register what they want kept fresh with {@link #poll}. Every half second
 * one background thread fetches each of them in turn and hands the answers to the JavaFX thread —
 * the "fetch all" pull of the course's chat example. A round never overlaps the previous one.
 * <p>
 * <b>Actions.</b> Anything the user does goes through {@link #run}: the request is made on a
 * worker thread, the answer is shown on the JavaFX thread, and the screens are refreshed at once
 * rather than at the next tick.
 */
public final class AppContext {

    /** Half a second, which the exercise names as a reasonable pull interval. */
    private static final long POLL_MILLIS = 500;

    private final Server server;
    private final String userName;
    private final List<Runnable> polls = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor(daemon("poller"));
    private final ExecutorService workers = Executors.newCachedThreadPool(daemon("request"));
    private final ReadOnlyStringWrapper connectionProblem = new ReadOnlyStringWrapper("");

    public AppContext(Server server, String userName) {
        this.server = server;
        this.userName = userName;
    }

    public Server server() {
        return server;
    }

    public String userName() {
        return userName;
    }

    /** Empty while the server answers; otherwise why it did not. */
    public ReadOnlyStringProperty connectionProblemProperty() {
        return connectionProblem.getReadOnlyProperty();
    }

    /**
     * Keeps something fresh. {@code fetch} runs on the poller thread and may return null to mean
     * "nothing to show right now"; {@code show} runs on the JavaFX thread with whatever it returned.
     */
    public <T> void poll(Supplier<T> fetch, Consumer<T> show) {
        polls.add(() -> {
            T value = fetch.get();
            Platform.runLater(() -> show.accept(value));
        });
    }

    public void startPolling() {
        poller.scheduleWithFixedDelay(this::pollOnce, 0, POLL_MILLIS, TimeUnit.MILLISECONDS);
    }

    /** Asks for a round straight away, after the user changed something. */
    public void refreshNow() {
        poller.execute(this::pollOnce);
    }

    /**
     * Makes one request for the user. A refusal is shown as an error over {@code owner} and
     * {@code onSuccess} is not called.
     */
    public <T> void run(Window owner, String title, Supplier<T> request, Consumer<T> onSuccess) {
        workers.execute(() -> {
            try {
                T result = request.get();
                Platform.runLater(() -> {
                    onSuccess.accept(result);
                    refreshNow();
                });
            } catch (ServerException e) {
                Platform.runLater(() -> Dialogs.failure(owner, title, e.getMessage()));
            }
        });
    }

    /** Like {@link #run}, for a lookup the user did not ask for outright: a refusal is simply not shown. */
    public <T> void fetch(Supplier<T> request, Consumer<T> onSuccess) {
        workers.execute(() -> {
            try {
                T result = request.get();
                Platform.runLater(() -> onSuccess.accept(result));
            } catch (ServerException ignored) {
                // a quote for a quantity the server refuses just shows nothing
            }
        });
    }

    private void pollOnce() {
        String problem = "";
        for (Runnable poll : polls) {
            try {
                poll.run();
            } catch (ServerException e) {
                problem = e.getMessage();
            } catch (RuntimeException e) {
                // A scheduled task that throws is never run again, so nothing may escape.
                problem = String.valueOf(e);
            }
        }
        String finalProblem = problem;
        Platform.runLater(() -> connectionProblem.set(finalProblem));
    }

    private static ThreadFactory daemon(String name) {
        return task -> {
            Thread thread = new Thread(task, "guess-market-" + name);
            thread.setDaemon(true);
            return thread;
        };
    }
}
