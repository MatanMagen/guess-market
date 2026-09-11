package guessmarket.ui;

import guessmarket.engine.api.Engine;
import guessmarket.engine.impl.GuessMarketEngine;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Holds {@code main} and names the concrete engine, which is the only place in the application
 * that knows it exists. Everything else works through the {@link Engine} interface.
 */
public class GuessMarketApp extends Application {

    private static final double INITIAL_WIDTH = 1180;
    private static final double INITIAL_HEIGHT = 760;

    @Override
    public void start(Stage stage) {
        Engine engine = new GuessMarketEngine();
        AppContext context = new AppContext(engine);
        MainView view = new MainView(context, stage);

        Scene scene = new Scene(view, INITIAL_WIDTH, INITIAL_HEIGHT);
        stage.setTitle("Guess Market");
        stage.setScene(scene);
        stage.setMinWidth(720);
        stage.setMinHeight(480);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
