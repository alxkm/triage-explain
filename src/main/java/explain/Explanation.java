package explain;

import java.util.List;
import triage.core.Cause;

/**
 * What the operator receives.
 *
 * @param cause always the decided cause
 * @param text the explanation
 * @param nextCheck the id of a permitted next check for {@link #cause()}
 * @param source {@code model} if the model's text passed every grounding rule, otherwise {@code template}
 * @param rejected the rules the model's reply failed (empty when {@code source} is model, or when no model was asked)
 * @param raw the model's reply as received, or "" if there was none
 * @param millis time spent on the model call, 0 for the template alone
 */
public record Explanation(Cause cause, String text, String nextCheck, String source, List<String> rejected, String raw, long millis) {
    public Explanation {
        rejected = List.copyOf(rejected);
    }
}
