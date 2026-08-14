package guessmarket.ui;

/**
 * The main menu, in screen order. A constant's position in the enum is its menu number, so the
 * printed menu and the dispatcher cannot drift apart.
 */
public enum MenuCommand {

    LOAD_FILE("Load system file"),
    SHOW_EVENTS("Show events"),
    SHOW_EVENT_STATE("Show the trading state of an event"),
    BUY_SHARES("Participate in an event (buy shares)"),
    CLOSE_EVENT("Close an event"),
    SAVE_STATE("Save the system state to a file"),
    RESTORE_STATE("Load a saved system state"),
    EXIT("Exit");

    private final String label;

    MenuCommand(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public int menuNumber() {
        return ordinal() + 1;
    }

    public static MenuCommand ofMenuNumber(int menuNumber) {
        int index = menuNumber - 1;
        if (index < 0 || index >= values().length) {
            throw new IllegalArgumentException("no menu command number " + menuNumber);
        }
        return values()[index];
    }

    public static int count() {
        return values().length;
    }
}
