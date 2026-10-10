package guessmarket.ui;

import guessmarket.engine.exception.GuessMarketException;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.stage.Window;

/** The two things the application ever has to tell the user, and one place that says them. */
public final class Dialogs {

    /** Past this many characters a message goes into a scrolling box rather than the header. */
    private static final int LONG_MESSAGE = 160;

    private Dialogs() {
    }

    public static void failure(Window owner, String title, GuessMarketException failure) {
        show(owner, Alert.AlertType.ERROR, title, Messages.describe(failure));
    }

    public static void failure(Window owner, String title, String message) {
        show(owner, Alert.AlertType.ERROR, title, message);
    }

    public static void note(Window owner, String title, String message) {
        show(owner, Alert.AlertType.INFORMATION, title, message);
    }

    private static void show(Window owner, Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(title);
        if (message.length() > LONG_MESSAGE || message.contains("\n")) {
            TextArea body = new TextArea(message);
            body.setEditable(false);
            body.setWrapText(true);
            body.setPrefRowCount(Math.min(18, message.split("\n").length + 2));
            alert.getDialogPane().setContent(body);
        } else {
            alert.setContentText(message);
        }
        alert.getDialogPane().setPrefWidth(560);
        alert.setResizable(true);
        alert.showAndWait();
    }
}
