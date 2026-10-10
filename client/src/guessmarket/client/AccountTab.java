package guessmarket.client;

import guessmarket.dto.AccountEntry;
import guessmarket.dto.EventLifecycle;
import guessmarket.dto.HoldingState;
import guessmarket.dto.LoadSummary;
import guessmarket.dto.MarketMethod;
import guessmarket.dto.TradeRecord;
import guessmarket.dto.UserDetails;
import guessmarket.dto.UserParticipation;
import guessmarket.dto.UserSummary;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The logged in user's own screen, laid out as the exercise's sketch has it: uploading a file
 * along the top, every user on the left, and on the right this user's balance and money loading,
 * every line of his account, the events he runs or trades in, and the one he has drilled into.
 * <p>
 * Everything here is about the logged in user only. Of the others he sees just the three things
 * the exercise allows: name, balance, and whether they are a market maker.
 */
public final class AccountTab extends BorderPane {

    private final AppContext context;
    private final TableView<UserSummary> users = Tables.table("Nobody else is logged in yet.");
    private final TableView<AccountEntry> ledger = Tables.table("No money has moved yet. Load funds to start.");
    private final TableView<UserParticipation> participations =
            Tables.table("You are not running or trading in any event yet.");
    private final EventDetailPane detail;

    private final Label filePath = new Label("No file chosen");
    private final Button loadFile = new Button("Load file...");
    private final ProgressIndicator uploading = new ProgressIndicator();

    private final Label balance = new Label();
    private final Label blocked = new Label();
    private final TextField amount = new TextField();
    private final VBox involvement = new VBox(8);

    private boolean refilling;
    private UserParticipation shownParticipation;

    public AccountTab(AppContext context) {
        this.context = context;
        this.detail = new EventDetailPane(context);

        setTop(fileRow());

        users.getColumns().add(Tables.column("User", 150, UserSummary::name));
        users.getColumns().add(Tables.numberColumn("Balance", 120, u -> Format.money(u.balance())));
        users.getColumns().add(Tables.column("Market maker", 110, u -> u.marketMaker() ? "Yes" : "No"));
        VBox.setVgrow(users, Priority.ALWAYS);
        VBox left = new VBox(8, sectionTitle("Users"), users);
        left.setPadding(new Insets(10));
        left.setMinWidth(260);

        ledger.getColumns().add(Tables.numberColumn("#", 50, e -> String.valueOf(e.number())));
        ledger.getColumns().add(Tables.column("Time", 80, e -> Format.time(e.timeMillis())));
        ledger.getColumns().add(Tables.column("What", 230, e -> Format.entry(e.kind())));
        ledger.getColumns().add(Tables.column("Event", 170, e -> e.eventName() == null ? "" : e.eventName()));
        ledger.getColumns().add(Tables.numberColumn("Amount", 100, e -> Format.signedMoney(e.amount())));
        ledger.getColumns().add(Tables.numberColumn("Balance after", 110, e -> Format.money(e.balanceAfter())));
        ledger.setPrefHeight(200);
        ledger.setMinHeight(120);

        participations.getColumns().add(Tables.column("Event", 190, UserParticipation::eventName));
        participations.getColumns().add(Tables.column("Method", 100, p -> Format.method(p.method())));
        participations.getColumns().add(Tables.column("Status", 100, p -> Format.status(p.status())));
        participations.getColumns().add(Tables.column("Role", 110, p -> p.marketMaker() ? "Market maker" : "Trader"));
        participations.getColumns().add(Tables.numberColumn("Commission paid", 130,
                p -> Format.money(p.commissionPaid())));
        participations.getColumns().add(Tables.numberColumn("Profit / loss", 120,
                p -> p.status() == EventLifecycle.CLOSED ? Format.signedMoney(p.profitOrLoss()) : "—"));
        participations.setPrefHeight(170);
        participations.setMinHeight(110);
        participations.getSelectionModel().selectedItemProperty().addListener((observable, was, now) -> {
            if (refilling) {
                return;
            }
            showParticipation(now);
        });

        VBox right = new VBox(10);
        right.setPadding(new Insets(12));
        right.getChildren().add(headline());
        right.getChildren().add(fundsRow());
        right.getChildren().add(sectionTitle("Your account, newest line first"));
        right.getChildren().add(ledger);
        right.getChildren().add(new Separator());
        right.getChildren().add(sectionTitle("Events you run or take part in"));
        right.getChildren().add(participations);
        right.getChildren().add(involvement);
        right.getChildren().add(new Separator());
        detail.setMinHeight(420);
        right.getChildren().add(detail);

        ScrollPane scroller = new ScrollPane(right);
        scroller.setFitToWidth(true);

        SplitPane split = new SplitPane(left, scroller);
        split.setDividerPositions(0.28);
        setCenter(split);

        context.poll(() -> context.server().users(), rows -> Tables.refill(users, rows, UserSummary::name));
    }

    // ---------- uploading ----------

