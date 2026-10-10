package guessmarket.engine.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything the server holds: every user who has logged in and every event uploaded so far.
 * Uploads accumulate rather than replace each other, and nothing outlives the server.
 */
public class MarketState implements Serializable {

    private static final long serialVersionUID = 2L;

    /** ArrayList and LinkedHashMap rather than the interfaces so the fields are provably serializable. */
    private final ArrayList<Event> events = new ArrayList<>();
    private final LinkedHashMap<String, User> users = new LinkedHashMap<>();

    public List<Event> events() {
        return Collections.unmodifiableList(events);
    }

    public List<User> users() {
        return List.copyOf(users.values());
    }

    /** Keyed by name, which registration has already proved unique. */
    public Map<String, User> usersByName() {
        return Collections.unmodifiableMap(users);
    }

    public User user(String name) {
        return users.get(name);
    }

    public void addUser(User user) {
        users.put(user.name(), user);
    }

    public void addEvent(Event event) {
        events.add(event);
    }

    /** One past the highest number in use, so a new event cannot collide with an existing one. */
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

    /** Names are what tell events apart since exercise 3, and they are compared exactly. */
    public Event eventByName(String name) {
        for (Event event : events) {
            if (event.name().equals(name)) {
                return event;
            }
        }
        return null;
    }
}
