package guessmarket.ui;

import guessmarket.dto.CommissionPolicy;
import guessmarket.dto.MarketMethod;
import guessmarket.dto.NewEvent;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.Region;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.util.List;

/**
 * Asks for everything a new event needs. Which settings are shown follows the trading method, since
 * an LMSR event is described by its liquidity and an order book by its base value and initial stock.
 * <p>
 * Nothing is judged here. The dialog only collects what was typed and hands it to the engine, which
 * applies the same rules it would to a loaded file and refuses the event if it does not meet them.
 */
public final class NewEventDialog extends Dialog<NewEvent> {

    private final TextField name = new TextField();
    private final TextArea description = new TextArea();
    private final Spinner<Integer> commission = new Spinner<>(0, 90, 5);
    private final ComboBox<CommissionPolicy> commissionType = new ComboBox<>();
    private final TextField firstAnswer = new TextField("Yes");
    private final TextField secondAnswer = new TextField("No");
    private final ComboBox<MarketMethod> method = new ComboBox<>();

    private final Spinner<Integer> liquidity = new Spinner<>(1, 1_000_000, 100);
    private final Spinner<Integer> basePrice = new Spinner<>(1, 1_000, 1);
    private final Spinner<Integer> initialInvestment = new Spinner<>(0, 1_000_000, 100);
    private final CheckBox mintAllowed = new CheckBox("Allow minting of new share pairs");

    private final Label lmsrLabel = new Label("Liquidity b");
    private final Label baseLabel = new Label("Base value d");
    private final Label initialLabel = new Label("Initial investment");

    public NewEventDialog(Window owner, String creatorName) {
        initOwner(owner);
        setTitle("New event");
        setHeaderText(creatorName + " will be the market maker of this event.");
        setResizable(true);

        description.setPrefRowCount(3);
        description.setWrapText(true);
        commission.setEditable(true);
        liquidity.setEditable(true);
        basePrice.setEditable(true);
        initialInvestment.setEditable(true);
        mintAllowed.setSelected(true);

        commissionType.setItems(FXCollections.observableArrayList(CommissionPolicy.values()));
        commissionType.setConverter(converter(Format::commission));
        commissionType.getSelectionModel().selectFirst();
        method.setItems(FXCollections.observableArrayList(MarketMethod.values()));
        method.setConverter(converter(Format::method));
        method.getSelectionModel().selectFirst();
        method.valueProperty().addListener((observable, was, now) -> showSettingsFor(now));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(12));
        ColumnConstraints labels = new ColumnConstraints();
        labels.setMinWidth(Region.USE_PREF_SIZE);
        ColumnConstraints fields = new ColumnConstraints();
        fields.setHgrow(Priority.ALWAYS);
        fields.setFillWidth(true);
        grid.getColumnConstraints().add(labels);
        grid.getColumnConstraints().add(fields);
        for (Region field : new Region[] {name, description, firstAnswer, secondAnswer,
                commission, commissionType, method, liquidity, basePrice, initialInvestment}) {
            field.setMaxWidth(Double.MAX_VALUE);
        }
        int row = 0;
        grid.addRow(row++, new Label("Name"), name);
        grid.addRow(row++, new Label("Description"), description);
        grid.addRow(row++, new Label("First answer"), firstAnswer);
        grid.addRow(row++, new Label("Second answer"), secondAnswer);
        grid.addRow(row++, new Label("Commission %"), commission);
        grid.addRow(row++, new Label("Charged"), commissionType);
        grid.addRow(row++, new Label("Traded by"), method);
        grid.addRow(row++, lmsrLabel, liquidity);
        grid.addRow(row++, baseLabel, basePrice);
        grid.addRow(row++, initialLabel, initialInvestment);
        grid.add(mintAllowed, 1, row);
        getDialogPane().setContent(grid);

        ButtonType create = new ButtonType("Create event", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().add(create);
        getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        getDialogPane().setPrefWidth(460);

        showSettingsFor(method.getValue());
        setResultConverter(button -> button == create ? buildRequest(creatorName) : null);
    }

    /** Combo boxes would otherwise show the constant names, which are not what anybody calls these. */
    private static <T> StringConverter<T> converter(java.util.function.Function<T, String> text) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : text.apply(value);
            }

            @Override
            public T fromString(String written) {
                throw new UnsupportedOperationException("these boxes are not editable");
            }
        };
    }

    private NewEvent buildRequest(String creatorName) {
        List<String> answers = List.of(firstAnswer.getText(), secondAnswer.getText());
        return method.getValue() == MarketMethod.LMSR
                ? NewEvent.lmsr(creatorName, name.getText(), description.getText(),
                        commission.getValue(), commissionType.getValue(), answers, liquidity.getValue())
                : NewEvent.orderBook(creatorName, name.getText(), description.getText(),
                        commission.getValue(), commissionType.getValue(), answers,
                        basePrice.getValue(), mintAllowed.isSelected(), initialInvestment.getValue());
    }

    private void showSettingsFor(MarketMethod chosen) {
        boolean lmsr = chosen == MarketMethod.LMSR;
        show(lmsrLabel, lmsr);
        show(liquidity, lmsr);
        show(baseLabel, !lmsr);
        show(basePrice, !lmsr);
        show(initialLabel, !lmsr);
        show(initialInvestment, !lmsr);
        show(mintAllowed, !lmsr);
        getDialogPane().getScene().getWindow().sizeToScene();
    }

    private void show(javafx.scene.Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
