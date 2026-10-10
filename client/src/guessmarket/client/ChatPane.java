package guessmarket.client;

import guessmarket.dto.ChatLine;
import guessmarket.dto.ChatLines;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/**
 * Bonus 1: a chat everybody logged in shares. Every line goes through the server, and each client
 * pulls only the lines it has not seen yet, the delta fetch of the course's chat example.
 */
public final class ChatPane extends BorderPane {

    private final ListView<String> lines = new ListView<>();
    private final TextField message = new TextField();
    /** How many lines this client already has. Only touched on the JavaFX thread or read once per poll. */
    private volatile int seen;

    public ChatPane(AppContext context) {
        lines.setPlaceholder(new javafx.scene.control.Label("Nobody has said anything yet."));
        message.setPromptText("Write to everybody and press Enter");
        Button send = new Button("Send");
        HBox.setHgrow(message, Priority.ALWAYS);
        HBox bottom = new HBox(8, message, send);
        bottom.setPadding(new Insets(8, 0, 0, 0));

        Runnable say = () -> {
            String text = message.getText().strip();
            if (text.isEmpty()) {
                return;
            }
            message.clear();
            context.run(getScene().getWindow(), "Chat", () -> {
                context.server().say(text);
                return text;
            }, sent -> { });
        };
        send.setOnAction(event -> say.run());
        message.setOnAction(event -> say.run());

        setPadding(new Insets(10));
        setCenter(lines);
        setBottom(bottom);

        context.poll(() -> context.server().chat(seen), this::append);
    }

    private void append(ChatLines fresh) {
        // Two polls can return the same lines if one was asked for before the other arrived.
        if (fresh.version() <= seen) {
            return;
        }
        int skip = fresh.lines().size() - (fresh.version() - seen);
        for (int i = Math.max(0, skip); i < fresh.lines().size(); i++) {
            ChatLine line = fresh.lines().get(i);
            lines.getItems().add(Format.time(line.timeMillis()) + "  " + line.userName() + ":  " + line.text());
        }
        seen = fresh.version();
        lines.scrollTo(lines.getItems().size() - 1);
    }
}
