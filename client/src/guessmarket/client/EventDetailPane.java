package guessmarket.ui;

import guessmarket.dto.EventLifecycle;
import guessmarket.dto.EventState;
import guessmarket.dto.FillRecord;
import guessmarket.dto.HoldingState;
import guessmarket.dto.LmsrDetails;
import guessmarket.dto.MarketMethod;
import guessmarket.dto.OrderBookDetails;
import guessmarket.dto.OrderResult;
import guessmarket.dto.OrderSide;
import guessmarket.dto.OrderView;
import guessmarket.dto.OutcomeBook;
import guessmarket.dto.OutcomeState;
import guessmarket.dto.ParticipantState;
import guessmarket.dto.PurchaseResult;
import guessmarket.dto.TradeRecord;
import guessmarket.engine.exception.GuessMarketException;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

import java.util.List;
import java.util.Locale;

/**
 * One event in full, and the controls to act on it.
 * <p>
 * Used by both screens: the events screen shows it for whatever event is selected there, and the
 * user screen shows it for the event a user has drilled into. What it offers depends on who is
 * acting — only the market maker is given the open and close buttons.
 */
public final class EventDetailPane extends VBox {

    private static final double LABEL_COLUMN = 150;

    private final AppContext context;
    private final ScrollPane scroller = new ScrollPane();
    private final VBox body = new VBox(12);

    private EventState state;

    public EventDetailPane(AppContext context) {
        this.context = context;
        setSpacing(0);
        body.setPadding(new Insets(12));
        scroller.setContent(body);
        scroller.setFitToWidth(true);
        VBox.setVgrow(scroller, Priority.ALWAYS);
        getChildren().add(scroller);
        showNothing();
    }

    public void showNothing() {
        state = null;
        body.getChildren().setAll(hint("Select an event to see its details."));
    }

    public void show(int eventId) {
        try {
            state = context.engine().eventState(eventId);
        } catch (GuessMarketException failure) {
            showNothing();
            return;
        }
        rebuild();
    }

    /** Redraws whatever is already on screen, after something changed it. */
    public void refresh() {
        if (state != null) {
            show(state.summary().id());
        }
    }

    public Integer shownEventId() {
        return state == null ? null : state.summary().id();
    }

    private void rebuild() {
        body.getChildren().setAll(header(), new Separator());
        if (state.summary().method() == MarketMethod.LMSR) {
            body.getChildren().add(lmsrSection(state.lmsr()));
        } else {
            body.getChildren().add(orderBookSection(state.orderBook()));
        }
        body.getChildren().add(new Separator());
        body.getChildren().add(participantsSection());
        body.getChildren().add(new Separator());
        body.getChildren().add(actionsSection());
    }

    private Node header() {
        GridPane grid = labelledGrid();
        Label title = new Label(state.summary().name());
        title.setFont(Font.font(title.getFont().getFamily(), 18));
        title.setWrapText(true);

        Label description = new Label(state.summary().description());
        description.setWrapText(true);

        int row = 0;
        grid.add(title, 0, row++, 2, 1);
        grid.add(description, 0, row++, 2, 1);
        row = addRow(grid, row, "Status", Format.status(state.summary().status()));
        row = addRow(grid, row, "Traded by", Format.method(state.summary().method()));
        row = addRow(grid, row, "Commission",
                state.summary().commissionPercent() + "% " + Format.commission(state.summary().commissionType()));
        row = addRow(grid, row, "Market maker", state.summary().marketMakerName());
        row = addRow(grid, row, "Event account", Format.money(state.summary().accountBalance()));
        if (state.winningOutcomeName() != null) {
            addRow(grid, row, "Winning answer", state.winningOutcomeName());
        }
        return grid;
    }

    // ---------- lmsr ----------

