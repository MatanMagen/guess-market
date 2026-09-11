import guessmarket.dto.OrderSide;
import guessmarket.engine.api.Engine;
import guessmarket.engine.impl.GuessMarketEngine;
import guessmarket.ui.AppContext;
import guessmarket.ui.MainView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Starts the real window on the grader's platform, drives the engine behind it, and leaves again.
 * Only used by the windows workflow: it proves the jars, the JavaFX module path and the native
 * libraries in the submission actually come up on Windows, which cannot be checked from a Mac.
 */
public class WindowsSmoke extends Application {

    @Override
    public void start(Stage stage) {
        Engine engine = new GuessMarketEngine();
        AppContext context = new AppContext(engine);
        MainView view = new MainView(context, stage);
        stage.setScene(new Scene(view, 1180, 760));
        stage.show();

        String testFiles = getParameters().getRaw().get(0);
        engine.loadFromFile(testFiles + "\\multiple.xml");
        engine.openEvent(2, "Avrum");
        engine.submitOrder(2, "Tikva", 1, OrderSide.BUY, 30, 0.62);
        engine.submitOrder(2, "Menash", 2, OrderSide.BUY, 20, 0.42);
        engine.openEvent(1, "Tikva");
        engine.buyLmsrShares(1, "Menash", 1, 25);
        context.actingUserProperty().set("Avrum");
        context.refreshAll();

        double account = engine.eventState(2).summary().accountBalance();
        Double lastOnSpain = engine.eventState(2).orderBook().books().get(1).lastTradePrice();
        System.out.println("SMOKE events=" + engine.listEvents().size()
                + " users=" + engine.listUsers().size()
                + " account=" + account
                + " lastNo=" + lastOnSpain);
        if (engine.listEvents().size() != 4 || account != 120.0d
                || lastOnSpain == null || Math.abs(lastOnSpain - 0.38d) > 1e-9) {
            System.out.println("SMOKE FAILED");
            Platform.exit();
            Runtime.getRuntime().halt(1);
        }
        System.out.println("SMOKE OK");
        Platform.runLater(Platform::exit);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
