package explain;

import callguard.Json;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import triage.core.Cause;

/**
 * The rules a model's explanation must pass before an operator sees it. A reply that fails any rule is replaced by
 * the template; the failed rules are recorded.
 *
 * <ul>
 * <li>R1 format — one JSON object with string fields {@code cause}, {@code explanation}, {@code next_check}
 * <li>R2 cause — {@code cause} names the decided cause
 * <li>R3 numbers — every number in the explanation occurs in the facts (allowing rounding to the digits written)
 * <li>R4 next check — {@code next_check} is a permitted check for the decided cause
 * <li>R5 no contradiction — no sentence asserts another cause without negating it (a lexical rule; see the paper). For an
 * UNDETERMINED decision, naming the process and instrument hypotheses it cannot separate is not a contradiction
 * <li>R6 length — at most 80 words
 * </ul>
 */
public final class GroundingValidator {
    public static final int MAX_WORDS = 80;
    private static final Pattern NUMBER = Pattern.compile("(?<![\\w.])-?\\d+(?:\\.\\d+)?");
    private static final Pattern NEGATION = Pattern.compile(
            "\\b(not|no|rather than|rules? out|ruled out|unlikely|instead of|without|isn't|exclud\\w*|neither|nor|cannot be attributed)\\b");
    private static final Map<Cause, List<String>> CLAIMS = Map.of(
            Cause.PROCESS, List.of("process change", "process changed", "the process itself", "genuine change", "real change in the"),
            Cause.INSTRUMENT, List.of("sensor fault", "instrument fault", "sensor failure", "instrument failure", "faulty sensor", "faulty instrument",
                    "sensor malfunction", "instrument malfunction", "sensor drift", "sensor is drifting", "instrument is drifting"),
            Cause.DATA_PATH, List.of("data path", "data-path", "transmission error", "communication error", "network error", "corrupted in transit",
                    "data corruption"),
            Cause.UNDETERMINED, List.of("cannot be determined", "undetermined", "cannot separate", "cannot distinguish", "not separable"));

    /** The parsed reply and the rules it failed. */
    public record Result(String cause, String explanation, String nextCheck, List<String> failed) {
        public boolean passed() {
            return failed.isEmpty();
        }
    }

    private GroundingValidator() {
    }

    public static Result validate(Incident i, String facts, String reply) {
        List<String> failed = new ArrayList<>();
        Map<?, ?> obj = parse(reply);
        if (obj == null || !(obj.get("cause") instanceof String cause) || !(obj.get("explanation") instanceof String text)
                || !(obj.get("next_check") instanceof String next)) {
            failed.add("R1-format");
            return new Result(null, null, null, failed);
        }
        if (!sameCause(cause, i.cause())) {
            failed.add("R2-cause");
        }
        List<Double> allowed = numbers(facts);
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            if (!grounded(m.group(), allowed)) {
                failed.add("R3-number:" + m.group());
                break;
            }
        }
        Checks.Check c = Checks.byId(next);
        if (c == null || c.cause() != i.cause()) {
            failed.add("R4-next-check");
        }
        String contradiction = contradiction(text, i.cause());
        if (contradiction != null) {
            failed.add("R5-contradiction:" + contradiction);
        }
        if (text.strip().split("\\s+").length > MAX_WORDS) {
            failed.add("R6-length");
        }
        return new Result(cause, text, next, failed);
    }

    static Map<?, ?> parse(String reply) {
        if (reply == null) {
            return null;
        }
        int a = reply.indexOf('{');
        int b = reply.lastIndexOf('}');
        if (a < 0 || b <= a) {
            return null;
        }
        try {
            return Json.parse(reply.substring(a, b + 1)) instanceof Map<?, ?> map ? map : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** The leading cause label of {@code said} (a label followed by its gloss, "DATA_PATH (the value was …)", counts as the label). */
    static boolean sameCause(String said, Cause decided) {
        String s = said.strip().toUpperCase(Locale.ROOT).replace('-', '_');
        s = s.replaceFirst("^DATA PATH", "DATA_PATH");
        Matcher m = Pattern.compile("^[A-Z_]+").matcher(s);
        return m.find() && m.group().equals(decided.name());
    }

    static List<Double> numbers(String facts) {
        List<Double> out = new ArrayList<>();
        Matcher m = NUMBER.matcher(facts);
        while (m.find()) {
            out.add(Double.parseDouble(m.group()));
        }
        return out;
    }

    /** {@code token} equals an allowed value, or is that value rounded to the number of decimals written. */
    static boolean grounded(String token, List<Double> allowed) {
        double x = Double.parseDouble(token);
        int dot = token.indexOf('.');
        int decimals = dot < 0 ? 0 : token.length() - dot - 1;
        double tol = 0.5 * Math.pow(10, -decimals) + 1e-9;
        for (double a : allowed) {
            if (Math.abs(x - a) <= tol) {
                return true;
            }
        }
        return false;
    }

    /** The first claim of another cause that is not negated in its sentence, or null. */
    static String contradiction(String text, Cause decided) {
        for (String sentence : text.toLowerCase(Locale.ROOT).split("(?<=[.!?;])\\s+")) {
            for (Map.Entry<Cause, List<String>> e : CLAIMS.entrySet()) {
                if (e.getKey() == decided || (decided == Cause.UNDETERMINED && e.getKey() != Cause.DATA_PATH)) {
                    continue; // an undetermined decision names the hypotheses it cannot separate
                }
                for (String phrase : e.getValue()) {
                    if (sentence.contains(phrase) && !NEGATION.matcher(sentence).find()) {
                        return phrase.replace(' ', '_');
                    }
                }
            }
        }
        return null;
    }
}
