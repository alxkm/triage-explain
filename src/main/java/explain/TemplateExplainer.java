package explain;

import java.util.List;
import java.util.Locale;

/** The deterministic explanation: always available, always grounded, and the fallback for every model failure. */
public final class TemplateExplainer {
    private TemplateExplainer() {
    }

    public static Explanation explain(Incident i, List<String> rejected, String raw, long millis) {
        Checks.Check next = Checks.first(i.cause());
        String evidence = String.join("; ", i.evidence().subList(0, Math.min(2, i.evidence().size())));
        String text = String.format(Locale.ROOT, "Attributed to %s: %s. Evidence: %s. First detector at t=%d s, decision at t=%d s. Next: %s.",
                Checks.label(i.cause()), Checks.MEANING.get(i.cause()), evidence, i.triggerIndex(), i.decisionIndex(), next.text());
        return new Explanation(i.cause(), text, next.id(), "template", rejected, raw, millis);
    }

    public static Explanation explain(Incident i) {
        return explain(i, List.of(), "", 0);
    }
}
