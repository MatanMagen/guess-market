package guessmarket.dto;

/** One message of the shared chat (bonus 1). */
public record ChatLine(String userName, long timeMillis, String text) {
}
