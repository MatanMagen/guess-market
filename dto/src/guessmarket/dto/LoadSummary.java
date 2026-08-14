package guessmarket.dto;

/**
 * What was loaded when a file was accepted. There is no equivalent for a rejected file: that
 * comes back as an InvalidFileException, so a caller cannot mistake failure for success by
 * forgetting to check a flag.
 */
public record LoadSummary(String sourceDescription, int eventsLoaded) {
}
