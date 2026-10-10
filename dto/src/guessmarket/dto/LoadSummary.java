package guessmarket.dto;

import java.util.List;

/**
 * What an accepted upload added to the system. There is no equivalent for a rejected file: that
 * comes back as an InvalidFileException, so a caller cannot mistake failure for success by
 * forgetting to check a flag.
 */
public record LoadSummary(String fileName, String marketMakerName, List<String> eventNames) {
    public LoadSummary {
        eventNames = List.copyOf(eventNames);
    }
}
