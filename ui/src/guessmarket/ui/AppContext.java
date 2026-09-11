package guessmarket.ui;

import guessmarket.engine.api.Engine;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * What every screen needs: the engine, who is currently acting, and a way to say that something
 * changed so the other screens redraw.
 * <p>
 * The concrete engine class is not named here — it is named once, in {@link GuessMarketApp}.
 */
public class AppContext {

    private final Engine engine;
    private final StringProperty actingUser = new SimpleStringProperty();
    private Runnable refreshAll = () -> { };

    public AppContext(Engine engine) {
        this.engine = engine;
    }

    public Engine engine() {
        return engine;
    }

    public StringProperty actingUserProperty() {
        return actingUser;
    }

    public String actingUser() {
        return actingUser.get();
    }

    public void setRefreshAll(Runnable refreshAll) {
        this.refreshAll = refreshAll;
    }

    public void refreshAll() {
        refreshAll.run();
    }
}