    private Node lmsrSection(LmsrDetails details) {
        VBox box = new VBox(10);
        box.getChildren().add(sectionTitle("Answers"));

        TableView<OutcomeState> outcomes = Tables.table("No answers.");
        outcomes.getColumns().add(Tables.column("#", 40, o -> String.valueOf(o.displayNumber())));
        outcomes.getColumns().add(Tables.column("Answer", 220, OutcomeState::name));
        outcomes.getColumns().add(Tables.numberColumn("Price", 90, o -> Format.price(o.price())));
        outcomes.getColumns().add(Tables.numberColumn("Chance", 90, o -> Format.percent(o.price())));
        outcomes.getColumns().add(Tables.numberColumn("Shares bought", 120,
                o -> Format.quantity(o.sharesBought())));
        outcomes.setItems(FXCollections.observableArrayList(details.outcomes()));
        fixedHeight(outcomes, details.outcomes().size());
        box.getChildren().add(outcomes);

        GridPane money = labelledGrid();
        int row = 0;
        row = addRow(money, row, "Liquidity b", String.valueOf(details.liquidity()));
        row = addRow(money, row, "Opening subsidy", Format.money(details.openingSubsidy()));
        row = addRow(money, row, "Maker net result", Format.signedMoney(details.marketMakerNetResult()));
        addRow(money, row, "Commission taken", Format.money(details.commissionCollected()));
        box.getChildren().add(money);

        box.getChildren().add(sectionTitle("Trade history, newest first"));
        TableView<TradeRecord> history = Tables.table("Nothing has been traded yet.");
        history.getColumns().add(Tables.column("User", 120, TradeRecord::userName));
        history.getColumns().add(Tables.column("Answer", 180, TradeRecord::outcomeName));
        history.getColumns().add(Tables.numberColumn("Shares", 90, t -> Format.quantity(t.quantity())));
        history.getColumns().add(Tables.numberColumn("Price paid", 110, t -> Format.money(t.sharesCost())));
        history.getColumns().add(Tables.numberColumn("Commission", 110, t -> Format.money(t.commissionPaid())));
        history.getColumns().add(Tables.numberColumn("Total", 110, t -> Format.money(t.totalPaid())));
        history.setItems(FXCollections.observableArrayList(details.tradeHistory()));
        history.setPrefHeight(180);
        box.getChildren().add(history);
        return box;
    }

    // ---------- order book ----------

    private Node orderBookSection(OrderBookDetails details) {
        VBox box = new VBox(10);
        GridPane settings = labelledGrid();
        int row = 0;
        row = addRow(settings, row, "Base value d", Format.money(details.basePrice()));
        row = addRow(settings, row, "Minting", details.mintAllowed() ? "Allowed" : "Not allowed");
        row = addRow(settings, row, "Initial investment", Format.money(details.initialInvestment()));
        addRow(settings, row, "Commission taken", Format.money(details.commissionCollected()));
        box.getChildren().add(settings);

        HBox books = new HBox(12);
        for (OutcomeBook book : details.books()) {
            Node pane = bookPane(book);
            HBox.setHgrow(pane, Priority.ALWAYS);
            books.getChildren().add(pane);
        }
        box.getChildren().add(books);
        return box;
    }

    private Node bookPane(OutcomeBook book) {
        VBox pane = new VBox(8);
        pane.setStyle("-fx-border-color: -fx-box-border; -fx-border-radius: 4; -fx-padding: 8;");
        pane.getChildren().add(sectionTitle(book.outcomeName()));

        FlowPane stats = new FlowPane(14, 4);
        stats.getChildren().add(stat("Last", Format.price(book.lastTradePrice())));
        stats.getChildren().add(stat("Bid", Format.price(book.bestBid())));
        stats.getChildren().add(stat("Ask", Format.price(book.bestAsk())));
        stats.getChildren().add(stat("Mid", Format.price(book.mid())));
        stats.getChildren().add(stat("Spread", Format.price(book.spread())));
        stats.getChildren().add(stat("Outstanding", Format.quantity(book.sharesOutstanding())));
        pane.getChildren().add(stats);

        pane.getChildren().add(new Label("Bids (buy), best first"));
        pane.getChildren().add(orderTable(book.bids(), "No buyers."));
        pane.getChildren().add(new Label("Asks (sell), best first"));
        pane.getChildren().add(orderTable(book.asks(), "No sellers."));
        return pane;
    }

