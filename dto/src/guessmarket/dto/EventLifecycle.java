package guessmarket.dto;

/** Where an event stands. It is loaded NOT_STARTED and only its market maker can move it on. */
public enum EventLifecycle {
    NOT_STARTED,
    ACTIVE,
    CLOSED
}
