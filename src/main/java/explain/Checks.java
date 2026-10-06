package explain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import triage.core.Cause;

/** What the explanation may say about each cause, and the next checks an operator can be pointed to. */
public final class Checks {
    private Checks() {
    }

    /** One permitted next check. */
    public record Check(String id, Cause cause, String text) {
    }

    public static final List<Check> ALL = List.of(
            new Check("P1", Cause.PROCESS, "confirm the change in the process log or on a neighbouring channel"),
            new Check("P2", Cause.PROCESS, "confirm a setpoint or operating-mode change with operations"),
            new Check("I1", Cause.INSTRUMENT, "compare the instrument against a reference or redundant sensor"),
            new Check("I2", Cause.INSTRUMENT, "schedule inspection or recalibration of the instrument"),
            new Check("D1", Cause.DATA_PATH, "inspect gateway, protocol and transport logs for the affected interval"),
            new Check("D2", Cause.DATA_PATH, "check the data pipeline for replayed, substituted or missing records"),
            new Check("U1", Cause.UNDETERMINED, "correlate with a second channel that measures a related quantity"),
            new Check("U2", Cause.UNDETERMINED, "refer to an operator for a manual check before acting"));

    public static final Map<Cause, String> MEANING = new LinkedHashMap<>();

    static {
        MEANING.put(Cause.PROCESS, "the measured quantity itself changed; neither the instrument nor the data path explains the readings");
        MEANING.put(Cause.INSTRUMENT, "the instrument no longer tracks the quantity (bias, drift, noise growth, stuck value or saturation)");
        MEANING.put(Cause.DATA_PATH, "the value was corrupted between the instrument and the software (transport, serialization, middleware)");
        MEANING.put(Cause.UNDETERMINED, "one channel cannot separate the causes; a second channel or an operator is needed");
    }

    public static Check byId(String id) {
        for (Check c : ALL) {
            if (c.id().equalsIgnoreCase(id == null ? "" : id.strip())) {
                return c;
            }
        }
        return null;
    }

    /** The first permitted check for a cause: what the template explanation points to. */
    public static Check first(Cause cause) {
        for (Check c : ALL) {
            if (c.cause() == cause) {
                return c;
            }
        }
        throw new IllegalArgumentException(cause.name());
    }

    /** "data path" for DATA_PATH. */
    public static String label(Cause c) {
        return c.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }
}
