package guessmarket.client;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Label;

import java.util.List;
import java.util.Objects;
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

    /**
     * Puts new rows into a table only if they differ from what is shown, and keeps the same row
     * selected, so the half second refresh neither flickers nor loses the user's place. Rows are
     * records, so "differ" means any value in them changed.
     * <p>
     * Replacing the rows clears the selection for a moment before it is put back; a selection
     * listener that should not react to that checks {@code isRefilling} on its own side.
     *
     * @param key what identifies a row across refreshes, such as an event's number.
     * @return whether anything changed.
     */
    public static <S> boolean refill(TableView<S> table, List<S> rows, Function<S, Object> key) {
        if (table.getItems().equals(rows)) {
            return false;
        }
        S selected = table.getSelectionModel().getSelectedItem();
        Object selectedKey = selected == null ? null : key.apply(selected);
        table.getItems().setAll(rows);
        if (selectedKey != null) {
            for (S row : table.getItems()) {
                if (Objects.equals(key.apply(row), selectedKey)) {
                    table.getSelectionModel().select(row);
                    break;
                }
            }
        }
        return true;
    }
}
