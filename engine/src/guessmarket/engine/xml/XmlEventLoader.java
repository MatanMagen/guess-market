package guessmarket.engine.xml;

import guessmarket.dto.FileProblem;
import guessmarket.dto.ProblemKind;
import guessmarket.engine.exception.InvalidFileException;
import guessmarket.engine.model.CommissionType;
import guessmarket.engine.model.Event;
import guessmarket.engine.model.LmsrEvent;
import guessmarket.engine.model.OrderBookEvent;
import guessmarket.engine.xml.generated.Commission;
import guessmarket.engine.xml.generated.GMEvent;
import guessmarket.engine.xml.generated.GMLMSR;
import guessmarket.engine.xml.generated.GMMethod;
import guessmarket.engine.xml.generated.GMOptions;
import guessmarket.engine.xml.generated.GMOrderBook;
import guessmarket.engine.xml.generated.GuessMarket;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Turns an uploaded Guess Market XML file into events. Reading and validating are engine work, so
 * a front end supplies nothing but the bytes and the name the file had on the user's machine.
 * <p>
 * The file never touches the server's disk: it is read straight from the stream it arrived on.
 * <p>
 * What is checked here is what the schema cannot express, as in exercise 1: the file is named
 * .xml, commissions are sensible percentages, every event has at least two distinct answers, and
 * b and d can be divided by. Exercise 3 adds that an event's name must be new — neither repeated
 * inside the file nor already used by an event in the system. Faults are collected rather than
 * thrown one at a time.
 */
public final class XmlEventLoader {

    /** The extension a Guess Market file must carry. */
    private static final String REQUIRED_EXTENSION = ".xml";

    private static final int MIN_COMMISSION_PERCENT = 0;
    private static final int MAX_COMMISSION_PERCENT = 90;
    private static final int MIN_OUTCOME_COUNT = 2;

    /** Building a context is slow and it is safe to share, so it is done once. */
    private static JAXBContext context;

    /**
     * @param fileName       as the user's machine called it; only its extension matters.
     * @param marketMaker    who uploaded the file, and so runs every event in it.
     * @param nameTaken      whether an event of that name already exists in the system.
     * @param firstId        the number the first event will be given; the rest follow on.
     * @throws InvalidFileException carrying every fault found. Nothing is created in that case.
     */
    public List<Event> load(InputStream xml, String fileName, String marketMaker,
                            Predicate<String> nameTaken, int firstId) {
        String name = fileName == null ? "" : fileName.trim();
        if (!name.toLowerCase().endsWith(REQUIRED_EXTENSION)) {
            throw new InvalidFileException(name,
                    List.of(FileProblem.ofFile(ProblemKind.WRONG_EXTENSION, name, REQUIRED_EXTENSION)));
        }

        GuessMarket parsed;
        try {
            parsed = unmarshal(xml);
        } catch (JAXBException | RuntimeException e) {
            throw new InvalidFileException(name,
                    List.of(FileProblem.ofFile(ProblemKind.UNREADABLE_XML, name, rootCauseMessage(e))));
        }

        List<GMEvent> xmlEvents = extractEvents(parsed);
        if (xmlEvents.isEmpty()) {
            throw new InvalidFileException(name, List.of(FileProblem.ofFile(ProblemKind.NO_EVENTS)));
        }
        List<FileProblem> problems = findEventProblems(xmlEvents, nameTaken);
        if (!problems.isEmpty()) {
            throw new InvalidFileException(name, problems);
        }

        List<Event> events = new ArrayList<>();
        int id = firstId;
        for (GMEvent xmlEvent : xmlEvents) {
            events.add(toEvent(xmlEvent, id++, marketMaker));
        }
        return events;
    }

    private static synchronized JAXBContext context() throws JAXBException {
        if (context == null) {
            context = JAXBContext.newInstance(GuessMarket.class);
        }
        return context;
    }

    private GuessMarket unmarshal(InputStream xml) throws JAXBException {
        if (xml == null) {
            throw new JAXBException("no content was received");
        }
        Unmarshaller unmarshaller = context().createUnmarshaller();
        Object root = unmarshaller.unmarshal(xml);
        if (!(root instanceof GuessMarket guessMarket)) {
            throw new JAXBException("the root element is not Guess-Market");
        }
        return guessMarket;
    }

    private List<GMEvent> extractEvents(GuessMarket parsed) {
        if (parsed.getGMEvents() == null || parsed.getGMEvents().getGMEvent() == null) {
            return List.of();
        }
        return parsed.getGMEvents().getGMEvent();
    }

    private List<FileProblem> findEventProblems(List<GMEvent> xmlEvents, Predicate<String> nameTaken) {
        List<FileProblem> problems = new ArrayList<>();
        Map<String, Integer> firstPositionOfName = new HashMap<>();

        for (int i = 0; i < xmlEvents.size(); i++) {
            GMEvent xmlEvent = xmlEvents.get(i);
            int position = i + 1;
            String name = trimmed(xmlEvent.getName());

            if (name.isEmpty()) {
                problems.add(FileProblem.ofEvent(ProblemKind.BLANK_EVENT_NAME, position, name));
            } else {
                Integer earlierPosition = firstPositionOfName.putIfAbsent(name, position);
                if (earlierPosition != null) {
                    problems.add(FileProblem.ofEvent(ProblemKind.DUPLICATE_EVENT_NAME_IN_FILE, position, name,
                            name, String.valueOf(earlierPosition)));
                } else if (nameTaken.test(name)) {
                    problems.add(FileProblem.ofEvent(ProblemKind.DUPLICATE_EVENT_NAME, position, name, name));
                }
            }

            problems.addAll(checkCommission(position, name, xmlEvent.getCommission()));
            problems.addAll(checkOutcomes(position, name, xmlEvent.getGMOptions()));
            problems.addAll(checkMethod(position, name, xmlEvent.getGMMethod()));
        }
        return problems;
    }

