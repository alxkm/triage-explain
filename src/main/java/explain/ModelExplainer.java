package explain;

import explain.port.ChatPort;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import triage.core.Cause;

/**
 * Asks a model to explain a decision the detectors have already made, and lets the explanation through only if it
 * passes every grounding rule. Otherwise — and whenever the call fails — the operator gets the template. The decided
 * cause is never changed: the model writes prose, it does not decide.
 *
 * <p>Each explanation is one Micrometer observation {@code triage.explain} with low-cardinality keys {@code source}
 * (model | template), {@code rule} (the first failed rule, or none) and {@code port}.
 */
public final class ModelExplainer {
    static final String SYSTEM = """
            You write one short explanation for a plant operator about a decision that deterministic detectors have already \
            made on one sensor channel.
            Rules:
            1. The cause is decided. State it exactly as given in FACTS; never change it.
            2. Use only numbers that appear in FACTS. Do not compute, convert or invent numbers.
            3. Choose next_check from ALLOWED CHECKS and copy its id. It must be a check for the decided cause.
            4. At most 3 sentences and at most 70 words.
            Reply with JSON only, no other text:
            {"cause": "<decided cause>", "explanation": "<text>", "next_check": "<id>"}""";

    private final ChatPort port;
    private final ObservationRegistry registry;

    public ModelExplainer(ChatPort port, ObservationRegistry registry) {
        this.port = port;
        this.registry = registry == null ? ObservationRegistry.NOOP : registry;
    }

    public static String userMessage(Incident i) {
        StringBuilder b = new StringBuilder("FACTS\n").append(Facts.forExplanation(i)).append("\nALLOWED CHECKS\n");
        for (Checks.Check c : Checks.ALL) {
            b.append(c.id()).append(" (for ").append(c.cause().name()).append("): ").append(c.text()).append('\n');
        }
        return b.toString();
    }

    public Explanation explain(Incident i) {
        Observation obs = Observation.createNotStarted("triage.explain", registry).lowCardinalityKeyValue("port", port.name()).start();
        long t0 = System.nanoTime();
        String reply;
        try {
            reply = port.chat(SYSTEM, userMessage(i));
        } catch (RuntimeException e) {
            long ms = (System.nanoTime() - t0) / 1_000_000;
            String rule = "R0-call:" + callguard.GuardException.find(e).map(g -> g.kind().tag()).orElse(e.getClass().getSimpleName());
            obs.lowCardinalityKeyValue("source", "template").lowCardinalityKeyValue("rule", "R0-call").error(e).stop();
            return TemplateExplainer.explain(i, List.of(rule), "", ms);
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        GroundingValidator.Result r = GroundingValidator.validate(i, Facts.forExplanation(i), reply);
        if (r.passed()) {
            obs.lowCardinalityKeyValue("source", "model").lowCardinalityKeyValue("rule", "none").stop();
            return new Explanation(i.cause(), r.explanation().strip(), r.nextCheck().strip().toUpperCase(java.util.Locale.ROOT), "model", List.of(), reply, ms);
        }
        obs.lowCardinalityKeyValue("source", "template").lowCardinalityKeyValue("rule", r.failed().get(0).split(":")[0]).stop();
        return TemplateExplainer.explain(i, r.failed(), reply, ms);
    }

    /** Kept for the ablation: the model's own attribution, never used to change a decision. */
    public static Cause parseCause(String reply) {
        var m = GroundingValidator.parse(reply);
        if (m != null && m.get("cause") instanceof String s) {
            for (Cause c : Cause.values()) {
                if (GroundingValidator.sameCause(s, c)) {
                    return c;
                }
            }
        }
        return null;
    }
}
