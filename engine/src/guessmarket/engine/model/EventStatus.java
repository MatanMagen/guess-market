package guessmarket.engine.model;

/**
 * An event is loaded NOT_STARTED. Only its market maker can open it, and only he can close it;
 * a closed event can never be reopened.
 */
public enum EventStatus {
    NOT_STARTED,
    ACTIVE,
    CLOSED
}
