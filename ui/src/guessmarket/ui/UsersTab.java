package guessmarket.ui;

import guessmarket.dto.HoldingState;
import guessmarket.dto.MarketMethod;
import guessmarket.dto.TradeRecord;
import guessmarket.dto.UserDetails;
import guessmarket.dto.UserParticipation;
import guessmarket.dto.UserSummary;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

import java.util.List;

/**
 * Every user, and what the selected one is doing.
 * <p>
 * This is where trading happens, as the exercise asks: pick the user you are playing, pick one of
 * his events, and the same detail pane the events screen uses appears with his controls on it.
 */
public final class UsersTab extends SplitPane {

    private final AppContext context;
    private final TableView<UserSummary> users = Tables.table("No file is loaded.");
    private final TableView<UserParticipation> participations = Tables.table("Not taking part in anything yet.");
    private final EventDetailPane detail;

    private final Label name = new Label();
    private final Label balance = new Label();
    private final Label blocked = new Label();
    private final VBox involvement = new VBox(8);

    public UsersTab(AppContext context) {
        this.context = context;
        this.detail = new EventDetailPane(context);

        users.getColumns().add(Tables.column("User", 150, UserSummary::name));
        users.getColumns().add(Tables.numberColumn("Balance", 120, u -> Format.money(u.balance())));
        users.getColumns().add(Tables.numberColumn("Events", 70, u -> String.valueOf(u.activeEventCount())));
        users.getColumns().add(Tables.column("State", 90, u -> u.blocked() ? "Blocked" : "Active"));
        VBox.setVgrow(users, Priority.ALWAYS);
        users.setMinWidth(300);

        users.getSelectionModel().selectedItemProperty().addListener((observable, was, now) -> {
            if (now != null) {
                // Selecting a user is also how you choose who you are playing.
                context.actingUserProperty().set(now.name());
            }
            showUser(now);
        });

        participations.getColumns().add(Tables.column("Event", 190, UserParticipation::eventName));
        participations.getColumns().add(Tables.column("Method", 100, p -> Format.method(p.method())));
        participations.getColumns().add(Tables.column("Status", 100, p -> Format.status(p.status())));
        participations.getColumns().add(Tables.column("Role", 110, p -> p.marketMaker() ? "Market maker" : "Trader"));
        participations.getColumns().add(Tables.numberColumn("Commission paid", 130,
                p -> Format.money(p.commissionPaid())));
        participations.getColumns().add(Tables.numberColumn("Profit / loss", 120,
                p -> p.status() == guessmarket.dto.EventLifecycle.CLOSED
                        ? Format.signedMoney(p.profitOrLoss()) : "—"));
        participations.setPrefHeight(170);
        participations.getSelectionModel().selectedItemProperty().addListener((observable, was, now) -> {
            if (now == null) {
                involvement.getChildren().clear();
                detail.showNothing();
            } else {
                involvement.getChildren().setAll(involvementOf(now));
                detail.show(now.eventId());
            }
        });

        VBox right = new VBox(10);
        right.setPadding(new Insets(12));
        right.getChildren().add(headline());
        right.getChildren().add(new Separator());
        right.getChildren().add(sectionTitle("Events he is taking part in"));
        right.getChildren().add(participations);
        right.getChildren().add(involvement);
        right.getChildren().add(new Separator());
        VBox.setVgrow(detail, Priority.ALWAYS);
        right.getChildren().add(detail);

        ScrollPane scroller = new ScrollPane(right);
        scroller.setFitToWidth(true);

        getItems().add(new VBox(users));
        getItems().add(scroller);
        setDividerPositions(0.33);
    }

    private Node headline() {
        name.setFont(Font.font(name.getFont().getFamily(), 18));
        HBox row = new HBox(16);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().add(name);
        row.getChildren().add(blocked);
        row.getChildren().add(spacer);
        row.getChildren().add(balance);
        return row;
    }

