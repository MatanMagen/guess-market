package guessmarket.engine.xml;

import guessmarket.dto.FileProblem;
import guessmarket.dto.ProblemKind;
import guessmarket.engine.exception.InvalidFileException;
import guessmarket.engine.model.CommissionType;
import guessmarket.engine.model.Event;
import guessmarket.engine.model.MarketState;
import guessmarket.engine.xml.generated.Comision;
import guessmarket.engine.xml.generated.GMEvent;
import guessmarket.engine.xml.generated.GMLMSR;
import guessmarket.engine.xml.generated.GMMethod;
import guessmarket.engine.xml.generated.GMOptions;
import guessmarket.engine.xml.generated.GuessMarket;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a Guess Market XML file into a {@link MarketState}. Reading and validating are engine
 * work, so a front end supplies nothing but the path the user typed.
 * <p>
 * The exercise guarantees the file matches the schema, so what is checked here is what the schema
 * cannot express: that the file exists and is named .xml, that event numbers do not repeat, that
 * commissions are sensible percentages, that each event has exactly two distinct answers, and that
 * b can actually be divided by. Faults are collected rather than thrown one at a time.
 */
public final class XmlEventLoader {

    /** The extension a Guess Market file must carry. */
    private static final String REQUIRED_EXTENSION = ".xml";

    /** Package holding the classes generated from GM-EX1-schema.xsd. */
    private static final String GENERATED_PACKAGE = "guessmarket.engine.xml.generated";

    private static final int MIN_COMMISSION_PERCENT = 0;
    private static final int MAX_COMMISSION_PERCENT = 90;
    private static final int REQUIRED_OUTCOME_COUNT = 2;

    /**
     * @param rawPath as typed by the user. Surrounding spaces and a surrounding pair of quotation
     *                marks are tolerated: a path pasted out of Windows Explorer often has them.
     * @throws InvalidFileException carrying every fault found.
     */
    public MarketState load(String rawPath) {
        String path = normalizePath(rawPath);

        if (path.isEmpty()) {
            throw new InvalidFileException(path, List.of(FileProblem.ofFile(ProblemKind.NO_PATH_GIVEN)));
        }

        List<FileProblem> problems = new ArrayList<>();
        File file = new File(path);

        if (!path.toLowerCase().endsWith(REQUIRED_EXTENSION)) {
            problems.add(FileProblem.ofFile(ProblemKind.WRONG_EXTENSION, path, REQUIRED_EXTENSION));
        }
        if (!file.exists()) {
            problems.add(FileProblem.ofFile(ProblemKind.FILE_NOT_FOUND, path));
        } else if (file.isDirectory()) {
            problems.add(FileProblem.ofFile(ProblemKind.PATH_IS_A_FOLDER, path));
        } else if (!file.canRead()) {
            problems.add(FileProblem.ofFile(ProblemKind.FILE_NOT_READABLE, path));
        }
        if (!problems.isEmpty()) {
            throw new InvalidFileException(path, problems);
        }

        GuessMarket parsed;
        try {
            parsed = unmarshal(file);
        } catch (JAXBException | IOException e) {
            throw new InvalidFileException(path,
                    List.of(FileProblem.ofFile(ProblemKind.UNREADABLE_XML, path, rootCauseMessage(e))));
        }

        List<GMEvent> xmlEvents = extractEvents(parsed);
        if (xmlEvents.isEmpty()) {
            throw new InvalidFileException(path, List.of(FileProblem.ofFile(ProblemKind.NO_EVENTS)));
        }

        problems.addAll(findProblems(xmlEvents));
        if (!problems.isEmpty()) {
            throw new InvalidFileException(path, problems);
        }

        List<Event> events = new ArrayList<>();
        for (GMEvent xmlEvent : xmlEvents) {
            events.add(toEvent(xmlEvent));
        }
        return new MarketState(file.getAbsolutePath(), events);
    }

    private GuessMarket unmarshal(File file) throws JAXBException, IOException {
        JAXBContext context = JAXBContext.newInstance(GENERATED_PACKAGE);
        Unmarshaller unmarshaller = context.createUnmarshaller();
        try (InputStream in = new FileInputStream(file)) {
            Object root = unmarshaller.unmarshal(in);
            if (!(root instanceof GuessMarket guessMarket)) {
                throw new JAXBException("the root element is not Guess-Market");
            }
            return guessMarket;
        }
    }

    private List<GMEvent> extractEvents(GuessMarket parsed) {
        if (parsed.getGMEvents() == null || parsed.getGMEvents().getGMEvent() == null) {
            return List.of();
        }
        return parsed.getGMEvents().getGMEvent();
    }

