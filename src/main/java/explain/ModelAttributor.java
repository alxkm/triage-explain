package explain;

import explain.port.ChatPort;
import triage.core.Cause;

/**
 * The ablation, not part of the method: a model asked to attribute the departure itself, from the detectors'
 * evidence without the decision, or from the raw readings alone. It measures what would be lost if the model
 * decided instead of explaining.
 */
public final class ModelAttributor {
    static final String SYSTEM = """
            You attribute a departure of one sensor channel's readings from expectation to one cause:
            PROCESS: the measured quantity itself changed.
            INSTRUMENT: the instrument no longer tracks the quantity (bias, drift, noise growth, stuck value, saturation).
            DATA_PATH: the value was corrupted between the instrument and the software (transport, serialization, middleware).
            UNDETERMINED: one channel cannot separate the causes.
            Reply with JSON only, no other text: {"cause": "<PROCESS|INSTRUMENT|DATA_PATH|UNDETERMINED>", "reason": "<one sentence>"}""";

    public enum Input {
        /** The detectors' evidence and triggers, without the decided cause. */
        EVIDENCE,
        /** The readings around the event, nothing from the detectors. */
        READINGS
    }

    private final ChatPort port;

    public ModelAttributor(ChatPort port) {
        this.port = port;
    }

    /** The cause, or {@code null} if the call failed or the reply named none. */
    public Cause attribute(Incident i, Input input) {
        String facts = input == Input.EVIDENCE ? Facts.withoutDecision(i) : Facts.readingsOnly(i);
        try {
            return ModelExplainer.parseCause(port.chat(SYSTEM, "FACTS\n" + facts));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
