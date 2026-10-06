package explain;

import java.util.List;
import triage.core.Cause;

/**
 * One triage decision as an operator would receive it, plus — for evaluation only — what was actually injected.
 * Nothing in {@link #truth()} or {@link #fault()} is ever shown to a model.
 *
 * @param id stable identifier
 * @param channel channel name as declared ({@code tank}, {@code thermal})
 * @param rangeMin declared instrument range, lower bound, engineering units
 * @param rangeMax declared instrument range, upper bound
 * @param sigma the channel's calibrated noise σ̂, engineering units
 * @param cause the cause the detectors decided
 * @param triggerIndex sample (= second) at which the first detector fired
 * @param decisionIndex sample at which the rule reached its verdict
 * @param triggers detectors that had fired when the decision was made
 * @param evidence the rule's reasons, in the order it evaluated them
 * @param window the readings from 20 samples before the trigger to the decision (at most the last 80), engineering units
 * @param fault the injected fault type (evaluation only)
 * @param truth the injected fault's true cause (evaluation only)
 */
public record Incident(String id, String channel, double rangeMin, double rangeMax, double sigma, Cause cause, long triggerIndex,
        long decisionIndex, List<String> triggers, List<String> evidence, double[] window, String fault, Cause truth) {

    public Incident {
        triggers = List.copyOf(triggers);
        evidence = List.copyOf(evidence);
        window = window.clone();
    }
}