    private List<FileProblem> findProblems(List<GMEvent> xmlEvents) {
        List<FileProblem> problems = new ArrayList<>();
        Map<Integer, Integer> firstPositionOfId = new HashMap<>();

        for (int i = 0; i < xmlEvents.size(); i++) {
            GMEvent xmlEvent = xmlEvents.get(i);
            int position = i + 1;
            String name = joinName(xmlEvent.getName());

            Integer earlierPosition = firstPositionOfId.putIfAbsent(xmlEvent.getId(), position);
            if (earlierPosition != null) {
                problems.add(FileProblem.ofEvent(ProblemKind.DUPLICATE_EVENT_ID, position, name,
                        String.valueOf(xmlEvent.getId()), String.valueOf(earlierPosition)));
            }
            if (name.isEmpty()) {
                problems.add(FileProblem.ofEvent(ProblemKind.BLANK_EVENT_NAME, position, name));
            }

            problems.addAll(checkCommission(position, name, xmlEvent.getComision()));
            problems.addAll(checkOutcomes(position, name, xmlEvent.getGMOptions()));
            problems.addAll(checkMethod(position, name, xmlEvent.getGMMethod()));
        }
        return problems;
    }

    private List<FileProblem> checkCommission(int position, String name, Comision comision) {
        if (comision == null) {
            return List.of(FileProblem.ofEvent(ProblemKind.MISSING_COMMISSION, position, name));
        }
        List<FileProblem> problems = new ArrayList<>();
        int percent = comision.getValue();
        if (percent < MIN_COMMISSION_PERCENT || percent > MAX_COMMISSION_PERCENT) {
            problems.add(FileProblem.ofEvent(ProblemKind.COMMISSION_OUT_OF_RANGE, position, name,
                    String.valueOf(percent),
                    String.valueOf(MIN_COMMISSION_PERCENT),
                    String.valueOf(MAX_COMMISSION_PERCENT)));
        }
        if (CommissionType.fromXmlValue(comision.getType()) == null) {
            problems.add(FileProblem.ofEvent(ProblemKind.UNKNOWN_COMMISSION_TYPE, position, name,
                    String.valueOf(comision.getType())));
        }
        return problems;
    }

    private List<FileProblem> checkOutcomes(int position, String name, GMOptions options) {
        if (options == null || options.getGMOption() == null) {
            return List.of(FileProblem.ofEvent(ProblemKind.WRONG_ANSWER_COUNT, position, name,
                    "0", String.valueOf(REQUIRED_OUTCOME_COUNT)));
        }
        List<FileProblem> problems = new ArrayList<>();
        List<String> names = trimmedOutcomeNames(options);

        if (names.size() != REQUIRED_OUTCOME_COUNT) {
            problems.add(FileProblem.ofEvent(ProblemKind.WRONG_ANSWER_COUNT, position, name,
                    String.valueOf(names.size()), String.valueOf(REQUIRED_OUTCOME_COUNT)));
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
        if (method == null) {
            return List.of(FileProblem.ofEvent(ProblemKind.MISSING_METHOD, position, name));
        }
        GMLMSR lmsr = method.getGMLMSR();
        if (lmsr == null) {
            return List.of(FileProblem.ofEvent(ProblemKind.MISSING_LMSR, position, name));
        }
        if (lmsr.getB() <= 0) {
            return List.of(FileProblem.ofEvent(ProblemKind.LIQUIDITY_NOT_POSITIVE, position, name,
                    String.valueOf(lmsr.getB())));
        }
        return List.of();
    }

    private Event toEvent(GMEvent xmlEvent) {
        CommissionType commissionType = CommissionType.fromXmlValue(xmlEvent.getComision().getType());
        return new Event(
                xmlEvent.getId(),
                joinName(xmlEvent.getName()),
                xmlEvent.getDescription() == null ? "" : xmlEvent.getDescription().trim(),
                xmlEvent.getComision().getValue(),
                commissionType,
                trimmedOutcomeNames(xmlEvent.getGMOptions()),
                xmlEvent.getGMMethod().getGMLMSR().getB());
    }

    private List<String> trimmedOutcomeNames(GMOptions options) {
        List<String> names = new ArrayList<>();
        for (String option : options.getGMOption()) {
            names.add(option == null ? "" : option.trim());
        }
        return names;
    }

    // The schema types the name attribute as xs:list, so JAXB splits it on whitespace and
    // "Mujtaba is Dead" arrives as three tokens. Joining with single spaces gets the name back,
    // at the cost of collapsing any run of several spaces inside it.
    private String joinName(List<String> tokens) {
        if (tokens == null) {
            return "";
        }
        return String.join(" ", tokens).trim();
    }

    private String normalizePath(String rawPath) {
        if (rawPath == null) {
            return "";
        }
        String path = rawPath.trim();
        if (path.length() >= 2 && path.startsWith("\"") && path.endsWith("\"")) {
            path = path.substring(1, path.length() - 1).trim();
        }
        return path;
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
