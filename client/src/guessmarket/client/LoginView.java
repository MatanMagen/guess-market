package guessmarket.client;

import guessmarket.dto.UserDetails;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * The first screen: a name and nothing else, as the exercise asks — no password, no sign up. A
 * name somebody already holds is refused by the server, and the user simply tries another.
 */
public final class LoginView extends VBox {

    private final Server server;
    private final Consumer<String> loggedIn;
    private final TextField name = new TextField();
    private final Label error = new Label();
    private final Button login = new Button("Log in");

    public LoginView(Server server, Consumer<String> loggedIn) {
        this.server = server;
        this.loggedIn = loggedIn;

        Label title = new Label("Guess Market");
        title.setFont(Font.font(title.getFont().getFamily(), 28));
        Label prompt = new Label("Choose a name to log in with");

        name.setPromptText("Your name");
        name.setMaxWidth(260);
        name.textProperty().addListener((observable, was, now) -> error.setText(""));
        name.setOnAction(event -> login());

        login.setDefaultButton(true);
        login.setOnAction(event -> login());

        error.setStyle("-fx-text-fill: firebrick;");
        error.setWrapText(true);
        error.setMaxWidth(420);

        Label where = new Label("Server: " + server.baseUrl());
        where.setStyle("-fx-opacity: 0.6;");

        setSpacing(12);
        setAlignment(Pos.CENTER);
        setPadding(new Insets(24));
        getChildren().addAll(title, prompt, name, login, error, where);
    }

    private void login() {
        String typed = name.getText().trim();
        if (typed.isEmpty()) {
            error.setText("Please enter a name.");
            return;
        }
        login.setDisable(true);
        error.setText("");
        CompletableFuture.supplyAsync(() -> server.login(typed)).whenComplete((UserDetails me, Throwable failure) ->
                Platform.runLater(() -> {
                    login.setDisable(false);
                    if (failure == null) {
                        loggedIn.accept(me.name());
                    } else {
                        Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                        error.setText(cause instanceof ServerException ? cause.getMessage() : String.valueOf(cause));
                    }
                }));
    }
}
