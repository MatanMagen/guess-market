package guessmarket.ui;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Label;

import java.util.function.Function;

/**
 * Table plumbing, kept in one place.
 * <p>
 * Every column here shows text that {@link Format} has already produced, so the cell factories stay
 * trivial and the tables carry no formatting rules of their own.
 */
public final class Tables {

    private Tables() {
    }

    public static <S> TableColumn<S, String> column(String title, double width, Function<S, String> value) {
        TableColumn<S, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return column;
    }

    /** A right aligned column, for anything that is a number. */
    public static <S> TableColumn<S, String> numberColumn(String title, double width, Function<S, String> value) {
        TableColumn<S, String> column = column(title, width, value);
        column.setStyle("-fx-alignment: CENTER-RIGHT;");
        return column;
    }

    public static <S> TableView<S> table(String emptyText) {
        TableView<S> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label(emptyText));
        return table;
    }
}
