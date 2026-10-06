package explain;

import java.util.Locale;
import java.util.StringJoiner;

/**
 * The text a model is given. Three variants: everything the operator would see (for explanation), the same without
 * the decided cause (to test whether a model can attribute from the detectors' evidence), and the raw readings only
 * (to test whether a model can attribute from the data). The grounding rules accept only numbers that occur here.
 */
public final class Facts {
    private Facts() {
    }

    public static String forExplanation(Incident i) {
        return header(i) + "decided cause: " + i.cause().name() + " (" + Checks.MEANING.get(i.cause()) + ")\n" + detectors(i) + timing(i);
    }

    public static String withoutDecision(Incident i) {
        return header(i) + detectors(i) + timing(i);
    }

    public static String readingsOnly(Incident i) {
        StringJoiner v = new StringJoiner(", ");
        for (double x : i.window()) {
            v.add(Double.isFinite(x) ? String.format(Locale.ROOT, "%.2f", x) : "missing");
        }
        long first = i.decisionIndex() - i.window().length + 1;
        return header(i) + String.format(Locale.ROOT, "readings from t=%d s to t=%d s, one per second:\n%s\n", first, i.decisionIndex(), v);
    }

    private static String header(Incident i) {
        return String.format(Locale.ROOT, "channel: %s (declared range %s to %s, one reading per second)\ncalibrated noise sigma: %.2f\n",
                i.channel().equals("tank") ? "tank level" : "vessel temperature", num(i.rangeMin()), num(i.rangeMax()), i.sigma());
    }

    private static String detectors(Incident i) {
        StringBuilder b = new StringBuilder("detectors fired: ").append(i.triggers().isEmpty() ? "(not listed)" : String.join(", ", i.triggers()))
                .append("\nevidence:\n");
        for (String e : i.evidence()) {
            b.append("- ").append(e).append('\n');
        }
        return b.toString();
    }

    private static String timing(Incident i) {
        return String.format(Locale.ROOT, "first detector fired at t=%d s; decision at t=%d s (%d s later)\n", i.triggerIndex(), i.decisionIndex(),
                i.decisionIndex() - i.triggerIndex());
    }

    private static String num(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v);
    }
}
