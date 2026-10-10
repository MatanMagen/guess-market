package guessmarket.dto;

import java.util.List;

/**
 * Everything the account screen shows about one user. {@code ledger} is every movement of their
 * money, oldest first, one line per movement.
 */
public record UserDetails(String name,
                          double balance,
                          boolean blocked,
                          List<UserParticipation> participations,
                          List<AccountEntry> ledger) {
    public UserDetails {
        participations = List.copyOf(participations);
        ledger = List.copyOf(ledger);
    }
}
