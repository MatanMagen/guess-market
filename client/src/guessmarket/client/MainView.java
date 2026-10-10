package guessmarket.client;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;

/**
 * The window once logged in: a bar along the top saying who you are, what you hold and whether
 * the server is answering, and the screens of the exercise's sketch underneath — the events, your
 * account, and the chat of bonus 1.
 */
public final class MainView extends BorderPane {

    private final Label balance = new Label();
    private final Label connection = new Label();

    public MainView(AppContext context) {
        EventsTab eventsTab = new EventsTab(context);
        AccountTab accountTab = new AccountTab(context);
        ChatPane chat = new ChatPane(context);

        setTop(topBar(context));

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().add(new Tab("Events", eventsTab));
        tabs.getTabs().add(new Tab("Account", accountTab));
        tabs.getTabs().add(new Tab("Chat", chat));
        setCenter(tabs);

        connection.textProperty().bind(context.connectionProblemProperty());

        // The user's own details feed both the bar up here and the account screen, so they are
        // pulled once for the two.
        context.poll(() -> context.server().me(), me -> {
            balance.setText("Balance " + Format.money(me.balance()) + (me.blocked() ? "  (blocked)" : ""));
            accountTab.showMe(me);
        });
        context.startPolling();
    }

    private HBox topBar(AppContext context) {
        Label title = new Label("Guess Market");
        title.setFont(Font.font(title.getFont().getFamily(), 20));
        Label who = new Label("Logged in as " + context.userName());
        balance.setFont(Font.font(balance.getFont().getFamily(), 14));
        connection.setStyle("-fx-text-fill: firebrick;");
        connection.setWrapText(true);
        connection.setMinWidth(0);
        HBox.setHgrow(connection, Priority.ALWAYS);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.SOMETIMES);

        HBox bar = new HBox(16, title, who, connection, spacer, balance);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10));
        return bar;
    }
}
