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
    LIQUIDITY_NOT_POSITIVE
}
