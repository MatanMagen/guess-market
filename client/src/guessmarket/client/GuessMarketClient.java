package guessmarket.client;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Holds {@code main}. Opens on the login screen and, once the server has accepted a name, swaps
 * in the main window for that user.
 * <p>
 * The server is found at {@link Server#DEFAULT_URL}, localhost:8080 under the war's own name, as
 * the exercise allows. {@code -Dguessmarket.server=...} points it elsewhere when needed.
 */
public class GuessMarketClient extends Application {

    private static final double INITIAL_WIDTH = 1240;
    private static final double INITIAL_HEIGHT = 800;

    @Override
    public void start(Stage stage) {
        Server server = new Server(System.getProperty("guessmarket.server", Server.DEFAULT_URL));
        Scene scene = new Scene(new LoginView(server, name -> {
            stage.setTitle("Guess Market — " + name);
            stage.getScene().setRoot(new MainView(new AppContext(server, name)));
        }), INITIAL_WIDTH, INITIAL_HEIGHT);
        stage.setTitle("Guess Market");
        stage.setScene(scene);
        stage.setMinWidth(720);
        stage.setMinHeight(480);
        stage.show();
    }

    /** The poller and request threads are daemons, but OkHttp keeps connections alive a while. */
    @Override
    public void stop() {
        System.exit(0);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