    private List<FileProblem> checkCommission(int position, String name, Commission commission) {
        if (commission == null) {
            return List.of(FileProblem.ofEvent(ProblemKind.MISSING_COMMISSION, position, name));
        }
        List<FileProblem> problems = new ArrayList<>();
        int percent = commission.getValue();
        if (percent < MIN_COMMISSION_PERCENT || percent > MAX_COMMISSION_PERCENT) {
            problems.add(FileProblem.ofEvent(ProblemKind.COMMISSION_OUT_OF_RANGE, position, name,
                    String.valueOf(percent),
                    String.valueOf(MIN_COMMISSION_PERCENT),
                    String.valueOf(MAX_COMMISSION_PERCENT)));
        }
        if (CommissionType.fromXmlValue(commission.getType()) == null) {
            problems.add(FileProblem.ofEvent(ProblemKind.UNKNOWN_COMMISSION_TYPE, position, name,
                    String.valueOf(commission.getType())));
        }
        return problems;
    }

    private List<FileProblem> checkOutcomes(int position, String name, GMOptions options) {
        if (options == null || options.getGMOption() == null) {
            return List.of(FileProblem.ofEvent(ProblemKind.WRONG_ANSWER_COUNT, position, name,
                    "0", String.valueOf(MIN_OUTCOME_COUNT)));
        }
        List<FileProblem> problems = new ArrayList<>();
        List<String> names = trimmedOutcomeNames(options);

        if (names.size() < MIN_OUTCOME_COUNT) {
            problems.add(FileProblem.ofEvent(ProblemKind.WRONG_ANSWER_COUNT, position, name,
                    String.valueOf(names.size()), String.valueOf(MIN_OUTCOME_COUNT)));
        }
        for (int i = 0; i < names.size(); i++) {
            if (names.get(i).isEmpty()) {
                problems.add(FileProblem.ofEvent(ProblemKind.BLANK_ANSWER, position, name,
                        String.valueOf(i + 1)));
            }
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String outcomeName : names) {
            if (!outcomeName.isEmpty() && !seen.add(outcomeName.toLowerCase())) {
                problems.add(FileProblem.ofEvent(ProblemKind.DUPLICATE_ANSWER, position, name, outcomeName));
            }
        }
        return problems;
    }

    private List<FileProblem> checkMethod(int position, String name, GMMethod method) {
        if (method == null || (method.getGMLMSR() == null && method.getGMOrderBook() == null)) {
            return List.of(FileProblem.ofEvent(ProblemKind.MISSING_METHOD, position, name));
        }
        GMLMSR lmsr = method.getGMLMSR();
        if (lmsr != null) {
            if (lmsr.getB() <= 0) {
                return List.of(FileProblem.ofEvent(ProblemKind.LIQUIDITY_NOT_POSITIVE, position, name,
                        String.valueOf(lmsr.getB())));
            }
            return List.of();
        }

        GMOrderBook orderBook = method.getGMOrderBook();
        List<FileProblem> problems = new ArrayList<>();
        if (orderBook.getD() <= 0) {
            problems.add(FileProblem.ofEvent(ProblemKind.BASE_PRICE_NOT_POSITIVE, position, name,
                    String.valueOf(orderBook.getD())));
        }
        if (orderBook.getInitial() < 0) {
            problems.add(FileProblem.ofEvent(ProblemKind.INITIAL_INVESTMENT_NEGATIVE, position, name,
                    String.valueOf(orderBook.getInitial())));
        }
        if (parseMintFlag(orderBook.getAllowMint()) == null) {
            problems.add(FileProblem.ofEvent(ProblemKind.UNKNOWN_MINT_FLAG, position, name,
                    String.valueOf(orderBook.getAllowMint())));
        }
        return problems;
    }

    private Event toEvent(GMEvent xmlEvent, int id, String marketMakerName) {
        CommissionType commissionType = CommissionType.fromXmlValue(xmlEvent.getCommission().getType());
        String name = trimmed(xmlEvent.getName());
        String description = xmlEvent.getDescription() == null ? "" : xmlEvent.getDescription().trim();
        int commissionPercent = xmlEvent.getCommission().getValue();
        List<String> outcomeNames = trimmedOutcomeNames(xmlEvent.getGMOptions());
        GMMethod method = xmlEvent.getGMMethod();

        if (method.getGMLMSR() != null) {
            return new LmsrEvent(id, name, description, commissionPercent, commissionType,
                    outcomeNames, marketMakerName, method.getGMLMSR().getB());
        }
        GMOrderBook orderBook = method.getGMOrderBook();
        return new OrderBookEvent(id, name, description, commissionPercent, commissionType,
                outcomeNames, marketMakerName,
                orderBook.getD(),
                Boolean.TRUE.equals(parseMintFlag(orderBook.getAllowMint())),
                orderBook.getInitial());
    }

    // The schema types allow-mint as an enumeration of the two strings rather than xs:boolean,
    // so JAXB hands over whatever text was in the file.
    private Boolean parseMintFlag(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.equalsIgnoreCase("true")) {
            return Boolean.TRUE;
        }
        if (value.equalsIgnoreCase("false")) {
            return Boolean.FALSE;
        }
        return null;
    }

    private List<String> trimmedOutcomeNames(GMOptions options) {
        List<String> names = new ArrayList<>();
        for (String option : options.getGMOption()) {
            names.add(option == null ? "" : option.trim());
        }
        return names;
    }

    private String trimmed(String value) {
        return value == null ? "" : value.trim();
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message.trim();
    }
}
