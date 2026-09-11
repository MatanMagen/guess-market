package guessmarket.ui;

import guessmarket.dto.CommissionPolicy;
import guessmarket.dto.EventLifecycle;
import guessmarket.dto.EventSummary;
import guessmarket.dto.MarketMethod;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Every event in the system, with the three filters the exercise asks for, and the details of
 * whichever one is selected.
 * <p>
 * The filters are toggle buttons rather than check boxes so that "all of them" is one click on a
 * button that is already there, and filtering happens here rather than in the engine: the engine
 * hands over every event and the screen decides what to draw.
 */
public final class EventsTab extends SplitPane {

    private final AppContext context;
    private final TableView<EventSummary> table = Tables.table("No file is loaded.");
    private final ObservableList<EventSummary> shown = FXCollections.observableArrayList();
    private final EventDetailPane detail;

    private final FilterGroup<MarketMethod> byMethod =
            new FilterGroup<>("Method", List.of(MarketMethod.values()), Format::method);
    private final FilterGroup<EventLifecycle> byStatus =
            new FilterGroup<>("Status", List.of(EventLifecycle.values()), Format::status);
    private final FilterGroup<CommissionPolicy> byCommission =
            new FilterGroup<>("Commission", List.of(CommissionPolicy.values()), Format::commission);

    private List<EventSummary> all = List.of();

    public EventsTab(AppContext context) {
        this.context = context;
        this.detail = new EventDetailPane(context);

        table.getColumns().add(Tables.column("Event", 200, EventSummary::name));
        table.getColumns().add(Tables.column("Status", 100, e -> Format.status(e.status())));
        table.getColumns().add(Tables.column("Method", 100, e -> Format.method(e.method())));
        table.getColumns().add(Tables.column("Commission", 130,
                e -> e.commissionPercent() + "% " + Format.commission(e.commissionType())));
        table.getColumns().add(Tables.numberColumn("Account", 110, e -> Format.money(e.accountBalance())));
        table.setItems(shown);
        VBox.setVgrow(table, Priority.ALWAYS);

        table.getSelectionModel().selectedItemProperty().addListener((observable, was, now) -> {
            if (now == null) {
                detail.showNothing();
            } else {
                detail.show(now.id());
            }
        });

        Runnable apply = this::applyFilters;
        byMethod.onChange(apply);
        byStatus.onChange(apply);
        byCommission.onChange(apply);

        VBox left = new VBox(8);
        left.setPadding(new Insets(10));
        left.getChildren().add(filterLine());
        left.getChildren().add(table);
        left.setMinWidth(320);

        getItems().add(left);
        getItems().add(detail);
        setDividerPositions(0.46);
    }

    private Node filterLine() {
        FlowPane line = new FlowPane(14, 6);
        line.setAlignment(Pos.CENTER_LEFT);
        line.getChildren().add(byMethod.node());
        line.getChildren().add(byStatus.node());
        line.getChildren().add(byCommission.node());
        return line;
    }

    /** Rereads everything from the engine, keeping the selected event selected if it is still there. */
    public void refresh() {
        Integer selected = table.getSelectionModel().getSelectedItem() == null
                ? null : table.getSelectionModel().getSelectedItem().id();
        all = context.engine().isLoaded() ? context.engine().listEvents() : List.of();
        applyFilters();
        if (selected != null) {
            for (EventSummary summary : shown) {
                if (summary.id() == selected) {
                    table.getSelectionModel().select(summary);
                    break;
                }
            }
        }
        detail.refresh();
    }

    private void applyFilters() {
        List<EventSummary> kept = new ArrayList<>();
        for (EventSummary summary : all) {
            if (byMethod.accepts(summary.method())
                    && byStatus.accepts(summary.status())
                    && byCommission.accepts(summary.commissionType())) {
                kept.add(summary);
            }
        }
        EventSummary selected = table.getSelectionModel().getSelectedItem();
        shown.setAll(kept);
        if (selected != null && kept.contains(selected)) {
            table.getSelectionModel().select(selected);
        }
    }

    /**
     * One filter: a row of toggle buttons where nothing selected means everything passes, which is
     * the "all of them" the exercise asks for.
     */
    private static final class FilterGroup<T> {

        private final HBox box = new HBox(4);
        private final Set<T> chosen = new LinkedHashSet<>();
        private Runnable onChange = () -> { };

        private FilterGroup(String name, List<T> values, Function<T, String> label) {
            box.setAlignment(Pos.CENTER_LEFT);
            Label caption = new Label(name + ":");
            caption.setStyle("-fx-opacity: 0.7;");
            box.getChildren().add(caption);
            for (T value : values) {
                ToggleButton button = new ToggleButton(label.apply(value));
                button.setOnAction(event -> {
                    if (button.isSelected()) {
                        chosen.add(value);
                    } else {
                        chosen.remove(value);
                    }
                    onChange.run();
                });
                box.getChildren().add(button);
            }
        }

        private void onChange(Runnable listener) {
            this.onChange = listener;
        }

        private boolean accepts(T value) {
            return chosen.isEmpty() || chosen.contains(value);
        }

        private Node node() {
            return box;
        }
    }
}
