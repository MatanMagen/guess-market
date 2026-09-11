package guessmarket.engine.model;

import guessmarket.dto.FillKind;

import java.io.Serializable;

/** One execution inside a submitted order. {@code sellerName} is null on a mint. */
public record Fill(FillKind kind,
                   String outcomeName,
                   String buyerName,
                   String sellerName,
                   long quantity,
                   double price,
                   double buyerCommission) implements Serializable {
}
