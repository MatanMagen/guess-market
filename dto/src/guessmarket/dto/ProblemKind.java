package guessmarket.dto;

/**
 * The faults a Guess Market file can be rejected for. The comment on each constant lists the
 * values a FileProblem of that kind carries.
 */
public enum ProblemKind {

    /** no values */
    NO_PATH_GIVEN,
    /** 0: path */
    FILE_NOT_FOUND,
    /** 0: path */
    PATH_IS_A_FOLDER,
    /** 0: path */
    FILE_NOT_READABLE,
    /** 0: path, 1: required extension */
    WRONG_EXTENSION,
    /** 0: path, 1: reason */
    UNREADABLE_XML,
    /** no values */
    NO_EVENTS,
    /** 0: the repeated number, 1: the earlier position using it */
    DUPLICATE_EVENT_ID,
    /** no values */
    BLANK_EVENT_NAME,
    /** no values */
    MISSING_COMMISSION,
    /** 0: value found, 1: lowest allowed, 2: highest allowed */
    COMMISSION_OUT_OF_RANGE,
    /** 0: value found */
    UNKNOWN_COMMISSION_TYPE,
    /** 0: count found, 1: count required */
    WRONG_ANSWER_COUNT,
    /** 0: the answer number */
    BLANK_ANSWER,
    /** 0: the repeated answer */
    DUPLICATE_ANSWER,
    /** no values */
    MISSING_METHOD,
    /** no values */
    MISSING_LMSR,
    /** 0: value found */
    LIQUIDITY_NOT_POSITIVE,

    // Added in exercise 2, with users and the order book.

    /** no values */
    NO_USERS,
    /** 0: the position in the file */
    BLANK_USER_NAME,
    /** 0: the repeated name, 1: the earlier position using it */
    DUPLICATE_USER_NAME,
    /** 0: user name, 1: value found */
    INITIAL_CASH_NOT_POSITIVE,
    /** 0: user name, 1: the event number referred to */
    MARKET_MAKER_OF_UNKNOWN_EVENT,
    /** 0: user name, 1: the event number named twice */
    MARKET_MAKER_TWICE_OF_SAME_EVENT,
    /** no values, event scoped */
    EVENT_WITHOUT_MARKET_MAKER,
    /** 0: the users claiming it, comma separated; event scoped */
    EVENT_WITH_SEVERAL_MARKET_MAKERS,
    /** no values */
    MISSING_ORDER_BOOK,
    /** 0: value found */
    BASE_PRICE_NOT_POSITIVE,
    /** 0: value found */
    INITIAL_INVESTMENT_NEGATIVE,
    /** 0: value found */
    UNKNOWN_MINT_FLAG
}
