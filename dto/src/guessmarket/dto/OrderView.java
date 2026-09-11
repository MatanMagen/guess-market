package guessmarket.dto;

/** One order resting in a book, as the whole market sees it: the book is public by design. */
public record OrderView(long orderId, String userName, long quantity, double price) {
}
