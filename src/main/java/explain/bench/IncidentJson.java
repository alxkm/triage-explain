package explain.bench;

import callguard.Json;
import explain.Incident;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import triage.core.Cause;

/** One incident per JSON line; read back with the guard's JSON reader. */
final class IncidentJson {
    private IncidentJson() {
    }

    static String write(Incident i) {
        StringBuilder w = new StringBuilder("[");
        for (int k = 0; k < i.window().length; k++) {
            double v = i.window()[k];
            w.append(k == 0 ? "" : ",").append(Double.isFinite(v) ? String.format(Locale.ROOT, "%.4f", v) : "null"); // a missing sample
        }
        w.append(']');
        return String.format(Locale.ROOT,
                "{\"id\":%s,\"channel\":%s,\"rangeMin\":%s,\"rangeMax\":%s,\"sigma\":%.6f,\"cause\":%s,\"triggerIndex\":%d,\"decisionIndex\":%d,"
                        + "\"triggers\":%s,\"evidence\":%s,\"window\":%s,\"fault\":%s,\"truth\":%s}",
                q(i.id()), q(i.channel()), Double.toString(i.rangeMin()), Double.toString(i.rangeMax()), i.sigma(), q(i.cause().name()), i.triggerIndex(),
                i.decisionIndex(), list(i.triggers()), list(i.evidence()), w, q(i.fault()), q(i.truth().name()));
    }

    static Incident read(String line) {
        Map<?, ?> m = (Map<?, ?>) Json.parse(line);
        List<?> w = (List<?>) m.get("window");
        double[] window = new double[w.size()];
        for (int k = 0; k < window.length; k++) {
            window[k] = w.get(k) instanceof Double d ? d : Double.NaN;
        }
        return new Incident((String) m.get("id"), (String) m.get("channel"), (Double) m.get("rangeMin"), (Double) m.get("rangeMax"),
                (Double) m.get("sigma"), Cause.valueOf((String) m.get("cause")), ((Double) m.get("triggerIndex")).longValue(),
                ((Double) m.get("decisionIndex")).longValue(), strings(m.get("triggers")), strings(m.get("evidence")), window, (String) m.get("fault"),
                Cause.valueOf((String) m.get("truth")));
    }

    private static List<String> strings(Object o) {
        List<String> out = new ArrayList<>();
        for (Object x : (List<?>) o) {
            out.add((String) x);
        }
        return out;
    }

    private static String list(List<String> xs) {
        StringBuilder b = new StringBuilder("[");
        for (int k = 0; k < xs.size(); k++) {
            b.append(k == 0 ? "" : ",").append(q(xs.get(k)));
        }
        return b.append(']').toString();
    }

    static String q(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        return b.append('"').toString();
    }
}