    /** Rereads everything, keeping the selected user and event selected. */
    public void refresh() {
        String selectedUser = users.getSelectionModel().getSelectedItem() == null
                ? null : users.getSelectionModel().getSelectedItem().name();
        Integer selectedEvent = participations.getSelectionModel().getSelectedItem() == null
                ? null : participations.getSelectionModel().getSelectedItem().eventId();

        users.setItems(FXCollections.observableArrayList(
                context.engine().isLoaded() ? context.engine().listUsers() : List.of()));
        if (selectedUser != null) {
            for (UserSummary summary : users.getItems()) {
                if (summary.name().equals(selectedUser)) {
                    users.getSelectionModel().select(summary);
                    break;
                }
            }
        }
        if (selectedEvent != null) {
            for (UserParticipation participation : participations.getItems()) {
                if (participation.eventId() == selectedEvent) {
                    participations.getSelectionModel().select(participation);
                    break;
                }
            }
        }
        detail.refresh();
    }

    private void showUser(UserSummary summary) {
        if (summary == null) {
            name.setText("");
            balance.setText("");
            blocked.setText("");
            participations.setItems(FXCollections.observableArrayList());
            involvement.getChildren().clear();
            detail.showNothing();
            return;
        }
        UserDetails details = context.engine().userDetails(summary.name());
        name.setText(details.name());
        balance.setText("Account balance " + Format.money(details.balance()));
        blocked.setText(details.blocked() ? "BLOCKED — went into overdraft" : "");
        blocked.setStyle(details.blocked() ? "-fx-text-fill: firebrick;" : "");

        Integer wasShowing = participations.getSelectionModel().getSelectedItem() == null
                ? null : participations.getSelectionModel().getSelectedItem().eventId();
        participations.setItems(FXCollections.observableArrayList(details.participations()));
        for (UserParticipation participation : participations.getItems()) {
            if (wasShowing != null && participation.eventId() == wasShowing) {
                participations.getSelectionModel().select(participation);
                return;
            }
        }
        involvement.getChildren().clear();
        detail.showNothing();
    }

    /** What his involvement in one event looks like, in whichever terms that event trades. */
    private Node involvementOf(UserParticipation participation) {
        VBox box = new VBox(8);
        if (participation.method() == MarketMethod.LMSR) {
            box.getChildren().add(sectionTitle("His trades on this event, newest first"));
            TableView<TradeRecord> trades = Tables.table("He has not traded here yet.");
            trades.getColumns().add(Tables.column("Answer", 180, TradeRecord::outcomeName));
            trades.getColumns().add(Tables.numberColumn("Shares", 90, t -> Format.quantity(t.quantity())));
            trades.getColumns().add(Tables.numberColumn("Price paid", 110, t -> Format.money(t.sharesCost())));
            trades.getColumns().add(Tables.numberColumn("Commission", 110, t -> Format.money(t.commissionPaid())));
            trades.setItems(FXCollections.observableArrayList(participation.lmsrTrades()));
            trades.setPrefHeight(150);
            box.getChildren().add(trades);
        } else {
            box.getChildren().add(sectionTitle("What he holds here"));
            TableView<HoldingState> holdings = Tables.table("He holds nothing.");
            holdings.getColumns().add(Tables.column("Answer", 180, HoldingState::outcomeName));
            holdings.getColumns().add(Tables.numberColumn("Shares", 100, h -> Format.quantity(h.quantity())));
            holdings.getColumns().add(Tables.numberColumn("Paid for them", 130, h -> Format.money(h.amountPaid())));
            holdings.setItems(FXCollections.observableArrayList(participation.holdings()));
            holdings.setPrefHeight(110);
            box.getChildren().add(holdings);
            box.getChildren().add(new Label("Commission paid: " + Format.money(participation.commissionPaid())));
        }
        if (participation.winningOutcomeName() != null) {
            box.getChildren().add(new Label("Closed on '" + participation.winningOutcomeName()
                    + "'. His result: " + Format.signedMoney(participation.profitOrLoss())));
        }
        return box;
    }

    private Label sectionTitle(String text) {
        Label label = new Label(text);
        label.setFont(Font.font(label.getFont().getFamily(), 14));
        return label;
    }
}
