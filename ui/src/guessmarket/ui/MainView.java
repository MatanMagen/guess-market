package guessmarket.ui;

import guessmarket.dto.UserSummary;
import guessmarket.engine.exception.GuessMarketException;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.List;

/**
 * The window: a bar along the top that loads files and says who you are playing, and the two
 * screens the sketch asks for underneath.
 * <p>
 * Files arrive only through a file chooser, and loading runs as a JavaFX task with a progress bar,
 * so the window stays alive while it happens.
 */
public final class MainView extends BorderPane {

    private final AppContext context;
    private final Stage stage;

    private final Label loadedPath = new Label("No file loaded");
    private final ComboBox<String> actingUser = new ComboBox<>();
    private final ProgressBar progress = new ProgressBar();
    private final Label progressText = new Label();
    private final Button loadButton = new Button("Load file");

    private final EventsTab eventsTab;
    private final UsersTab usersTab;

    public MainView(AppContext context, Stage stage) {
        this.context = context;
        this.stage = stage;
        this.eventsTab = new EventsTab(context);
        this.usersTab = new UsersTab(context);

        setTop(topBar());

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Tab events = new Tab("Events", eventsTab);
        Tab users = new Tab("Users", usersTab);
        tabs.getTabs().add(events);
        tabs.getTabs().add(users);
        setCenter(tabs);

        context.setRefreshAll(this::refreshAll);
        setMinWidth(720);
        setMinHeight(480);
    }

    private VBox topBar() {
        Label title = new Label("Guess Market");
        title.setFont(Font.font(title.getFont().getFamily(), 20));

        loadButton.setOnAction(event -> chooseAndLoad());
        loadedPath.setStyle("-fx-opacity: 0.8;");
        loadedPath.setMaxWidth(Double.MAX_VALUE);

        actingUser.setPromptText("Acting as");
        actingUser.setPrefWidth(160);
        actingUser.valueProperty().addListener((observable, was, now) -> {
            if (now != null && !now.equals(context.actingUser())) {
                context.actingUserProperty().set(now);
            }
            refreshDetailsOnly();
        });
        context.actingUserProperty().addListener((observable, was, now) -> {
            if (now != null && !now.equals(actingUser.getValue())) {
                actingUser.setValue(now);
            }
        });

        progress.setVisible(false);
        progress.setPrefWidth(180);
        progressText.setStyle("-fx-opacity: 0.8;");

        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(loadedPath, Priority.ALWAYS);
        row.getChildren().add(loadButton);
        row.getChildren().add(loadedPath);
        row.getChildren().add(spacer);
        row.getChildren().add(progressText);
        row.getChildren().add(progress);
        row.getChildren().add(new Label("Acting as"));
        row.getChildren().add(actingUser);

        VBox bar = new VBox(8);
        bar.setPadding(new Insets(10));
        bar.getChildren().add(title);
        bar.getChildren().add(row);
        return bar;
    }

    private void chooseAndLoad() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a Guess Market file");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Guess Market files", "*.xml"));
        File chosen = chooser.showOpenDialog(stage);
        if (chosen == null) {
            return;
        }
        load(chosen.getAbsolutePath());
    }

    private void load(String path) {
        LoadFileTask task = new LoadFileTask(context.engine(), path);
        progress.progressProperty().bind(task.progressProperty());
        progressText.textProperty().bind(task.messageProperty());
        progress.setVisible(true);
        loadButton.setDisable(true);

        task.setOnSucceeded(event -> {
            unbindProgress();
            loadedPath.setText(task.getValue().sourceDescription());
            refreshAll();
            selectFirstUser();
        });
        task.setOnFailed(event -> {
            unbindProgress();
            Throwable failure = task.getException();
            if (failure instanceof GuessMarketException engineFault) {
                Dialogs.failure(stage, "Cannot load that file", engineFault);
            } else {
                Dialogs.failure(stage, "Cannot load that file", String.valueOf(failure));
            }
        });

        Thread worker = new Thread(task, "guess-market-load");
        worker.setDaemon(true);
        worker.start();
    }

    private void unbindProgress() {
        progress.progressProperty().unbind();
        progressText.textProperty().unbind();
        progress.setVisible(false);
        progressText.setText("");
        loadButton.setDisable(false);
    }

    private void selectFirstUser() {
        List<UserSummary> users = context.engine().listUsers();
        if (!users.isEmpty() && context.actingUser() == null) {
            context.actingUserProperty().set(users.get(0).name());
        }
    }

    private void refreshAll() {
        List<String> names = new java.util.ArrayList<>();
        if (context.engine().isLoaded()) {
            for (UserSummary summary : context.engine().listUsers()) {
                names.add(summary.name());
            }
        }
        String acting = context.actingUser();
        actingUser.setItems(FXCollections.observableArrayList(names));
        if (acting != null && names.contains(acting)) {
            actingUser.setValue(acting);
        }
        eventsTab.refresh();
        usersTab.refresh();
    }

    private void refreshDetailsOnly() {
        if (context.engine().isLoaded()) {
            eventsTab.refresh();
            usersTab.refresh();
        }
    }
}
