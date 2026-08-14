package guessmarket.engine.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Everything loaded from one file. Built in full before the engine takes it, which is what keeps a
 * rejected file from disturbing the state already in memory.
 */
public class MarketState implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String sourceDescription;
    /** ArrayList rather than List so the field is provably serializable. */
    private final ArrayList<Event> events;

    public MarketState(String sourceDescription, List<Event> events) {
        this.sourceDescription = sourceDescription;
        this.events = new ArrayList<>(events);
    }

    public String sourceDescription() {
        return sourceDescription;
    }

    public List<Event> events() {
        return Collections.unmodifiableList(events);
    }

    public List<Event> activeEvents() {
        List<Event> active = new ArrayList<>();
        for (Event event : events) {
            if (event.status() == EventStatus.ACTIVE) {
                active.add(event);
            }
        }
        return active;
    }
}
