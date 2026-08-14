package guessmarket.ui;

import guessmarket.dto.CloseResult;
import guessmarket.dto.EventState;
import guessmarket.dto.EventSummary;
import guessmarket.dto.LoadSummary;
import guessmarket.dto.PurchaseResult;
import guessmarket.engine.api.Engine;
import guessmarket.engine.exception.GuessMarketException;
import guessmarket.engine.exception.InvalidFileException;

import java.util.List;

/**
 * Drives the whole application: shows the menu, collects what the user wants, asks the engine to do
 * it, prints the answer. The active side of the pair; the engine never calls back into it, and is
 * only ever reached through the {@link Engine} interface.
 * <p>
 * Nothing a user can type ends the program except the exit command and the input running out.
 */
public class GuessMarketConsole {

    private final Engine engine;
    private final ConsoleReader in;
    private final OutputFormatter out;

    public GuessMarketConsole(Engine engine, ConsoleReader in, OutputFormatter out) {
        this.engine = engine;
        this.in = in;
        this.out = out;
    }

    public void run() {
        out.banner();
        boolean running = true;
        while (running) {
            showMenu();
            int choice = in.readIntInRange(" Enter your choice (1-" + MenuCommand.count() + "): ",
                    1, MenuCommand.count());
            running = handle(MenuCommand.ofMenuNumber(choice));
        }
        out.blank();
        out.info("Goodbye.");
    }

    private void showMenu() {
        if (engine.isLoaded()) {
            out.menu(engine.loadedSourceDescription(), engine.listEvents().size());
        } else {
            out.menu(null, 0);
        }
    }

    private boolean handle(MenuCommand command) {
        switch (command) {
            case LOAD_FILE -> loadFile();
            case SHOW_EVENTS -> guarded(this::showEvents);
            case SHOW_EVENT_STATE -> guarded(this::showEventState);
            case BUY_SHARES -> guarded(this::buyShares);
            case CLOSE_EVENT -> guarded(this::closeEvent);
            case SAVE_STATE -> saveState();
            case RESTORE_STATE -> restoreState();
            case EXIT -> {
                return false;
            }
        }
        return true;
    }

    // The engine's faults are unchecked, so this one catch site covers every command instead of
    // each call needing its own.
    private void guarded(Runnable body) {
        try {
            body.run();
        } catch (GuessMarketException e) {
            out.blank();
            out.error(Messages.describe(e));
        }
    }

    private void loadFile() {
        out.section("LOAD SYSTEM FILE");
        out.info(" Enter the full path of the Guess Market XML file.");
        out.info(" The path may contain spaces, and surrounding quotation marks are ignored.");
        out.blank();
        String path = in.readRequiredLine(" File path: ");

        try {
            LoadSummary summary = engine.loadFromFile(path);
            out.loadSucceeded(summary);
        } catch (InvalidFileException e) {
            out.loadFailed(e.problems(), currentlyLoadedOrNull());
        } catch (GuessMarketException e) {
            out.blank();
            out.error(Messages.describe(e));
        }
    }

    private void showEvents() {
        out.eventList("EVENTS LOADED", engine.listEvents());
    }

    private void showEventState() {
        List<EventSummary> events = engine.listEvents();
        out.eventList("EVENTS LOADED", events);
        int selection = selectFrom("event", events.size());
        if (selection == ConsoleReader.CANCEL) {
            return;
        }
        out.eventState(engine.eventState(selection));
    }

    private void buyShares() {
        List<EventSummary> active = engine.listActiveEvents();
        out.eventList("ACTIVE EVENTS", active);
        int eventNumber = selectFrom("event to take part in", active.size());
        if (eventNumber == ConsoleReader.CANCEL) {
            return;
        }

        EventState state = engine.activeEventState(eventNumber);
        out.eventState(state);

        out.blank();
        int answerNumber = selectFrom("answer you believe in", state.outcomes().size());
        if (answerNumber == ConsoleReader.CANCEL) {
            return;
        }

        long quantity = in.readQuantity(" How many shares do you want to buy (0 to cancel): ");
        if (quantity == ConsoleReader.CANCEL) {
            out.info(" Purchase cancelled.");
            return;
        }

        PurchaseResult result = engine.buyShares(eventNumber, answerNumber, quantity);
        out.purchase(result);
        out.eventState(result.stateAfterPurchase());
    }

    private void closeEvent() {
        List<EventSummary> active = engine.listActiveEvents();
        out.eventList("ACTIVE EVENTS", active);
        int eventNumber = selectFrom("event to close", active.size());
        if (eventNumber == ConsoleReader.CANCEL) {
            return;
        }

        EventState state = engine.activeEventState(eventNumber);
        out.eventState(state);

        out.blank();
        int winningNumber = selectFrom("answer the event ended on", state.outcomes().size());
        if (winningNumber == ConsoleReader.CANCEL) {
            return;
        }

        CloseResult result = engine.closeEvent(eventNumber, winningNumber);
        out.close(result);
        out.eventState(result.stateAfterClose());
    }

    private void saveState() {
        out.section("SAVE SYSTEM STATE");
        if (!engine.isLoaded()) {
            out.error("No system file is loaded yet, so there is nothing to save.");
            return;
        }
        out.info(" Enter the full path and file name to save to, without an extension.");
        out.blank();
        String path = in.readRequiredLine(" Save to: ");
        try {
            String written = engine.saveState(path);
            out.blank();
            out.info(" The system state was saved to:");
            out.info("   " + written);
        } catch (GuessMarketException e) {
            out.error(Messages.describe(e));
        }
    }

    private void restoreState() {
        out.section("LOAD A SAVED SYSTEM STATE");
        out.info(" Enter the full path and file name of the saved state, without an extension.");
        out.blank();
        String path = in.readRequiredLine(" Load from: ");
        try {
            LoadSummary summary = engine.restoreState(path);
            out.blank();
            out.info(" The saved state was loaded: " + summary.eventsLoaded()
                    + " event(s), trade history included.");
            out.info(" Anything loaded before this has been replaced.");
        } catch (GuessMarketException e) {
            out.error(Messages.describe(e));
        }
    }

    // Bounds-checking here saves the engine a pointless round trip, but the engine checks again
    // regardless: it can never rely on a front end having done so.
    private int selectFrom(String what, int itemCount) {
        out.blank();
        return in.readSelection(" Choose the " + what + " (1-" + itemCount
                + ", 0 to go back): ", itemCount);
    }

    private String currentlyLoadedOrNull() {
        return engine.isLoaded() ? engine.loadedSourceDescription() : null;
    }
}
