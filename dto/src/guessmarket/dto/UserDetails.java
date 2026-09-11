package guessmarket.dto;

import java.util.List;

/** Everything the user screen shows about one user. */
public record UserDetails(String name,
                          double balance,
                          boolean blocked,
                          List<UserParticipation> participations) {

    public UserDetails {
        participations = List.copyOf(participations);
    }
}
