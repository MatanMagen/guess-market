package guessmarket.engine.xml;

import guessmarket.dto.FileProblem;
import guessmarket.dto.ProblemKind;
import guessmarket.engine.exception.InvalidFileException;
import guessmarket.engine.model.CommissionType;
import guessmarket.engine.model.Event;
import guessmarket.engine.model.LmsrEvent;
import guessmarket.engine.model.MarketState;
import guessmarket.engine.model.OrderBookEvent;
import guessmarket.engine.model.User;
import guessmarket.engine.xml.generated.Commission;
import guessmarket.engine.xml.generated.GMEvent;
import guessmarket.engine.xml.generated.GMLMSR;
import guessmarket.engine.xml.generated.GMMethod;
import guessmarket.engine.xml.generated.GMOptions;
import guessmarket.engine.xml.generated.GMOrderBook;
import guessmarket.engine.xml.generated.GMUser;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a Guess Market XML file into a {@link MarketState}. Reading and validating are engine
 * work, so a front end supplies nothing but the path the user chose.
 * <p>
 * What is checked here is what the schema cannot express. From exercise 1: the file exists and is
 * named .xml, event numbers do not repeat, commissions are sensible percentages, each event has
 * exactly two distinct answers, and b can be divided by. Added for exercise 2: user names are
 * unique, every user opens with money, no market maker points at an event that is not there, and
 * every event has exactly one market maker. Faults are collected rather than thrown one at a time.
 */
public final class XmlEventLoader {

    /** The extension a Guess Market file must carry. */
    private static final String REQUIRED_EXTENSION = ".xml";

    /** Package holding the classes generated from GM-EX2-Schema.xsd. */
    private static final String GENERATED_PACKAGE = "guessmarket.engine.xml.generated";

    private static final int MIN_COMMISSION_PERCENT = 0;
    private static final int MAX_COMMISSION_PERCENT = 90;
    private static final int REQUIRED_OUTCOME_COUNT = 2;

