package guessmarket.engine.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything loaded from one file: its events and its users. Built in full before the engine takes
 * it, which is what keeps a rejected file from disturbing the state already in memory.
 */
public class MarketState implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String sourceDescription;
    /** ArrayList and LinkedHashMap rather than the interfaces so the fields are provably serializable. */
    private final ArrayList<Event> events;
    private final LinkedHashMap<String, User> users;

    public MarketState(String sourceDescription, List<Event> events, List<User> users) {
        this.sourceDescription = sourceDescription;
        this.events = new ArrayList<>(events);
        this.users = new LinkedHashMap<>();
        for (User user : users) {
            this.users.put(user.name(), user);
        }
    }

    public String sourceDescription() {
        return sourceDescription;
    }

    public List<Event> events() {
        return Collections.unmodifiableList(events);
    }

    public List<User> users() {
        return List.copyOf(users.values());
    }

    /** Keyed by name, which the loader has already proved unique. */
    public Map<String, User> usersByName() {
        return Collections.unmodifiableMap(users);
    }

    public User user(String name) {
        return users.get(name);
    }

    /** Adds an event somebody built by hand. Everything else about it works exactly as if it had been loaded. */
    public void addEvent(Event event) {
        events.add(event);
    }

    /** One past the highest number in use, so a new event cannot collide with a loaded one. */
    public int nextEventId() {
        int highest = 0;
        for (Event event : events) {
            highest = Math.max(highest, event.id());
        }
        return highest + 1;
    }

    public Event eventById(int id) {
        for (Event event : events) {
            if (event.id() == id) {
                return event;
            }
        }
        return null;
    }

    /** Exercise 3 keys events by name instead of by number, so both lookups exist from the start. */
    public Event eventByName(String name) {
        for (Event event : events) {
            if (event.name().equals(name)) {
                return event;
            }
        }
        return null;
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