    private Node fileRow() {
        loadFile.setOnAction(event -> chooseAndUpload());
        uploading.setVisible(false);
        uploading.setPrefSize(22, 22);
        filePath.setStyle("-fx-opacity: 0.8;");
        filePath.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(filePath, Priority.ALWAYS);

        HBox row = new HBox(10, loadFile, uploading, filePath);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 10, 0, 10));
        return row;
    }

    private void chooseAndUpload() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a Guess Market file to upload");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Guess Market files", "*.xml"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("All files", "*.*"));
        File chosen = chooser.showOpenDialog(getScene().getWindow());
        if (chosen == null) {
            return;
        }
        filePath.setText(chosen.getAbsolutePath());
        loadFile.setDisable(true);
        uploading.setVisible(true);
        // Reading the file and sending it both happen off the JavaFX thread; the upload is
        // asynchronous by nature now, so no pause is staged to show progress.
        context.run(getScene().getWindow(), "Upload failed", () -> {
            try {
                return context.server().upload(chosen.getName(), Files.readAllBytes(chosen.toPath()));
            } catch (IOException e) {
                throw new ServerException(0, "The file " + chosen.getAbsolutePath() + " could not be read: "
                        + e.getMessage());
            } finally {
                javafx.application.Platform.runLater(() -> {
                    loadFile.setDisable(false);
                    uploading.setVisible(false);
                });
            }
        }, this::uploaded);
    }

    private void uploaded(LoadSummary summary) {
        Dialogs.note(getScene().getWindow(), "File loaded",
                "'" + summary.fileName() + "' was loaded and added " + summary.eventNames().size()
                        + " event(s) to the system:\n\n  • " + String.join("\n  • ", summary.eventNames())
                        + "\n\nYou are the market maker of all of them. They have not been started: "
                        + "start each one from its details when you are ready to fund it.");
    }

    // ---------- the user's own account ----------

    private Node headline() {
        Label name = new Label(context.userName());
        name.setFont(Font.font(name.getFont().getFamily(), 18));
        balance.setFont(Font.font(balance.getFont().getFamily(), 16));
        blocked.setStyle("-fx-text-fill: firebrick;");
        blocked.setWrapText(true);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(16, name, blocked, spacer, balance);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Node fundsRow() {
        amount.setPromptText("Amount");
        amount.setPrefWidth(120);
        Button load = new Button("Load funds");
        load.setOnAction(event -> loadFunds());
        amount.setOnAction(event -> loadFunds());
        FlowPane row = new FlowPane(8, 6, new Label("Load money into your account:"), amount, load);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private void loadFunds() {
        String typed = amount.getText().trim();
        double value;
        try {
            value = Double.parseDouble(typed);
        } catch (NumberFormatException e) {
            Dialogs.failure(getScene().getWindow(), "Load funds", "'" + typed + "' is not an amount of money.");
            return;
        }
        context.run(getScene().getWindow(), "Load funds", () -> context.server().deposit(value), details -> {
            amount.clear();
            showMe(details);
        });
    }

    /** Called with a fresh copy of the user's own details every half second. */
    public void showMe(UserDetails me) {
        balance.setText("Balance " + Format.money(me.balance()));
        blocked.setText(me.blocked()
                ? "BLOCKED — your balance went below zero. Load funds to trade again." : "");

        List<AccountEntry> newestFirst = new ArrayList<>(me.ledger());
        java.util.Collections.reverse(newestFirst);
        Tables.refill(ledger, newestFirst, AccountEntry::number);

        refilling = true;
        try {
            Tables.refill(participations, me.participations(), UserParticipation::eventId);
        } finally {
            refilling = false;
        }
        showParticipation(participations.getSelectionModel().getSelectedItem());
    }

    private void showParticipation(UserParticipation participation) {
        if (Objects.equals(participation, shownParticipation)) {
            return;
        }
        shownParticipation = participation;
        if (participation == null) {
            involvement.getChildren().clear();
            detail.showNothing();
            return;
        }
        involvement.getChildren().setAll(involvementOf(participation));
        detail.show(participation.eventId());
    }

    /** What his involvement in one event looks like, in whichever terms that event trades. */
    private Node involvementOf(UserParticipation participation) {
        VBox box = new VBox(8);
        if (participation.method() == MarketMethod.LMSR) {
            box.getChildren().add(sectionTitle("Your trades on this event, newest first"));
            TableView<TradeRecord> trades = Tables.table("You have not traded here yet.");
            trades.getColumns().add(Tables.column("Answer", 180, TradeRecord::outcomeName));
            trades.getColumns().add(Tables.numberColumn("Shares", 90, t -> Format.quantity(t.quantity())));
            trades.getColumns().add(Tables.numberColumn("Price paid", 110, t -> Format.money(t.sharesCost())));
            trades.getColumns().add(Tables.numberColumn("Commission", 110, t -> Format.money(t.commissionPaid())));
            trades.setItems(FXCollections.observableArrayList(participation.lmsrTrades()));
            trades.setPrefHeight(150);
            box.getChildren().add(trades);
        } else {
            box.getChildren().add(sectionTitle("What you hold here"));
            TableView<HoldingState> holdings = Tables.table("You hold nothing.");
            holdings.getColumns().add(Tables.column("Answer", 180, HoldingState::outcomeName));
            holdings.getColumns().add(Tables.numberColumn("Shares", 100, h -> Format.quantity(h.quantity())));
            holdings.getColumns().add(Tables.numberColumn("Paid for them", 130, h -> Format.money(h.amountPaid())));
            holdings.setItems(FXCollections.observableArrayList(participation.holdings()));
            holdings.setPrefHeight(110);
            box.getChildren().add(holdings);
            box.getChildren().add(new Label("Commission paid: " + Format.money(participation.commissionPaid())));
        }
        if (participation.winningOutcomeName() != null) {
            box.getChildren().add(new Label(String.format(Locale.US, "Closed on '%s'. Your result: %s",
                    participation.winningOutcomeName(), Format.signedMoney(participation.profitOrLoss()))));
        }
        return box;
    }

    private Label sectionTitle(String text) {
        Label label = new Label(text);
        label.setFont(Font.font(label.getFont().getFamily(), 14));
        return label;
    }
}