    /**
     * @param rawPath as chosen by the user. Surrounding spaces and a surrounding pair of quotation
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
        List<GMUser> xmlUsers = extractUsers(parsed);
        if (xmlEvents.isEmpty()) {
            problems.add(FileProblem.ofFile(ProblemKind.NO_EVENTS));
        }
        if (xmlUsers.isEmpty()) {
            problems.add(FileProblem.ofFile(ProblemKind.NO_USERS));
        }
        if (!problems.isEmpty()) {
            throw new InvalidFileException(path, problems);
        }

        problems.addAll(findEventProblems(xmlEvents));
        problems.addAll(findUserProblems(xmlUsers, xmlEvents));
        if (!problems.isEmpty()) {
            throw new InvalidFileException(path, problems);
        }

        Map<Integer, String> marketMakers = marketMakersByEventId(xmlUsers);
        List<Event> events = new ArrayList<>();
        for (GMEvent xmlEvent : xmlEvents) {
            events.add(toEvent(xmlEvent, marketMakers.get(xmlEvent.getId())));
        }
        List<User> users = new ArrayList<>();
        for (GMUser xmlUser : xmlUsers) {
            users.add(new User(xmlUser.getName().trim(), xmlUser.getInitialCash()));
        }
        return new MarketState(file.getAbsolutePath(), events, users);
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

    private List<GMUser> extractUsers(GuessMarket parsed) {
        if (parsed.getGMUsers() == null || parsed.getGMUsers().getGMUser() == null) {
            return List.of();
        }
        return parsed.getGMUsers().getGMUser();
    }

    private List<FileProblem> findEventProblems(List<GMEvent> xmlEvents) {
        List<FileProblem> problems = new ArrayList<>();
        Map<Integer, Integer> firstPositionOfId = new HashMap<>();

        for (int i = 0; i < xmlEvents.size(); i++) {
            GMEvent xmlEvent = xmlEvents.get(i);
            int position = i + 1;
            String name = trimmed(xmlEvent.getName());

            Integer earlierPosition = firstPositionOfId.putIfAbsent(xmlEvent.getId(), position);
            if (earlierPosition != null) {
                problems.add(FileProblem.ofEvent(ProblemKind.DUPLICATE_EVENT_ID, position, name,
                        String.valueOf(xmlEvent.getId()), String.valueOf(earlierPosition)));
            }
            if (name.isEmpty()) {
                problems.add(FileProblem.ofEvent(ProblemKind.BLANK_EVENT_NAME, position, name));
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
        GMOrderBook orderBook = method.getGMOrderBook();
        if (lmsr == null && orderBook == null) {
            return List.of(FileProblem.ofEvent(ProblemKind.MISSING_METHOD, position, name));
        }
        if (lmsr != null) {
            if (lmsr.getB() <= 0) {
                return List.of(FileProblem.ofEvent(ProblemKind.LIQUIDITY_NOT_POSITIVE, position, name,
                        String.valueOf(lmsr.getB())));
            }
            return List.of();
        }

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

    private List<FileProblem> findUserProblems(List<GMUser> xmlUsers, List<GMEvent> xmlEvents) {
        List<FileProblem> problems = new ArrayList<>();
        Map<String, Integer> firstPositionOfName = new HashMap<>();
        Set<Integer> knownEventIds = new LinkedHashSet<>();
        for (GMEvent xmlEvent : xmlEvents) {
            knownEventIds.add(xmlEvent.getId());
        }
        Map<Integer, List<String>> claimsByEventId = new LinkedHashMap<>();

        for (int i = 0; i < xmlUsers.size(); i++) {
            GMUser xmlUser = xmlUsers.get(i);
            int position = i + 1;
            String name = trimmed(xmlUser.getName());

            if (name.isEmpty()) {
                problems.add(FileProblem.ofFile(ProblemKind.BLANK_USER_NAME, String.valueOf(position)));
            } else {
                Integer earlier = firstPositionOfName.putIfAbsent(name, position);
                if (earlier != null) {
                    problems.add(FileProblem.ofFile(ProblemKind.DUPLICATE_USER_NAME, name, String.valueOf(earlier)));
                }
            }
            if (xmlUser.getInitialCash() <= 0) {
                problems.add(FileProblem.ofFile(ProblemKind.INITIAL_CASH_NOT_POSITIVE,
                        name, String.valueOf(xmlUser.getInitialCash())));
            }

            Set<Integer> claimedHere = new LinkedHashSet<>();
            for (int eventId : marketMakerEventIds(xmlUser)) {
                if (!claimedHere.add(eventId)) {
                    problems.add(FileProblem.ofFile(ProblemKind.MARKET_MAKER_TWICE_OF_SAME_EVENT,
                            name, String.valueOf(eventId)));
                    continue;
                }
                if (!knownEventIds.contains(eventId)) {
                    problems.add(FileProblem.ofFile(ProblemKind.MARKET_MAKER_OF_UNKNOWN_EVENT,
                            name, String.valueOf(eventId)));
                    continue;
                }
                claimsByEventId.computeIfAbsent(eventId, key -> new ArrayList<>()).add(name);
            }
        }

        for (int i = 0; i < xmlEvents.size(); i++) {
            GMEvent xmlEvent = xmlEvents.get(i);
            int position = i + 1;
            String name = trimmed(xmlEvent.getName());
            List<String> claims = claimsByEventId.getOrDefault(xmlEvent.getId(), List.of());
            if (claims.isEmpty()) {
                problems.add(FileProblem.ofEvent(ProblemKind.EVENT_WITHOUT_MARKET_MAKER, position, name));
            } else if (claims.size() > 1) {
                problems.add(FileProblem.ofEvent(ProblemKind.EVENT_WITH_SEVERAL_MARKET_MAKERS, position, name,
                        String.join(", ", claims)));
            }
        }
        return problems;
    }

    private Map<Integer, String> marketMakersByEventId(List<GMUser> xmlUsers) {
        Map<Integer, String> owners = new LinkedHashMap<>();
        for (GMUser xmlUser : xmlUsers) {
            for (int eventId : marketMakerEventIds(xmlUser)) {
                owners.putIfAbsent(eventId, trimmed(xmlUser.getName()));
            }
        }
        return owners;
    }

    private List<Integer> marketMakerEventIds(GMUser xmlUser) {
        if (xmlUser.getGMMarketMaker() == null || xmlUser.getGMMarketMaker().getEvent() == null) {
            return List.of();
        }
        List<Integer> ids = new ArrayList<>();
        for (guessmarket.engine.xml.generated.Event reference : xmlUser.getGMMarketMaker().getEvent()) {
            ids.add(reference.getId());
        }
        return ids;
    }

    private Event toEvent(GMEvent xmlEvent, String marketMakerName) {
        CommissionType commissionType = CommissionType.fromXmlValue(xmlEvent.getCommission().getType());
        String name = trimmed(xmlEvent.getName());
        String description = xmlEvent.getDescription() == null ? "" : xmlEvent.getDescription().trim();
        int commissionPercent = xmlEvent.getCommission().getValue();
        List<String> outcomeNames = trimmedOutcomeNames(xmlEvent.getGMOptions());
        GMMethod method = xmlEvent.getGMMethod();

        if (method.getGMLMSR() != null) {
            return new LmsrEvent(xmlEvent.getId(), name, description, commissionPercent, commissionType,
                    outcomeNames, marketMakerName, method.getGMLMSR().getB());
        }
        GMOrderBook orderBook = method.getGMOrderBook();
        return new OrderBookEvent(xmlEvent.getId(), name, description, commissionPercent, commissionType,
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