    private TableView<OrderView> orderTable(List<OrderView> orders, String empty) {
        TableView<OrderView> table = Tables.table(empty);
        table.getColumns().add(Tables.column("User", 110, OrderView::userName));
        table.getColumns().add(Tables.numberColumn("Shares", 80, o -> Format.quantity(o.quantity())));
        table.getColumns().add(Tables.numberColumn("Price", 80, o -> Format.price(o.price())));
        table.setItems(FXCollections.observableArrayList(orders));
        table.setPrefHeight(140);
        table.setMinHeight(90);
        return table;
    }

    // ---------- participants ----------

    private Node participantsSection() {
        VBox box = new VBox(8);
        box.getChildren().add(sectionTitle("Participants"));
        TableView<ParticipantState> table = Tables.table("Nobody has taken part yet.");
        table.getColumns().add(Tables.column("User", 130, ParticipantState::userName));
        for (int i = 0; i < state.summary().outcomeNames().size(); i++) {
            final int index = i;
            String answer = state.summary().outcomeNames().get(i);
            table.getColumns().add(Tables.numberColumn(answer + " held", 120,
                    p -> Format.quantity(holding(p, index).quantity())));
            table.getColumns().add(Tables.numberColumn(answer + " paid", 120,
                    p -> Format.money(holding(p, index).amountPaid())));
        }
        table.getColumns().add(Tables.numberColumn("Resting orders", 110,
                p -> Format.quantity(p.restingOrders())));
        table.setItems(FXCollections.observableArrayList(state.participants()));
        table.setPrefHeight(170);
        box.getChildren().add(table);
        return box;
    }

    private HoldingState holding(ParticipantState participant, int index) {
        return index < participant.holdings().size()
                ? participant.holdings().get(index)
                : new HoldingState("", 0L, 0.0d);
    }

    // ---------- acting on the event ----------

    private Node actionsSection() {
        VBox box = new VBox(10);
        String actor = context.actingUser();
        if (actor == null) {
            box.getChildren().add(hint("Choose who you are acting as to trade."));
            return box;
        }
        box.getChildren().add(sectionTitle("Acting as " + actor));

        if (state.summary().marketMakerName().equals(actor)) {
            box.getChildren().add(marketMakerControls());
        }
        if (state.summary().status() == EventLifecycle.ACTIVE) {
            box.getChildren().add(state.summary().method() == MarketMethod.LMSR
                    ? lmsrTradeControls()
                    : orderControls());
        } else {
            box.getChildren().add(hint(state.summary().status() == EventLifecycle.NOT_STARTED
                    ? "The event has not been started, so it cannot be traded."
                    : "The event is closed."));
        }
        return box;
    }

    private Node marketMakerControls() {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);

        Button open = new Button("Start event");
        open.setDisable(state.summary().status() != EventLifecycle.NOT_STARTED);
        open.setOnAction(e -> run(() -> {
            context.engine().openEvent(state.summary().id(), context.actingUser());
            context.refreshAll();
        }));

        ComboBox<String> winner = new ComboBox<>(
                FXCollections.observableArrayList(state.summary().outcomeNames()));
        winner.setPromptText("Winning answer");
        Button close = new Button("Close event");
        close.setDisable(state.summary().status() != EventLifecycle.ACTIVE);
        close.setOnAction(e -> {
            int chosen = winner.getSelectionModel().getSelectedIndex();
            if (chosen < 0) {
                Dialogs.failure(getScene().getWindow(), "Close event", "Choose the winning answer first.");
                return;
            }
            run(() -> {
                var result = context.engine().closeEvent(state.summary().id(), context.actingUser(), chosen + 1);
                context.refreshAll();
                Dialogs.note(getScene().getWindow(), "Event closed", closeSummary(result.winningOutcomeName(),
                        result.winningShares(), result.netPaidToWinners(),
                        result.commissionCharged(), result.subsidyReturned()));
            });
        });

