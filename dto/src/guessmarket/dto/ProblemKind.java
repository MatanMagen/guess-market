package guessmarket.dto;

/**
 * The faults a Guess Market file can be rejected for. The comment on each constant lists the
 * values a FileProblem of that kind carries.
 */
public enum ProblemKind {
    /** 0: file name, 1: required extension */
    WRONG_EXTENSION,
    /** 0: file name, 1: reason */
    UNREADABLE_XML,
    /** no values */
    NO_EVENTS,
    /** no values */
    BLANK_EVENT_NAME,
    /** 0: the name, 1: the earlier position in the same file using it */
    DUPLICATE_EVENT_NAME_IN_FILE,
    /** 0: the name, already used by an event in the system */
    DUPLICATE_EVENT_NAME,
    /** no values */
    MISSING_COMMISSION,
    /** 0: value found, 1: lowest allowed, 2: highest allowed */
    COMMISSION_OUT_OF_RANGE,
    /** 0: value found */
    UNKNOWN_COMMISSION_TYPE,
    /** 0: count found, 1: fewest allowed */
    WRONG_ANSWER_COUNT,
    /** 0: the answer number */
    BLANK_ANSWER,
    /** 0: the repeated answer */
    DUPLICATE_ANSWER,
    /** no values */
    MISSING_METHOD,
    /** 0: value found */
    LIQUIDITY_NOT_POSITIVE,
    /** 0: value found */
    BASE_PRICE_NOT_POSITIVE,
    /** 0: value found */
    INITIAL_INVESTMENT_NEGATIVE,
    /** 0: value found */
    UNKNOWN_MINT_FLAG
}