        row.getChildren().add(open);
        row.getChildren().add(new Separator(javafx.geometry.Orientation.VERTICAL));
        row.getChildren().add(winner);
        row.getChildren().add(close);
        return row;
    }

    private String closeSummary(String winner, long shares, double paid, double commission, double returned) {
        StringBuilder text = new StringBuilder();
        text.append(winner).append(" wins.\n");
        text.append(Format.quantity(shares)).append(" winning share(s) paid ")
                .append(Format.money(paid)).append(" in total.\n");
        text.append("Commission taken: ").append(Format.money(commission)).append(".");
        if (returned > 0) {
            text.append("\nSubsidy returned to the market maker: ").append(Format.money(returned)).append(".");
        }
        return text.toString();
    }

    private Node lmsrTradeControls() {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        ComboBox<String> answer = new ComboBox<>(
                FXCollections.observableArrayList(state.summary().outcomeNames()));
        answer.getSelectionModel().selectFirst();
        Spinner<Integer> quantity = new Spinner<>(1, 1_000_000, 10);
        quantity.setEditable(true);
        quantity.setPrefWidth(110);
        Label quote = new Label();

        Runnable requote = () -> {
            int index = answer.getSelectionModel().getSelectedIndex();
            if (index < 0) {
                quote.setText("");
                return;
            }
            try {
                double cost = context.engine().quoteLmsrPurchase(
                        state.summary().id(), index + 1, quantity.getValue());
                double commission = cost * state.summary().commissionPercent() / 100.0d;
                quote.setText(state.summary().commissionType() == guessmarket.dto.CommissionPolicy.ON_PURCHASE
                        ? "Costs " + Format.money(cost) + " plus " + Format.money(commission) + " commission"
                        : "Costs " + Format.money(cost));
            } catch (GuessMarketException failure) {
                quote.setText("");
            }
        };
        answer.valueProperty().addListener((observable, was, now) -> requote.run());
        quantity.valueProperty().addListener((observable, was, now) -> requote.run());
        requote.run();

        Button buy = new Button("Buy");
        buy.setOnAction(e -> run(() -> {
            PurchaseResult result = context.engine().buyLmsrShares(state.summary().id(),
                    context.actingUser(), answer.getSelectionModel().getSelectedIndex() + 1,
                    quantity.getValue());
            context.refreshAll();
            Dialogs.note(getScene().getWindow(), "Shares bought",
                    "Bought " + Format.quantity(result.quantity()) + " of " + result.outcomeName()
                            + " for " + Format.money(result.sharesCost())
                            + (result.commissionCharged()
                                    ? " plus " + Format.money(result.commissionPaid()) + " commission" : "")
                            + ".\nTotal paid: " + Format.money(result.totalPaid()) + ".");
        }));

        row.getChildren().add(new Label("Answer"));
        row.getChildren().add(answer);
        row.getChildren().add(new Label("Shares"));
        row.getChildren().add(quantity);
        row.getChildren().add(buy);
        row.getChildren().add(quote);
        return row;
    }

    private Node orderControls() {
        OrderBookDetails details = state.orderBook();
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);

        ComboBox<String> answer = new ComboBox<>(
                FXCollections.observableArrayList(state.summary().outcomeNames()));
        answer.getSelectionModel().selectFirst();
        ComboBox<OrderSide> side = new ComboBox<>(
                FXCollections.observableArrayList(OrderSide.BUY, OrderSide.SELL));
        side.getSelectionModel().selectFirst();
        Spinner<Integer> quantity = new Spinner<>(1, 1_000_000, 10);
        quantity.setEditable(true);
        quantity.setPrefWidth(110);
        TextField price = new TextField("0.50");
        price.setPrefWidth(90);
        Label bounds = new Label(String.format(Locale.US, "between %.2f and %.2f",
                0.01d, details.basePrice() - 0.01d));

        Button submit = new Button("Submit order");
        submit.setOnAction(e -> {
            double asked;
            try {
                asked = Double.parseDouble(price.getText().trim());
            } catch (NumberFormatException bad) {
                Dialogs.failure(getScene().getWindow(), "Submit order",
                        "'" + price.getText() + "' is not a price.");
                return;
            }
            run(() -> {
                OrderResult result = context.engine().submitOrder(state.summary().id(),
                        context.actingUser(), answer.getSelectionModel().getSelectedIndex() + 1,
                        side.getValue(), quantity.getValue(), asked);
                context.refreshAll();
                Dialogs.note(getScene().getWindow(), "Order submitted", orderSummary(result));
            });
        });

        row.getChildren().add(new Label("Answer"));
        row.getChildren().add(answer);
        row.getChildren().add(side);
        row.getChildren().add(new Label("Shares"));
        row.getChildren().add(quantity);
        row.getChildren().add(new Label("Price"));
        row.getChildren().add(price);
        row.getChildren().add(submit);
        row.getChildren().add(bounds);
        return row;
    }

    private String orderSummary(OrderResult result) {
        StringBuilder text = new StringBuilder();
        if (result.quantityFilled() == 0) {
            text.append("Nothing matched, so all ")
                    .append(Format.quantity(result.quantityResting()))
                    .append(" share(s) are resting in the book.");
            return text.toString();
        }
        text.append("Filled ").append(Format.quantity(result.quantityFilled()))
                .append(" of ").append(Format.quantity(result.quantitySubmitted())).append(" share(s).\n");
        for (FillRecord fill : result.fills()) {
            text.append("\n  • ");
            if (fill.sellerName() == null) {
                text.append("minted ").append(Format.quantity(fill.quantity()))
                        .append(" ").append(fill.outcomeName())
                        .append(" for ").append(fill.buyerName())
                        .append(" at ").append(Format.price(fill.price()));
            } else {
                text.append(Format.quantity(fill.quantity())).append(" ").append(fill.outcomeName())
                        .append(" from ").append(fill.sellerName())
                        .append(" to ").append(fill.buyerName())
                        .append(" at ").append(Format.price(fill.price()));
            }
        }
        if (result.quantityResting() > 0) {
            text.append("\n\n").append(Format.quantity(result.quantityResting()))
                    .append(" share(s) left resting in the book.");
        }
        if (result.cashSpent() > 0) {
            text.append("\nPaid ").append(Format.money(result.cashSpent()));
            if (result.commissionPaid() > 0) {
                text.append(" plus ").append(Format.money(result.commissionPaid())).append(" commission");
            }
            text.append(".");
        }
        if (result.cashReceived() > 0) {
            text.append("\nReceived ").append(Format.money(result.cashReceived())).append(".");
        }
        return text.toString();
    }

    /** One catch site covers every action, because they all fail the same way. */
    private void run(Runnable action) {
        try {
            action.run();
        } catch (GuessMarketException failure) {
            Dialogs.failure(getScene().getWindow(), "Cannot do that", failure);
        }
    }

    // ---------- small pieces ----------

    private GridPane labelledGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(4);
        ColumnConstraints labels = new ColumnConstraints(LABEL_COLUMN);
        ColumnConstraints values = new ColumnConstraints();
        values.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().add(labels);
        grid.getColumnConstraints().add(values);
        return grid;
    }

    private int addRow(GridPane grid, int row, String name, String value) {
        Label key = new Label(name);
        key.setStyle("-fx-text-fill: -fx-text-inner-color; -fx-opacity: 0.7;");
        Label shown = new Label(value);
        shown.setWrapText(true);
        grid.add(key, 0, row);
        grid.add(shown, 1, row);
        return row + 1;
    }

    private Label sectionTitle(String text) {
        Label label = new Label(text);
        label.setFont(Font.font(label.getFont().getFamily(), 14));
        return label;
    }

    private Node stat(String name, String value) {
        Label label = new Label(name + " " + value);
        label.setStyle("-fx-background-color: -fx-control-inner-background-alt; -fx-padding: 2 6 2 6;");
        return label;
    }

    private Label hint(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setStyle("-fx-opacity: 0.7;");
        return label;
    }

    private void fixedHeight(TableView<?> table, int rows) {
        double height = 28 + rows * 26.0;
        table.setPrefHeight(height);
        table.setMinHeight(Region.USE_PREF_SIZE);
        table.setMaxHeight(height);
    }
}
